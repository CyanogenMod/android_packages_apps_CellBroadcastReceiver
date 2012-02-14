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

import android.content.AsyncTaskLoader;
import android.content.CancellationSignal;
import android.content.Context;
import android.content.OperationCanceledException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;

/**
 * Async task loader for Cell Broadcast database cursors. Based on
 * {@link android.content.CursorLoader}, which is used to query content providers.
 */
class CellBroadcastCursorLoader extends AsyncTaskLoader<Cursor> {

    static final String TAG = "CellBroadcastCursorLoader";
    static final String TABLE_NAME = "broadcasts";

    private SQLiteDatabase mDatabase;

    Cursor mCursor;
    CancellationSignal mCancellationSignal;

    /**
     * Creates an empty cell broadcast cursor loader. The query is hardcoded into the class.
     */
    public CellBroadcastCursorLoader(Context context) {
        super(context);
    }

    /**
     * Called on a worker thread to perform the actual load and to return the result of the load
     * operation.
     *
     * @return The result of the load operation.
     * @throws android.content.OperationCanceledException
     *          if the load is canceled during execution.
     * @see #isLoadInBackgroundCanceled
     * @see #cancelLoadInBackground
     * @see #onCanceled
     */
    @Override
    public Cursor loadInBackground() {
        synchronized (this) {
            if (isLoadInBackgroundCanceled()) {
                throw new OperationCanceledException();
            }
            mCancellationSignal = new CancellationSignal();
        }
        try {
            if (mDatabase == null) {
                if (DBG) Log.d(TAG, "loadInBackground: opening SQLite database");
                mDatabase = new CellBroadcastDatabase.DatabaseHelper(getContext())
                        .getReadableDatabase();
            }
            Cursor cursor = mDatabase.query(false, TABLE_NAME,
                    CellBroadcastDatabase.Columns.QUERY_COLUMNS, null, null, null, null,
                    CellBroadcastDatabase.Columns.DELIVERY_TIME + " DESC", null,
                    mCancellationSignal);

            if (cursor != null) {
                // Ensure the cursor window is filled
                int count = cursor.getCount();
                if (DBG) Log.d(TAG, "loadInBackground() cursor row count = " + count);
            }
            return cursor;
        } finally {
            synchronized (this) {
                mCancellationSignal = null;
            }
        }
    }

    @Override
    public void cancelLoadInBackground() {
        super.cancelLoadInBackground();

        synchronized (this) {
            if (mCancellationSignal != null) {
                mCancellationSignal.cancel();
            }
        }
    }

    /* Runs on the UI thread */
    @Override
    public void deliverResult(Cursor cursor) {
        if (isReset()) {
            if (DBG) Log.d(TAG, "isReset() is true, closing cursor and returning");
            // An async query came in while the loader is stopped
            if (cursor != null) {
                cursor.close();
            }
            return;
        }
        Cursor oldCursor = mCursor;
        mCursor = cursor;

        if (isStarted()) {
            if (DBG) Log.d(TAG, "isStarted() is true, delivering result");
            super.deliverResult(cursor);
        } else {
            if (DBG) Log.d(TAG, "isStarted() is false, not delivering!!!");
        }

        if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
            if (DBG) Log.d(TAG, "closing the old cursor...");
            oldCursor.close();
        }
    }

    /**
     * Starts an asynchronous query of the database. When the result is ready the callbacks
     * will be called on the UI thread. If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately.
     *
     * Must be called from the UI thread
     */
    @Override
    protected void onStartLoading() {
        if (mCursor != null) {
            Log.d(TAG, "onStartLoading() delivering existing cursor");
            deliverResult(mCursor);
        }
        if (takeContentChanged() || mCursor == null) {
            Log.d(TAG, "onStartLoading() calling forceLoad()");
            forceLoad();
        }
    }

    /**
     * Must be called from the UI thread
     */
    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
        Log.d(TAG, "onStopLoading() called cancelLoad()");
    }

    @Override
    public void onCanceled(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
            Log.d(TAG, "onCanceled() closed the cursor");
        }
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
            Log.d(TAG, "onReset() closed the cursor");
        }
        mCursor = null;
    }
}
