/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.AppOpsManager;
import android.content.Context;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.CellBroadcastMessage;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * ContentProvider for the database of received cell broadcasts.
 */
public class CellBroadcastContentProvider extends ContentProvider {
    private static final String TAG = "CellBroadcastContentProvider";

    /** URI matcher for ContentProvider queries. */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /** Authority string for content URIs. */
    static final String CB_AUTHORITY = "cellbroadcasts";

    /** Content URI for notifying observers. */
    static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts/");

    /** Content URI for notifying observers. */
    static final Uri PRESIDENT_PIN_URI = Uri.parse("content://cellbroadcasts/presidentpin/");
    /** Content URI for channel customized */
    private static final Uri CHANNEL_URI =  Uri.parse("content://cellbroadcasts/channel/");

    /** URI matcher type to get all cell broadcasts. */
    private static final int CB_ALL = 0;

    /** URI matcher type to get a cell broadcast by ID. */
    private static final int CB_ALL_ID = 1;

    private static final int CB_PRESIDENT_PIN = 2;

    /** MIME type for the list of all cell broadcasts. */
    private static final String CB_LIST_TYPE = "vnd.android.cursor.dir/cellbroadcast";

    /** MIME type for an individual cell broadcast. */
    private static final String CB_TYPE = "vnd.android.cursor.item/cellbroadcast";

    private static final String CB_CHANNEL_TYPE ="vnd.android.cursor.item/chanel";

    private static final int CB_CHANNEL_ID = 3;

    /** The projection and the index for query the channel */
    private static final String[] PROJECTION_CHANNEL
            = new String[] { "_id", "name", "number", "enable" };
    private static final int INDEX_NAME = 1;
    private static final int INDEX_CATEGORY = 2;
    private static final int INDEX_ENALBE = 3;

    private static final int ENABLE_VALUE_TRUE = 1;

    static {
        sUriMatcher.addURI(CB_AUTHORITY, null, CB_ALL);
        sUriMatcher.addURI(CB_AUTHORITY, "#", CB_ALL_ID);
        sUriMatcher.addURI(CB_AUTHORITY, "presidentpin", CB_PRESIDENT_PIN);
        sUriMatcher.addURI(CB_AUTHORITY, "channel", CB_CHANNEL_ID);
    }

    /** The database for this content provider. */
    private SQLiteOpenHelper mOpenHelper;
    private static final long TIME12HOURS = 12*60*60*1000;
    /**
     * Initialize content provider.
     * @return true if the provider was successfully loaded, false otherwise
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new CellBroadcastDatabaseHelper(getContext());
        setAppOps(AppOpsManager.OP_READ_CELL_BROADCASTS, AppOpsManager.OP_NONE);
        return true;
    }

    /**
     * Return a cursor for the cell broadcast table.
     * @param uri the URI to query.
     * @param projection the list of columns to put into the cursor, or null.
     * @param selection the selection criteria to apply when filtering rows, or null.
     * @param selectionArgs values to replace ?s in selection string.
     * @param sortOrder how the rows in the cursor should be sorted, or null to sort from most
     *  recently received to least recently received.
     * @return a Cursor or null.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(CellBroadcastDatabaseHelper.TABLE_NAME);

        int match = sUriMatcher.match(uri);

        switch (match) {
            case CB_ALL:
                // get all broadcasts
                break;

            case CB_ALL_ID:
                // get broadcast by ID
                qb.appendWhere("(_id=" + uri.getPathSegments().get(0) + ')');
                break;

            case CB_PRESIDENT_PIN:
                SQLiteDatabase tempDB = mOpenHelper.getReadableDatabase();
                Cursor cs = tempDB.rawQuery(genPresidentPin(projection, selection, sortOrder), null);
                cs.setNotificationUri(getContext().getContentResolver(), PRESIDENT_PIN_URI);
                return cs;

            case CB_CHANNEL_ID:
                qb.setTables(CellBroadcastDatabaseHelper.CHANNEL_TABLE);
                break;

            default:
                Log.e(TAG, "Invalid query: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        String orderBy;
        if (!TextUtils.isEmpty(sortOrder)) {
            orderBy = sortOrder;
        } else {
            orderBy = Telephony.CellBroadcasts.DEFAULT_SORT_ORDER;
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
        }
        return c;
    }

    private String genPresidentPin(String[] projection,String selection,String strOrder) {
        String strProject = "";
        if (projection == null) return "";
        for(int iIndex = 0; iIndex < projection.length; iIndex++) {
            strProject += projection[iIndex];
            if ( (iIndex >= 0) && (iIndex < projection.length -1)) {
                strProject += ",";
            }
        }
        String strClass =  Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS;
        String strTab = CellBroadcastDatabaseHelper.TABLE_NAME;
        String strSel = "SELECT * FROM (SELECT ";
        String strPresidentCon = " WHERE (" + selection + ") AND " + strClass
                + " = 0 ORDER BY " + strOrder;
        String strgenCon = " WHERE (" + selection + ") AND " + strClass
                + " <> 0 ORDER BY " + strOrder;
        String strPresident = strSel + strProject + " FROM " + strTab + strPresidentCon +") AS A";
        String strGen = strSel + strProject + " FROM " + strTab + strgenCon +") AS B";
        String strSQL = strPresident + " UNION ALL " + strGen;
        return strSQL;
    }
    /**
     * Return the MIME type of the data at the specified URI.
     * @param uri the URI to query.
     * @return a MIME type string, or null if there is no type.
     */
    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_ALL:
                return CB_LIST_TYPE;

