/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;

/**
 * Application wrapper for {@link SmsCbMessage}. This is Parcelable so that
 * decoded broadcast message objects can be passed between running Services.
 * New broadcasts are received by {@link CellBroadcastReceiver},
 * displayed by {@link CellBroadcastAlertService}, and saved to SQLite by
 * {@link CellBroadcastDatabaseService}.
 */
public class CellBroadcastMessage implements Parcelable {

    /** Identifier for getExtra() when adding this object to an Intent. */
    public static final String SMS_CB_MESSAGE_EXTRA =
            "com.android.cellbroadcastreceiver.SMS_CB_MESSAGE";

    /** Bold style for formatting CMAS headers and unread message items. */
    static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

    /** SmsCbMessage. */
    private final SmsCbMessage mSmsCbMessage;

    private final long mDeliveryTime;
    private boolean mIsRead;

    CellBroadcastMessage(SmsCbMessage message) {
        mSmsCbMessage = message;
        mDeliveryTime = System.currentTimeMillis();
        mIsRead = false;
    }

    private CellBroadcastMessage(SmsCbMessage message, long deliveryTime, boolean isRead) {
        mSmsCbMessage = message;
        mDeliveryTime = deliveryTime;
        mIsRead = isRead;
    }

    private CellBroadcastMessage(Parcel in) {
        mSmsCbMessage = new SmsCbMessage(in);
        mDeliveryTime = in.readLong();
        mIsRead = (in.readInt() != 0);
    }

