/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastreceiver;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbMessage;
import android.util.Log;

import com.android.internal.telephony.gsm.SmsCbConstants;

public class CellBroadcastDatabase {
    // package local for efficient access from inner class
    static final String TAG = "CellBroadcastDatabase";

    private CellBroadcastDatabase() {}

    static final String DATABASE_NAME = "cell_broadcasts.db";
    static final String TABLE_NAME = "broadcasts";

    /** Temporary table for upgrading the database version. */
    static final String TEMP_TABLE_NAME = "old_broadcasts";

    /**
     * Database version 1: initial version
     * Database version 2-9: (reserved for OEM database customization)
     * Database version 10: adds ETWS and CMAS columns and CDMA support
     */
    static final int DATABASE_VERSION = 10;

    static final class Columns implements BaseColumns {

        private Columns() {}

        /**
         * Message geographical scope.
         * <P>Type: INTEGER</P>
         */
        public static final String GEOGRAPHICAL_SCOPE = "geo_scope";

        /**
         * Message serial number.
         * <P>Type: INTEGER</P>
         */
        public static final String SERIAL_NUMBER = "serial_number";

        /**
         * PLMN of broadcast sender. (SERIAL_NUMBER + PLMN + LAC + CID) uniquely identifies a
         * broadcast for duplicate detection purposes.
         * <P>Type: TEXT</P>
         */
        public static final String PLMN = "plmn";

        /**
         * Location Area (GSM) or Service Area (UMTS) of broadcast sender. Unused for CDMA.
         * Only included if Geographical Scope of message is not PLMN wide (01).
         * <P>Type: INTEGER</P>
         */
        public static final String LAC = "lac";

        /**
         * Cell ID of message sender (GSM/UMTS). Unused for CDMA. Only included when the
         * Geographical Scope of message is cell wide (00 or 11).
         * <P>Type: INTEGER</P>
         */
        public static final String CID = "cid";

        /**
         * Message code (OBSOLETE: merged into SERIAL_NUMBER).
         * <P>Type: INTEGER</P>
         */
        public static final String V1_MESSAGE_CODE = "message_code";

        /**
         * Message identifier (OBSOLETE: renamed to SERVICE_CATEGORY).
         * <P>Type: INTEGER</P>
         */
        public static final String V1_MESSAGE_IDENTIFIER = "message_id";

        /**
         * Service category (GSM/UMTS message identifier, CDMA service category).
         * <P>Type: INTEGER</P>
         */
        public static final String SERVICE_CATEGORY = "service_category";

        /**
         * Message language code.
         * <P>Type: TEXT</P>
         */
        public static final String LANGUAGE_CODE = "language";

        /**
         * Message body.
         * <P>Type: TEXT</P>
         */
        public static final String MESSAGE_BODY = "body";

        /**
         * Message delivery time.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DELIVERY_TIME = "date";

        /**
         * Has the message been viewed?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String MESSAGE_READ = "read";

        /**
         * Message format (3GPP or 3GPP2).
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_FORMAT = "format";

        /**
         * Message priority (including emergency).
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_PRIORITY = "priority";

        /**
         * ETWS warning type (ETWS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String ETWS_WARNING_TYPE = "etws_warning_type";

        /**
         * CMAS message class (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_MESSAGE_CLASS = "cmas_message_class";

        /**
         * CMAS category (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_CATEGORY = "cmas_category";

        /**
         * CMAS response type (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_RESPONSE_TYPE = "cmas_response_type";

        /**
         * CMAS severity (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_SEVERITY = "cmas_severity";

        /**
         * CMAS urgency (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_URGENCY = "cmas_urgency";

        /**
         * CMAS certainty (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_CERTAINTY = "cmas_certainty";

        /**
         * Query for list view adapter.
         */
        static final String[] QUERY_COLUMNS = {
                _ID,
                GEOGRAPHICAL_SCOPE,
                PLMN,
                LAC,
                CID,
                SERIAL_NUMBER,
                SERVICE_CATEGORY,
                LANGUAGE_CODE,
                MESSAGE_BODY,
                DELIVERY_TIME,
                MESSAGE_READ,
                MESSAGE_FORMAT,
                MESSAGE_PRIORITY,
                ETWS_WARNING_TYPE,
                CMAS_MESSAGE_CLASS,
                CMAS_CATEGORY,
                CMAS_RESPONSE_TYPE,
                CMAS_SEVERITY,
                CMAS_URGENCY,
                CMAS_CERTAINTY
        };
    }

