/*
 *  Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are
 *  met:
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above
 *        copyright notice, this list of conditions and the following
 *        disclaimer in the documentation and/or other materials provided
 *        with the distribution.
 *      * Neither the name of The Linux Foundation nor the names of its
 *        contributors may be used to endorse or promote products derived
 *        from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.cellbroadcastreceiver;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.view.WindowManager;
import android.util.Log;
import com.android.internal.telephony.CommandException;

import java.util.ArrayList;

public class TimeConsumingPreferenceActivity extends PreferenceActivity
                        implements DialogInterface.OnClickListener,
                        DialogInterface.OnCancelListener {
    private static final String LOG_TAG = "CellBroadcast/TimeConsumingPreferenceActivity";
    private static final boolean DBG = true;

    private class ErrorFinishListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            finish();
        }
    }
    private final DialogInterface.OnClickListener mErrorFinishListener
            = new ErrorFinishListener();

    private static final int CONSUME_READING_DIALOG = 100;
    private static final int CONSUME_SAVING_DIALOG = 200;

    public static final int ERROR_FINISH_DIALOG = 300;

    private final ArrayList<String> mReadBusyList = new ArrayList<String>();
    private final ArrayList<String> mSaveBusyList = new ArrayList<String>();

    protected boolean mIsForeground = false;

    @Override
    protected Dialog onCreateDialog(int id) {
        Log.d(LOG_TAG, "onCreateDialog id =" + id);
        if (id == CONSUME_READING_DIALOG || id == CONSUME_SAVING_DIALOG) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(getText(R.string.updating_title));
            dialog.setCanceledOnTouchOutside(false);
            dialog.setIndeterminate(true);

            switch (id) {
                case CONSUME_READING_DIALOG:
                    if (DBG) Log.d(LOG_TAG, "showDialog(CONSUME_READING_DIALOG)");
                    dialog.setMessage(getText(R.string.reading_settings));
                    dialog.setCancelable(true);
                    dialog.setOnCancelListener(this);
                    return dialog;
                case CONSUME_SAVING_DIALOG:
                    if (DBG) Log.d(LOG_TAG, "showDialog(CONSUME_SAVING_DIALOG)");
                    dialog.setMessage(getText(R.string.updating_settings));
                    dialog.setCancelable(false);
                    return dialog;
                default:
                    break;
            }
        }

        if (id == ERROR_FINISH_DIALOG) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            int msgId;
            int titleId = R.string.error_updating_title;
            msgId = R.string.error_finish_dialog_title;
            b.setTitle(getText(titleId));
            b.setMessage(getText(msgId));
            b.setNeutralButton(R.string.close_dialog, mErrorFinishListener);
            b.setCancelable(false);
            AlertDialog dialog = b.create();
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            return dialog;
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume");
        mIsForeground = true;
        if (mSaveBusyList.size() == 1) {
            showDialog(CONSUME_SAVING_DIALOG);
        } else if (mReadBusyList.size() == 1) {
            showDialog(CONSUME_READING_DIALOG);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsForeground = false;
    }

    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
    }

    public void onStartConsume(Preference preference, boolean reading) {
        if (DBG) Log.d(LOG_TAG, "onStartConsume, preference="
                + (preference != null ? preference.getKey() : "NULL")
                + ", reading=" + reading);

        if (!reading) {
            mSaveBusyList.add(preference.getKey());
            if ((mSaveBusyList.size() == 1) && mIsForeground) {
                showDialog(CONSUME_SAVING_DIALOG);
            }
        } else {
            mReadBusyList.add(preference.getKey());
            if ((mReadBusyList.size() == 1) && mIsForeground) {
                showDialog(CONSUME_READING_DIALOG);
            }
        }
    }

    public void onFinishConsume(Preference preference, boolean reading) {
        if (DBG) Log.d(LOG_TAG, "onFinishConsume, preference="
                + (preference != null ? preference.getKey() : "NULL")
                + ", reading=" + reading);
        if (!reading) {
            mSaveBusyList.remove(preference.getKey());
            if (mSaveBusyList.isEmpty()) {
                removeDialog(CONSUME_SAVING_DIALOG);
            }
        } else {
            mReadBusyList.remove(preference.getKey());
            if (mReadBusyList.isEmpty()) {
                removeDialog(CONSUME_READING_DIALOG);
            }
        }
        preference.setEnabled(true);
    }

    public void onErrorFinish(Preference preference, int error) {
        if (DBG) Log.d(LOG_TAG, "onErrorFinish, preference="
                + (preference != null ? preference.getKey() : "NULL"));

        if (mIsForeground) {
            showDialog(error);
        }
    }

    public void onCancel(DialogInterface dialog) {
        finish();
    }

    protected void dismissConsumeDialog() {
        mReadBusyList.clear();
        dismissConsumeIfNeed(CONSUME_READING_DIALOG);
        mSaveBusyList.clear();
        dismissConsumeIfNeed(CONSUME_SAVING_DIALOG);
    }

    private void dismissConsumeIfNeed(int id) {
        try {
             dismissDialog(id);
        } catch (IllegalArgumentException e) {
            // If no dialog, skip.
        }
    }
}