            case CB_ALL_ID:
                return CB_TYPE;

            case CB_CHANNEL_ID:
                return CB_CHANNEL_TYPE;
            default:
                return null;
        }
    }

    /**
     * Insert a new row. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the content:// URI of the insertion request.
     * @param values a set of column_name/value pairs to add to the database.
     * @return the URI for the newly inserted item.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        Uri result = null;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = sUriMatcher.match(uri);
        ContentValues values;
        long rowID;
        String table = null;

        Log.d(TAG, " insert match = "+match);
        switch (match) {
        case CB_CHANNEL_ID:
            table = CellBroadcastDatabaseHelper.CHANNEL_TABLE;
            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }
            if (!values.containsKey(PROJECTION_CHANNEL[INDEX_NAME])) {
                values.put(PROJECTION_CHANNEL[INDEX_NAME], "");
            }
            if (!values.containsKey(PROJECTION_CHANNEL[INDEX_CATEGORY])) {
                values.put(PROJECTION_CHANNEL[INDEX_CATEGORY], "");
            }
            if (!values.containsKey(PROJECTION_CHANNEL[INDEX_ENALBE])) {
                values.put(PROJECTION_CHANNEL[INDEX_ENALBE], false);
            }
            rowID = db.insert(table, null, values);
            if (rowID > 0) {
                notifyChange();
                result = Uri.parse("content://channel/" + rowID);
            }
            break;

        default:
            throw new UnsupportedOperationException("insert not supported");
        }
        return result;
    }

    /**
     * Delete one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the full URI to query, including a row ID (if a specific record is requested).
     * @param selection an optional restriction to apply to rows when deleting.
     * @return the number of rows affected.
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int deletedRows = 0;
        Uri deleteUri = null;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        deletedRows = deleteOnce(uri, selection, selectionArgs);
        notifyChange();

        return deletedRows;
    }
    public int deleteOnce(Uri uri, String where, String[] whereArgs) {
        int count = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_CHANNEL_ID:
                count = db.delete(CellBroadcastDatabaseHelper.CHANNEL_TABLE, where, whereArgs);
                break;

            default:
                throw new UnsupportedOperationException("Cannot delete that URL: "
                        + uri);
        }
        notifyChange();
        return count;
    }
    /**
     * Update one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the URI to query, potentially including the row ID.
     * @param values a Bundle mapping from column names to new column values.
     * @param selection an optional filter to match rows to update.
     * @return the number of rows affected.
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String extraWhere = null;
        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_CHANNEL_ID:
               count = db.update(CellBroadcastDatabaseHelper.CHANNEL_TABLE, values, selection,
                    selectionArgs);
               break;

            default:
               throw new UnsupportedOperationException("Cannot update that URL: "
                    + uri);
        }
        notifyChange();
        return count;
    }

    /**
     * Internal method to insert a new Cell Broadcast into the database and notify observers.
     * @param message the message to insert
     * @return true if the broadcast is new, false if it's a duplicate broadcast.
     */
    boolean insertNewBroadcast(CellBroadcastMessage message) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        ContentValues cv = message.getContentValues();

        // Note: this method previously queried the database for duplicate message IDs, but this
        // is not compatible with CMAS carrier requirements and could also cause other emergency
        // alerts, e.g. ETWS, to not display if the database is filled with old messages.
        // Use duplicate message ID detection in CellBroadcastAlertService instead of DB query.

        long rowId = db.insert(CellBroadcastDatabaseHelper.TABLE_NAME, null, cv);
        if (rowId == -1) {
            Log.e(TAG, "failed to insert new broadcast into database");
            // Return true on DB write failure because we still want to notify the user.
            // The CellBroadcastMessage will be passed with the intent, so the message will be
            // displayed in the emergency alert dialog, or the dialog that is displayed when
            // the user selects the notification for a non-emergency broadcast, even if the
            // broadcast could not be written to the database.
        }
        return true;    // broadcast is not a duplicate
    }

    /**
     * Internal method to delete a cell broadcast by row ID and notify observers.
     * @param rowId the row ID of the broadcast to delete
     * @return true if the database was updated, false otherwise
     */
    boolean deleteBroadcast(long rowId) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int rowCount = db.delete(CellBroadcastDatabaseHelper.TABLE_NAME,
                Telephony.CellBroadcasts._ID + "=?",
                new String[]{Long.toString(rowId)});
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete broadcast at row " + rowId);
            return false;
        }
    }

    /**
     * Internal method to delete all cell broadcasts and notify observers.
     * @return true if the database was updated, false otherwise
     */
    boolean deleteAllBroadcasts() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int rowCount = db.delete(CellBroadcastDatabaseHelper.TABLE_NAME, null, null);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete all broadcasts");
            return false;
        }
    }

    boolean markItemDeleted(long rowId) {
        deleteAllMarked();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        ContentValues value = new ContentValues(1);
        value.put(CellBroadcastDatabaseHelper.MESSAGE_DELETED, 1);
        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, value,
                Telephony.CellBroadcasts._ID + "=?",
                new String[]{Long.toString(rowId)});
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete broadcast at row " + rowId);
            return false;
        }
    }

    boolean markAllItemsDeleted() {
        deleteAllMarked();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        ContentValues value = new ContentValues(1);
        value.put(CellBroadcastDatabaseHelper.MESSAGE_DELETED, 1);
        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, value, CellBroadcastDatabaseHelper.MESSAGE_DELETED + "=0", null);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete all broadcasts");
            return false;
        }
    }

    void deleteAllMarked() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String strWhere = CellBroadcastDatabaseHelper.MESSAGE_DELETED + "=1 AND "+Telephony.CellBroadcasts.DELIVERY_TIME + "<?";
        long time = System.currentTimeMillis();
        String strExpired = Long.toString(time - TIME12HOURS);
        db.delete(CellBroadcastDatabaseHelper.TABLE_NAME, strWhere,new String[]{strExpired});
    }

    /**
     * Internal method to mark a broadcast as read and notify observers. The broadcast can be
     * identified by delivery time (for new alerts) or by row ID. The caller is responsible for
     * decrementing the unread non-emergency alert count, if necessary.
     *
     * @param columnName the column name to query (ID or delivery time)
     * @param columnValue the ID or delivery time of the broadcast to mark read
     * @return true if the database was updated, false otherwise
     */
    boolean markBroadcastRead(String columnName, long columnValue) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        ContentValues cv = new ContentValues(1);
        cv.put(Telephony.CellBroadcasts.MESSAGE_READ, 1);

        String whereClause = columnName + "=?";
        String[] whereArgs = new String[]{Long.toString(columnValue)};

        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, cv, whereClause, whereArgs);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to mark broadcast read: " + columnName + " = " + columnValue);
            return false;
        }
    }

    /** Callback for users of AsyncCellBroadcastOperation. */
    interface CellBroadcastOperation {
        /**
         * Perform an operation using the specified provider.
         * @param provider the CellBroadcastContentProvider to use
         * @return true if any rows were changed, false otherwise
         */
        boolean execute(CellBroadcastContentProvider provider);
    }

    /**
     * Async task to call this content provider's internal methods on a background thread.
     * The caller supplies the CellBroadcastOperation object to call for this provider.
     */
    static class AsyncCellBroadcastTask extends AsyncTask<CellBroadcastOperation, Void, Void> {
        /** Reference to this app's content resolver. */
        private ContentResolver mContentResolver;

        AsyncCellBroadcastTask(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        /**
         * Perform a generic operation on the CellBroadcastContentProvider.
         * @param params the CellBroadcastOperation object to call for this provider
         * @return void
         */
        @Override
        protected Void doInBackground(CellBroadcastOperation... params) {
            ContentProviderClient cpc = mContentResolver.acquireContentProviderClient(
                    CellBroadcastContentProvider.CB_AUTHORITY);
            CellBroadcastContentProvider provider = (CellBroadcastContentProvider)
                    cpc.getLocalContentProvider();

            if (provider != null) {
                try {
                    boolean changed = params[0].execute(provider);
                    if (changed) {
                        Log.d(TAG, "database changed: notifying observers...");
                        mContentResolver.notifyChange(CONTENT_URI, null, false);
                    }
                } finally {
                    cpc.release();
                }
            } else {
                Log.e(TAG, "getLocalContentProvider() returned null");
            }

            mContentResolver = null;    // free reference to content resolver
            return null;
        }
    }

    private void notifyChange() {
        Log.i(TAG, "Notify change");
        Context context = getContext();
        Set<String> enabledChannels = new HashSet<String>();
        Set<String> disabledChannels = new HashSet<String>();
        Cursor cursor = null;
        try {
            Log.i(TAG, "notifyChange() before query");
            cursor = query(CHANNEL_URI, PROJECTION_CHANNEL, null, null,
                    PROJECTION_CHANNEL[INDEX_CATEGORY]);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(INDEX_ENALBE) == ENABLE_VALUE_TRUE) {
                        enabledChannels.add(cursor.getString(INDEX_CATEGORY));
                    } else {
                        disabledChannels.add(cursor.getString(INDEX_CATEGORY));
                    }
                }
            } else {
                return;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException e: " + e);
            return;
        } finally {
            Log.i(TAG, "notifyChange() finally");
            if (cursor != null) {
                cursor.close();
            }
        }

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(CellBroadcastSettings.KEY_ENABLE_CHANNELS_ALERTS, enabledChannels);
        editor.putStringSet(CellBroadcastSettings.KEY_DISABLE_CHANNELS_ALERTS, disabledChannels);
        editor.commit();

        Intent serviceIntent = new Intent(CellBroadcastConfigService.ACTION_ENABLE_CHANNELS,
                null, context, CellBroadcastConfigService.class);
        context.startService(serviceIntent);
    }
}