    /* Column indexes for reading from cursor. */

    static final int COLUMN_ID                  = 0;
    static final int COLUMN_GEOGRAPHICAL_SCOPE  = 1;
    static final int COLUMN_PLMN                = 2;
    static final int COLUMN_LAC                 = 3;
    static final int COLUMN_CID                 = 4;
    static final int COLUMN_SERIAL_NUMBER       = 5;
    static final int COLUMN_SERVICE_CATEGORY    = 6;    // was COLUMN_MESSAGE_IDENTIFIER
    static final int COLUMN_LANGUAGE_CODE       = 7;
    static final int COLUMN_MESSAGE_BODY        = 8;
    static final int COLUMN_DELIVERY_TIME       = 9;
    static final int COLUMN_MESSAGE_READ        = 10;
    static final int COLUMN_MESSAGE_FORMAT      = 11;
    static final int COLUMN_MESSAGE_PRIORITY    = 12;
    static final int COLUMN_ETWS_WARNING_TYPE   = 13;
    static final int COLUMN_CMAS_MESSAGE_CLASS  = 14;
    static final int COLUMN_CMAS_CATEGORY       = 15;
    static final int COLUMN_CMAS_RESPONSE_TYPE  = 16;
    static final int COLUMN_CMAS_SEVERITY       = 17;
    static final int COLUMN_CMAS_URGENCY        = 18;
    static final int COLUMN_CMAS_CERTAINTY      = 19;

    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Columns.GEOGRAPHICAL_SCOPE + " INTEGER,"
                    + Columns.PLMN + " TEXT,"
                    + Columns.LAC + " INTEGER,"
                    + Columns.CID + " INTEGER,"
                    + Columns.SERIAL_NUMBER + " INTEGER,"
                    + Columns.SERVICE_CATEGORY + " INTEGER,"
                    + Columns.LANGUAGE_CODE + " TEXT,"
                    + Columns.MESSAGE_BODY + " TEXT,"
                    + Columns.DELIVERY_TIME + " INTEGER,"
                    + Columns.MESSAGE_READ + " INTEGER,"
                    + Columns.MESSAGE_FORMAT + " INTEGER,"
                    + Columns.MESSAGE_PRIORITY + " INTEGER,"
                    + Columns.ETWS_WARNING_TYPE + " INTEGER,"
                    + Columns.CMAS_MESSAGE_CLASS + " INTEGER,"
                    + Columns.CMAS_CATEGORY + " INTEGER,"
                    + Columns.CMAS_RESPONSE_TYPE + " INTEGER,"
                    + Columns.CMAS_SEVERITY + " INTEGER,"
                    + Columns.CMAS_URGENCY + " INTEGER,"
                    + Columns.CMAS_CERTAINTY + " INTEGER);");
        }

        /** Columns to copy on database upgrade. */
        private static final String[] COLUMNS_V1 = {
                Columns.GEOGRAPHICAL_SCOPE,
                Columns.SERIAL_NUMBER,
                Columns.V1_MESSAGE_CODE,
                Columns.V1_MESSAGE_IDENTIFIER,
                Columns.LANGUAGE_CODE,
                Columns.MESSAGE_BODY,
                Columns.DELIVERY_TIME,
                Columns.MESSAGE_READ,
        };

        private static final int COLUMN_V1_GEOGRAPHICAL_SCOPE   = 0;
        private static final int COLUMN_V1_SERIAL_NUMBER        = 1;
        private static final int COLUMN_V1_MESSAGE_CODE         = 2;
        private static final int COLUMN_V1_MESSAGE_IDENTIFIER   = 3;
        private static final int COLUMN_V1_LANGUAGE_CODE        = 4;
        private static final int COLUMN_V1_MESSAGE_BODY         = 5;
        private static final int COLUMN_V1_DELIVERY_TIME        = 6;
        private static final int COLUMN_V1_MESSAGE_READ         = 7;

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == newVersion) {
                return;
            }
            Log.i(TAG, "Upgrading DB from version " + oldVersion + " to " + newVersion);

            if (oldVersion == 1) {
                db.beginTransaction();
                try {
                    // Step 1: rename original table
                    db.execSQL("DROP TABLE IF EXISTS " + TEMP_TABLE_NAME);
                    db.execSQL("ALTER TABLE " + TABLE_NAME + " RENAME TO " + TEMP_TABLE_NAME);

                    // Step 2: create new table and indices
                    onCreate(db);

                    // Step 3: copy each message into the new table
                    Cursor cursor = db.query(TEMP_TABLE_NAME, COLUMNS_V1, null, null, null, null,
                            null);
                    try {
                        while (cursor.moveToNext()) {
                            upgradeMessageV1ToV2(db, cursor);
                        }
                    } finally {
                        cursor.close();
                    }

                    // Step 4: drop the original table and commit transaction
                    db.execSQL("DROP TABLE " + TEMP_TABLE_NAME);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                oldVersion = 2;
            }
        }

        /**
         * Upgrades a single broadcast message from version 1 to version 2.
         */
        private static void upgradeMessageV1ToV2(SQLiteDatabase db, Cursor cursor) {
            int geographicalScope = cursor.getInt(COLUMN_V1_GEOGRAPHICAL_SCOPE);
            int updateNumber = cursor.getInt(COLUMN_V1_SERIAL_NUMBER);
            int messageCode = cursor.getInt(COLUMN_V1_MESSAGE_CODE);
            int messageId = cursor.getInt(COLUMN_V1_MESSAGE_IDENTIFIER);
            String languageCode = cursor.getString(COLUMN_V1_LANGUAGE_CODE);
            String messageBody = cursor.getString(COLUMN_V1_MESSAGE_BODY);
            long deliveryTime = cursor.getLong(COLUMN_V1_DELIVERY_TIME);
            boolean isRead = (cursor.getInt(COLUMN_V1_MESSAGE_READ) != 0);

            int serialNumber = ((geographicalScope & 0x03) << 14)
                    | ((messageCode & 0x3ff) << 4) | (updateNumber & 0x0f);

            ContentValues cv = new ContentValues(16);
            cv.put(Columns.GEOGRAPHICAL_SCOPE, geographicalScope);
            cv.put(Columns.SERIAL_NUMBER, serialNumber);
            cv.put(Columns.SERVICE_CATEGORY, messageId);
            cv.put(Columns.LANGUAGE_CODE, languageCode);
            cv.put(Columns.MESSAGE_BODY, messageBody);
            cv.put(Columns.DELIVERY_TIME, deliveryTime);
            cv.put(Columns.MESSAGE_READ, isRead);
            cv.put(Columns.MESSAGE_FORMAT, SmsCbMessage.MESSAGE_FORMAT_3GPP);

            int etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_UNKNOWN;
            int cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_UNKNOWN;
            int cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN;
            int cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN;
            int cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN;
            switch (messageId) {
                case SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING:
                    etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE;
                    break;

                case SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING:
                    etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI;
                    break;

                case SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING:
                    etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI;
                    break;

                case SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE:
                    etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE;
                    break;

                case SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE:
                    etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_OTHER_EMERGENCY;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                    cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                    cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                    cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE;
                    break;

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE:
                    cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE;
                    break;
            }

            if (etwsWarningType != SmsCbEtwsInfo.ETWS_WARNING_TYPE_UNKNOWN
                    || cmasMessageClass != SmsCbCmasInfo.CMAS_CLASS_UNKNOWN) {
                cv.put(Columns.MESSAGE_PRIORITY, SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY);
            } else {
                cv.put(Columns.MESSAGE_PRIORITY, SmsCbMessage.MESSAGE_PRIORITY_NORMAL);
            }

            if (etwsWarningType != SmsCbEtwsInfo.ETWS_WARNING_TYPE_UNKNOWN) {
                cv.put(Columns.ETWS_WARNING_TYPE, etwsWarningType);
            }

            if (cmasMessageClass != SmsCbCmasInfo.CMAS_CLASS_UNKNOWN) {
                cv.put(Columns.CMAS_MESSAGE_CLASS, cmasMessageClass);
            }

            if (cmasSeverity != SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN) {
                cv.put(Columns.CMAS_SEVERITY, cmasSeverity);
            }

            if (cmasUrgency != SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN) {
                cv.put(Columns.CMAS_URGENCY, cmasUrgency);
            }

            if (cmasCertainty != SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN) {
                cv.put(Columns.CMAS_CERTAINTY, cmasCertainty);
            }

            db.insert(TABLE_NAME, null, cv);
        }
    }
}