    /** Parcelable: no special flags. */
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        mSmsCbMessage.writeToParcel(out, flags);
        out.writeLong(mDeliveryTime);
        out.writeInt(mIsRead ? 1 : 0);
    }

    public static final Parcelable.Creator<CellBroadcastMessage> CREATOR
            = new Parcelable.Creator<CellBroadcastMessage>() {
        public CellBroadcastMessage createFromParcel(Parcel in) {
            return new CellBroadcastMessage(in);
        }

        public CellBroadcastMessage[] newArray(int size) {
            return new CellBroadcastMessage[size];
        }
    };

    /**
     * Create a CellBroadcastMessage from a row in the database.
     * @param cursor an open SQLite cursor pointing to the row to read
     * @return the new CellBroadcastMessage
     */
    public static CellBroadcastMessage createFromCursor(Cursor cursor) {
        int geoScope = cursor.getInt(CellBroadcastDatabase.COLUMN_GEOGRAPHICAL_SCOPE);
        int serialNum = cursor.getInt(CellBroadcastDatabase.COLUMN_SERIAL_NUMBER);
        int category = cursor.getInt(CellBroadcastDatabase.COLUMN_SERVICE_CATEGORY);
        String language = cursor.getString(CellBroadcastDatabase.COLUMN_LANGUAGE_CODE);
        String body = cursor.getString(CellBroadcastDatabase.COLUMN_MESSAGE_BODY);
        int format = cursor.getInt(CellBroadcastDatabase.COLUMN_MESSAGE_FORMAT);
        int priority = cursor.getInt(CellBroadcastDatabase.COLUMN_MESSAGE_PRIORITY);

        String plmn = null;
        if (!cursor.isNull(CellBroadcastDatabase.COLUMN_PLMN)) {
            plmn = cursor.getString(CellBroadcastDatabase.COLUMN_PLMN);
        }

        int lac = -1;
        if (!cursor.isNull(CellBroadcastDatabase.COLUMN_LAC)) {
            lac = cursor.getInt(CellBroadcastDatabase.COLUMN_LAC);
        }

        int cid = -1;
        if (!cursor.isNull(CellBroadcastDatabase.COLUMN_CID)) {
            cid = cursor.getInt(CellBroadcastDatabase.COLUMN_CID);
        }

        SmsCbLocation location = new SmsCbLocation(plmn, lac, cid);

        SmsCbEtwsInfo etwsInfo = null;
        if (!cursor.isNull(CellBroadcastDatabase.COLUMN_ETWS_WARNING_TYPE)) {
            int warningType = cursor.getInt(CellBroadcastDatabase.COLUMN_ETWS_WARNING_TYPE);
            etwsInfo = new SmsCbEtwsInfo(warningType, false, false, null);
        }

        SmsCbCmasInfo cmasInfo = null;
        if (!cursor.isNull(CellBroadcastDatabase.COLUMN_CMAS_MESSAGE_CLASS)) {
            int messageClass = cursor.getInt(CellBroadcastDatabase.COLUMN_CMAS_MESSAGE_CLASS);

            int cmasCategory = SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN;
            if (!cursor.isNull(CellBroadcastDatabase.COLUMN_CMAS_CATEGORY)) {
                cmasCategory = cursor.getInt(CellBroadcastDatabase.COLUMN_CMAS_CATEGORY);
            }

            int responseType = SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN;
            if (!cursor.isNull(CellBroadcastDatabase.COLUMN_CMAS_RESPONSE_TYPE)) {
                responseType = cursor.getInt(CellBroadcastDatabase.COLUMN_CMAS_RESPONSE_TYPE);
            }

            int severity = SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN;
            if (!cursor.isNull(CellBroadcastDatabase.COLUMN_CMAS_SEVERITY)) {
                severity = cursor.getInt(CellBroadcastDatabase.COLUMN_CMAS_SEVERITY);
            }

            int urgency = SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN;
            if (!cursor.isNull(CellBroadcastDatabase.COLUMN_CMAS_URGENCY)) {
                urgency = cursor.getInt(CellBroadcastDatabase.COLUMN_CMAS_URGENCY);
            }

            int certainty = SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN;
            if (!cursor.isNull(CellBroadcastDatabase.COLUMN_CMAS_CERTAINTY)) {
                certainty = cursor.getInt(CellBroadcastDatabase.COLUMN_CMAS_CERTAINTY);
            }

            cmasInfo = new SmsCbCmasInfo(messageClass, cmasCategory, responseType, severity,
                    urgency, certainty);
        }

        SmsCbMessage msg = new SmsCbMessage(format, geoScope, serialNum, location, category,
                language, body, priority, etwsInfo, cmasInfo);

        long deliveryTime = cursor.getLong(CellBroadcastDatabase.COLUMN_DELIVERY_TIME);
        boolean isRead = (cursor.getInt(CellBroadcastDatabase.COLUMN_MESSAGE_READ) != 0);

        return new CellBroadcastMessage(msg, deliveryTime, isRead);
    }

    /**
     * Return a ContentValues object for insertion into the database.
     * @return a new ContentValues object containing this object's data
     */
    public ContentValues getContentValues() {
        ContentValues cv = new ContentValues(16);
        SmsCbMessage msg = mSmsCbMessage;
        cv.put(CellBroadcastDatabase.Columns.GEOGRAPHICAL_SCOPE, msg.getGeographicalScope());
        SmsCbLocation location = msg.getLocation();
        if (location.getPlmn() != null) {
            cv.put(CellBroadcastDatabase.Columns.PLMN, location.getPlmn());
        }
        if (location.getLac() != -1) {
            cv.put(CellBroadcastDatabase.Columns.LAC, location.getLac());
        }
        if (location.getCid() != -1) {
            cv.put(CellBroadcastDatabase.Columns.CID, location.getCid());
        }
        cv.put(CellBroadcastDatabase.Columns.SERIAL_NUMBER, msg.getSerialNumber());
        cv.put(CellBroadcastDatabase.Columns.SERVICE_CATEGORY, msg.getServiceCategory());
        cv.put(CellBroadcastDatabase.Columns.LANGUAGE_CODE, msg.getLanguageCode());
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_BODY, msg.getMessageBody());
        cv.put(CellBroadcastDatabase.Columns.DELIVERY_TIME, mDeliveryTime);
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_READ, mIsRead);
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_FORMAT, msg.getMessageFormat());
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_PRIORITY, msg.getMessagePriority());

        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        if (etwsInfo != null) {
            cv.put(CellBroadcastDatabase.Columns.ETWS_WARNING_TYPE, etwsInfo.getWarningType());
        }

        SmsCbCmasInfo cmasInfo = mSmsCbMessage.getCmasWarningInfo();
        if (cmasInfo != null) {
            cv.put(CellBroadcastDatabase.Columns.CMAS_MESSAGE_CLASS, cmasInfo.getMessageClass());
            cv.put(CellBroadcastDatabase.Columns.CMAS_CATEGORY, cmasInfo.getCategory());
            cv.put(CellBroadcastDatabase.Columns.CMAS_RESPONSE_TYPE, cmasInfo.getResponseType());
            cv.put(CellBroadcastDatabase.Columns.CMAS_SEVERITY, cmasInfo.getSeverity());
            cv.put(CellBroadcastDatabase.Columns.CMAS_URGENCY, cmasInfo.getUrgency());
            cv.put(CellBroadcastDatabase.Columns.CMAS_CERTAINTY, cmasInfo.getCertainty());
        }

        return cv;
    }

    /**
     * Set or clear the "read message" flag.
     * @param isRead true if the message has been read; false if not
     */
    public void setIsRead(boolean isRead) {
        mIsRead = isRead;
    }

    public String getLanguageCode() {
        return mSmsCbMessage.getLanguageCode();
    }

    public int getServiceCategory() {
        return mSmsCbMessage.getServiceCategory();
    }

    public long getDeliveryTime() {
        return mDeliveryTime;
    }

    public String getMessageBody() {
        return mSmsCbMessage.getMessageBody();
    }

    public boolean isRead() {
        return mIsRead;
    }

    public int getSerialNumber() {
        return mSmsCbMessage.getSerialNumber();
    }

    /**
     * Returns a styled CharSequence containing the message body and optional CMAS alert headers.
     * @param context a Context for resource string access
     * @return a CharSequence for display in the broadcast alert dialog
     */
    public CharSequence getFormattedMessageBody(Context context) {
        if (isCmasMessage()) {
            SmsCbCmasInfo cmasInfo = mSmsCbMessage.getCmasWarningInfo();
            SpannableStringBuilder buf = new SpannableStringBuilder();

            // CMAS category
            int categoryId = getCmasCategoryResId(cmasInfo);
            if (categoryId != 0) {
                buf.append(context.getText(R.string.cmas_category_heading));
                buf.append(context.getText(categoryId));
                buf.append('\n');
            }

            // CMAS response type
            int responseId = getCmasResponseResId(cmasInfo);
            if (responseId != 0) {
                buf.append(context.getText(R.string.cmas_response_heading));
                buf.append(context.getText(responseId));
                buf.append('\n');
            }

            // CMAS severity
            int severityId = getCmasSeverityResId(cmasInfo);
            if (severityId != 0) {
                buf.append(context.getText(R.string.cmas_severity_heading));
                buf.append(context.getText(severityId));
                buf.append('\n');
            }

            // CMAS urgency
            int urgencyId = getCmasUrgencyResId(cmasInfo);
            if (urgencyId != 0) {
                buf.append(context.getText(R.string.cmas_urgency_heading));
                buf.append(context.getText(urgencyId));
                buf.append('\n');
            }

            // CMAS certainty
            int certaintyId = getCmasCertaintyResId(cmasInfo);
            if (certaintyId != 0) {
                buf.append(context.getText(R.string.cmas_certainty_heading));
                buf.append(context.getText(certaintyId));
                buf.append('\n');
            }

            // Style all headings in bold
            buf.setSpan(STYLE_BOLD, 0, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

            buf.append(mSmsCbMessage.getMessageBody());
            return buf;
        } else {
            return mSmsCbMessage.getMessageBody();
        }
    }

    /**
     * Returns the string resource ID for the CMAS category.
     * @return a string resource ID, or 0 if the CMAS category is unknown or not present
     */
    private static int getCmasCategoryResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getCategory()) {
            case SmsCbCmasInfo.CMAS_CATEGORY_GEO:
                return R.string.cmas_category_geo;

            case SmsCbCmasInfo.CMAS_CATEGORY_MET:
                return R.string.cmas_category_met;

            case SmsCbCmasInfo.CMAS_CATEGORY_SAFETY:
                return R.string.cmas_category_safety;

            case SmsCbCmasInfo.CMAS_CATEGORY_SECURITY:
                return R.string.cmas_category_security;

            case SmsCbCmasInfo.CMAS_CATEGORY_RESCUE:
                return R.string.cmas_category_rescue;

            case SmsCbCmasInfo.CMAS_CATEGORY_FIRE:
                return R.string.cmas_category_fire;

            case SmsCbCmasInfo.CMAS_CATEGORY_HEALTH:
                return R.string.cmas_category_health;

            case SmsCbCmasInfo.CMAS_CATEGORY_ENV:
                return R.string.cmas_category_env;

            case SmsCbCmasInfo.CMAS_CATEGORY_TRANSPORT:
                return R.string.cmas_category_transport;

            case SmsCbCmasInfo.CMAS_CATEGORY_INFRA:
                return R.string.cmas_category_infra;

            case SmsCbCmasInfo.CMAS_CATEGORY_CBRNE:
                return R.string.cmas_category_cbrne;

            case SmsCbCmasInfo.CMAS_CATEGORY_OTHER:
                return R.string.cmas_category_other;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS response type.
     * @return a string resource ID, or 0 if the CMAS response type is unknown or not present
     */
    private static int getCmasResponseResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getResponseType()) {
            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_SHELTER:
                return R.string.cmas_response_shelter;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EVACUATE:
                return R.string.cmas_response_evacuate;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_PREPARE:
                return R.string.cmas_response_prepare;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EXECUTE:
                return R.string.cmas_response_execute;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_MONITOR:
                return R.string.cmas_response_monitor;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_AVOID:
                return R.string.cmas_response_avoid;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_ASSESS:
                return R.string.cmas_response_assess;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_NONE:
                return R.string.cmas_response_none;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS severity.
     * @return a string resource ID, or 0 if the CMAS severity is unknown or not present
     */
    private static int getCmasSeverityResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getSeverity()) {
            case SmsCbCmasInfo.CMAS_SEVERITY_EXTREME:
                return R.string.cmas_severity_extreme;

            case SmsCbCmasInfo.CMAS_SEVERITY_SEVERE:
                return R.string.cmas_severity_severe;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS urgency.
     * @return a string resource ID, or 0 if the CMAS urgency is unknown or not present
     */
    private static int getCmasUrgencyResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getUrgency()) {
            case SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE:
                return R.string.cmas_urgency_immediate;

            case SmsCbCmasInfo.CMAS_URGENCY_EXPECTED:
                return R.string.cmas_urgency_expected;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS certainty.
     * @return a string resource ID, or 0 if the CMAS certainty is unknown or not present
     */
    private static int getCmasCertaintyResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getCertainty()) {
            case SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED:
                return R.string.cmas_certainty_observed;

            case SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY:
                return R.string.cmas_certainty_likely;

            default:
                return 0;
        }
    }

    /**
     * Return whether the broadcast is an emergency (PWS) message type.
     * This includes lower priority test messages and Amber alerts.
     *
     * All public alerts show the flashing warning icon in the dialog,
     * but only emergency alerts play the alert sound and speak the message.
     *
     * @return true if the message is PWS type; false otherwise
     */
    public boolean isPublicAlertMessage() {
        return mSmsCbMessage.isEmergencyMessage();
    }

    /**
     * Returns whether the broadcast is an emergency (PWS) message type,
     * including test messages, but excluding lower priority Amber alert broadcasts.
     *
     * @return true if the message is PWS type, excluding Amber alerts
     */
    public boolean isEmergencyAlertMessage() {
        if (!mSmsCbMessage.isEmergencyMessage()) {
            return false;
        }
        SmsCbCmasInfo cmasInfo = mSmsCbMessage.getCmasWarningInfo();
        if (cmasInfo != null &&
                cmasInfo.getMessageClass() == SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY) {
            return false;
        }
        return true;
    }

    /**
     * Return whether the broadcast is an ETWS emergency message type.
     * @return true if the message is ETWS emergency type; false otherwise
     */
    public boolean isEtwsMessage() {
        return mSmsCbMessage.isEtwsMessage();
    }

    /**
     * Return whether the broadcast is a CMAS emergency message type.
     * @return true if the message is CMAS emergency type; false otherwise
     */
    public boolean isCmasMessage() {
        return mSmsCbMessage.isCmasMessage();
    }

    /**
     * Return the CMAS message class.
     * @return the CMAS message class, e.g. {@link SmsCbCmasInfo#CMAS_CLASS_SEVERE_THREAT}, or
     *  {@link SmsCbCmasInfo#CMAS_CLASS_UNKNOWN} if this is not a CMAS alert
     */
    public int getCmasMessageClass() {
        if (mSmsCbMessage.isCmasMessage()) {
            return mSmsCbMessage.getCmasWarningInfo().getMessageClass();
        } else {
            return SmsCbCmasInfo.CMAS_CLASS_UNKNOWN;
        }
    }

    /**
     * Return whether the broadcast is an ETWS popup alert.
     * This method checks the message ID and the message code.
     * @return true if the message indicates an ETWS popup alert
     */
    public boolean isEtwsPopupAlert() {
        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        return etwsInfo != null && etwsInfo.isPopupAlert();
    }

    /**
     * Return whether the broadcast is an ETWS emergency user alert.
     * This method checks the message ID and the message code.
     * @return true if the message indicates an ETWS emergency user alert
     */
    public boolean isEtwsEmergencyUserAlert() {
        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        return etwsInfo != null && etwsInfo.isEmergencyUserAlert();
    }

    /**
     * Return whether the broadcast is an ETWS test message.
     * @return true if the message is an ETWS test message; false otherwise
     */
    public boolean isEtwsTestMessage() {
        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        return etwsInfo != null &&
                etwsInfo.getWarningType() == SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE;
    }

    public int getDialogTitleResource() {
        // ETWS warning types
        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        if (etwsInfo != null) {
            switch (etwsInfo.getWarningType()) {
                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE:
                    return R.string.etws_earthquake_warning;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI:
                    return R.string.etws_tsunami_warning;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI:
                    return R.string.etws_earthquake_and_tsunami_warning;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE:
                    return R.string.etws_test_message;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_OTHER_EMERGENCY:
                default:
                    return R.string.etws_other_emergency_type;
            }
        }

        // CMAS warning types
        SmsCbCmasInfo cmasInfo = mSmsCbMessage.getCmasWarningInfo();
        if (cmasInfo != null) {
            switch (cmasInfo.getMessageClass()) {
                case SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT:
                    return R.string.cmas_presidential_level_alert;

                case SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT:
                    return R.string.cmas_extreme_alert;

                case SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT:
                    return R.string.cmas_severe_alert;

                case SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY:
                    return R.string.cmas_amber_alert;

                case SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST:
                    return R.string.cmas_required_monthly_test;

                case SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE:
                    return R.string.cmas_exercise_alert;

                case SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE:
                    return R.string.cmas_operator_defined_alert;

                default:
                    return R.string.pws_other_message_identifiers;
            }
        }

        if (mSmsCbMessage.isEmergencyMessage()) {
            return R.string.pws_other_message_identifiers;
        } else {
            return R.string.cb_other_message_identifiers;
        }
    }

    /**
     * Return the abbreviated date string for the message delivery time.
     * @param context the context object
     * @return a String to use in the broadcast list UI
     */
    String getDateString(Context context) {
        int flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT | DateUtils.FORMAT_SHOW_TIME |
                DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_CAP_AMPM;
        return DateUtils.formatDateTime(context, mDeliveryTime, flags);
    }

    /**
     * Return the date string for the message delivery time, suitable for text-to-speech.
     * @param context the context object
     * @return a String for populating the list item AccessibilityEvent for TTS
     */
    String getSpokenDateString(Context context) {
        int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE;
        return DateUtils.formatDateTime(context, mDeliveryTime, flags);
    }
}
