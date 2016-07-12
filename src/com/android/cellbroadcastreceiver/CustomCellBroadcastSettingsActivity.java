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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;

import java.util.ArrayList;
import java.util.HashMap;

public class CustomCellBroadcastSettingsActivity extends TimeConsumingPreferenceActivity
        implements DialogInterface.OnClickListener, Preference.OnPreferenceClickListener {

    private static final boolean DEBUG = true;
    private static final String LOG_TAG = "CustomCellBroadcastSettingsActivity";

    private static final int FILTER_NAME_LENGTH = 24;
    private static final int CB_CATEGORY_MAX = 65535;

    private static final int FILTER_OFFSET = 3;
    private static final int MENU_FILTER_DELETE = 0x1;
    private static final int MENU_FILTER_EDIT = 0x2;

    private static final Uri CB_CHANNEL_URI =  Uri.parse("content://cellbroadcasts/channel/");
    private static final String FILTER_ID = "_id";
    private static final String FILTER_NAME = "name";
    private static final String FILTER_CATEGORY = "number";
    private static final String FILTER_STAT = "enable";

    private static final String KEY_BUTTON_ADD_CHANNEL = "button_add_channel";
    private static final String KEY_CHECKBOX_CB_Setting = "checkbox_cb_setting";
    private static final String KEY_MENU_CHANNEL_LIST = "menu_channel_list";

    private PreferenceCategory mFilterListPreference;
    private PreferenceScreen mAddFilterPreference;
    private CheckBoxPreference mCBSwitchPreference;
    private ProgressDialog mBusyDialog;

    private HashMap<String, CellBroadcastChannel> mChannelFilterMap;
    private ArrayList<CellBroadcastChannel> mFilterArray = new ArrayList<CellBroadcastChannel>();

    private CbCustomizedAsyncTask mCbCustomizedAsyncTask = null;
    private ArrayList<SmsBroadcastConfigInfo> mSmsCbCfgList;

    private class CbCustomizedAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            if (DEBUG) Log.d(LOG_TAG, "onPreExecute(): Preparing the AsyncTask");
            if (CustomCellBroadcastSettingsActivity.this.isDestroyed()
                    || CustomCellBroadcastSettingsActivity.this.isFinishing()) {
                if (DEBUG) Log.d(LOG_TAG, "onPreExecute(): The root activity no longer exists");
                return;
            }
            updateUIStatus(false);
            dismissConsumeDialog();
            mBusyDialog = new ProgressDialog(CustomCellBroadcastSettingsActivity.this);
            mBusyDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mBusyDialog.setCanceledOnTouchOutside(false);
            mBusyDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (mCbCustomizedAsyncTask != null) {
                        mCbCustomizedAsyncTask.cancel(true);
                        mCbCustomizedAsyncTask = null;
                    }
                }
            });
            mBusyDialog.show();
        }

        @Override
        protected void onCancelled() {
            if (DEBUG) Log.d(LOG_TAG, "onCancelled(): The task is cancelled.");
            updateUI();
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.d(LOG_TAG, "onPostExecute(): AsyncTask finished ");
            if (!result) {
                onErrorFinish(mCBSwitchPreference, ERROR_FINISH_DIALOG);
            }
            updateUI();
            if (!TelephonyManager.getDefault().hasIccCard()) {
                mCBSwitchPreference.setEnabled(false);
                mFilterListPreference.setEnabled(false);
                mAddFilterPreference.setEnabled(false);
            }

        }

        @Override
        protected Boolean doInBackground(Void... none) {
            if (DEBUG) Log.i(LOG_TAG, "doInBackground(): AsyncTask is ongoing");
            if (queryFilters()) {
                initChannelFilterMap();
                updateCurrentFilter(mSmsCbCfgList);
            } else {
                 return false;
            }
            return true;
        }
    }

    private class CellBroadcastChannel {
        private int mKeyId;
        private int mCategory;
        private String mChannelName;
        private boolean mEnabled;

        public CellBroadcastChannel(CellBroadcastChannel channel) {
            mKeyId = channel.getKeyId();
            mCategory = channel.getChannelId();
            mChannelName = channel.getChannelName();
            mEnabled = channel.getChannelStatus();
        }

        public CellBroadcastChannel(int keyId, int numberId, String name,
                boolean state) {
            mKeyId = keyId;
            mCategory = numberId;
            mChannelName = name;
            mEnabled = state;
        }

        public CellBroadcastChannel(int numberId, String name, boolean state) {
            mCategory = numberId;
            mChannelName = name;
            mEnabled = state;
        }

        public int getKeyId() {
            return mKeyId;
        }

        public void setKeyId(int id) {
            mKeyId = id;
        }

        public int getChannelId() {
            return mCategory;
        }

        public void setChannelId(int id) {
            mCategory = id;
        }

        public String getChannelName() {
            return mChannelName;
        }

        public void setChannelName(String name) {
            mChannelName = name;
        }

        public boolean getChannelStatus() {
            return mEnabled;
        }

        public void setChannelStatus(boolean state) {
            mEnabled = state;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initActivity();
    }

    @Override
    public void onResume() {
        super.onResume();
        pollCellBroadcastConfig();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissConsumeDialog();
        if (mCbCustomizedAsyncTask != null) {
            mCbCustomizedAsyncTask.cancel(true);
            mCbCustomizedAsyncTask = null;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuinfo
                = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int index = menuinfo.position - FILTER_OFFSET;
        CellBroadcastChannel oldFilter = mFilterArray.get(index);
        switch (item.getItemId()) {
            case MENU_FILTER_DELETE:
                oldFilter.setChannelStatus(false);
                if (deleteChannelFromProvider(oldFilter)) {
                    setCellBroadcastConfig();
                    SmsManager.getDefault().disableCellBroadcast(oldFilter.getChannelId(),
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                } else {
                    onErrorFinish(mCBSwitchPreference, ERROR_FINISH_DIALOG);
                }
                break;
            case MENU_FILTER_EDIT:
                showEditFilterDialog(oldFilter);
                break;
            default:
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo menuinfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (menuinfo == null) {
            if (DEBUG) Log.e(LOG_TAG, "onCreateContextMenu, no menuInfo!");
            return;
        }
        int position = menuinfo.position;
        if (position >= FILTER_OFFSET) {
            int index = position - FILTER_OFFSET;
            CellBroadcastChannel channel = mFilterArray.get(index);
            menu.setHeaderTitle(R.string.cb_menu_tile);
            menu.add(1, MENU_FILTER_EDIT, 0, R.string.cb_menu_edit);
            menu.add(2, MENU_FILTER_DELETE, 0, R.string.cb_menu_delete);
        }
    }

    @Override
    protected void dismissConsumeDialog() {
        super.dismissConsumeDialog();
        if (mBusyDialog != null && mBusyDialog.isShowing()) {
            mBusyDialog.dismiss();
            mBusyDialog = null;
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        if (preference.equals(mCBSwitchPreference)) {
            final SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext()).edit();
            boolean isCBEnabled = mCBSwitchPreference.isChecked();
            mFilterListPreference.setEnabled(isCBEnabled);
            mAddFilterPreference.setEnabled(isCBEnabled);
            editor.putBoolean(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, isCBEnabled);
            editor.commit();
            return true;
        } else if (preference.equals(mAddFilterPreference)) {
            showAddFilterDialog();
            return true;
        }
        return false;
    }

    private void initActivity() {
        SharedPreferences prefs
                = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final String KEY_CHANNEL_50_ADDED = "is50added";
        final int CHANNEL_50_CATEGORY = 50;
        if (!prefs.getBoolean(KEY_CHANNEL_50_ADDED, false)) {
            insertCbFilterToProvider(new CellBroadcastChannel(CHANNEL_50_CATEGORY,
                    getString(R.string.cb_channel_50_name), true));
            prefs.edit().putBoolean(KEY_CHANNEL_50_ADDED, true).commit();
        }

        addPreferencesFromResource(R.xml.cell_broadcast_settings);
        mAddFilterPreference = (PreferenceScreen) findPreference(KEY_BUTTON_ADD_CHANNEL);
        mFilterListPreference = (PreferenceCategory) findPreference(KEY_MENU_CHANNEL_LIST);

        mCBSwitchPreference = (CheckBoxPreference) findPreference(KEY_CHECKBOX_CB_Setting);
        mCBSwitchPreference.setOnPreferenceClickListener(this);
        boolean enableEmergencyAlerts
                = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        mCBSwitchPreference.setChecked(enableEmergencyAlerts);
        SharedPreferences pref
                = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!pref.contains(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS)) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
            editor.commit();
        }

        mAddFilterPreference.setOnPreferenceClickListener(this);
        registerForContextMenu(this.getListView());
    }

    private void updateUI() {
        updateFilterUIList();
        updateUIStatus(true);
        dismissConsumeDialog();
    }

    private void updateUIStatus(boolean statue) {
        boolean enableEmergencyAlerts
                = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        mCBSwitchPreference.setEnabled(statue);
        mCBSwitchPreference.setChecked(enableEmergencyAlerts);
        mFilterListPreference.setEnabled(statue && enableEmergencyAlerts);
        mAddFilterPreference.setEnabled(statue && enableEmergencyAlerts);
    }

    private void pollCellBroadcastConfig() {
        if (DEBUG) Log.d(LOG_TAG, "pollCellBroadcastConfig start");
        onStartConsume(mCBSwitchPreference, true);
        handlePollCellBroadcastConfigResponse();
    }

    private void handlePollCellBroadcastConfigResponse() {
        onFinishConsume(mAddFilterPreference, true);
        onFinishConsume(mCBSwitchPreference, true);
        if (mCbCustomizedAsyncTask != null) {
            mCbCustomizedAsyncTask.cancel(true);
            mCbCustomizedAsyncTask = null;
        }
        mCbCustomizedAsyncTask = new CbCustomizedAsyncTask();
        mCbCustomizedAsyncTask.execute();
    }

    private void showEditFilterDialog(final CellBroadcastChannel oldFilter) {
        final View filterEditView
                = LayoutInflater.from(this).inflate(R.layout.pref_add_channel, null);
        final EditText filterName = (EditText) filterEditView.findViewById(R.id.edit_channel_name);
        final EditText filterCat = (EditText) filterEditView.findViewById(R.id.edit_channel_number);
        final CheckBox filterState
                = (CheckBox) filterEditView.findViewById(R.id.checkbox_channel_enable);
        filterName.setText(oldFilter.getChannelName());
        filterCat.setText(String.valueOf(oldFilter.getChannelId()));
        filterState.setChecked(oldFilter.getChannelStatus());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(filterEditView)
                .setTitle(R.string.cb_channel_dialog_edit_channel)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int where) {
                String name = filterName.getText().toString();
                String num = filterCat.getText().toString();
                boolean checked = filterState.isChecked();
                String errorText = "";
                if (!isFilterNameValid(name)) {
                    errorText += getString(R.string.cb_error_channel_name);
                }
                if (!isCategoryValid(num)) {
                    errorText += "\n" + getString(R.string.cb_error_channel_num);
                }
                if (errorText.equals("")) {
                    int newFilterId = Integer.valueOf(num).intValue();
                    int tempOldChannelId = oldFilter.getChannelId();
                    int tmpOldKeyId = oldFilter.getKeyId();
                    if (!checkCategoryIdExist(newFilterId, tmpOldKeyId)) {
                        dialog.dismiss();
                        CellBroadcastChannel newFilter =
                            new CellBroadcastChannel(tmpOldKeyId, newFilterId, name,
                            checked);
                        oldFilter.setChannelStatus(false);
                        int tempNewChannelId = newFilter.getChannelId();
                        if (updateCbFilterToProvider(oldFilter, newFilter)) {
                            setCellBroadcastConfig();
                        } else {
                            onErrorFinish(mCBSwitchPreference, ERROR_FINISH_DIALOG);
                        }
                    } else {
                        showToast(getString(R.string.cb_error_channel_id_exist));
                    }
                } else {
                    showToast(errorText);
                }
            }
        }).create();
        dialog.show();
        requestInputPanel(dialog);
    }

    private void updateFilterUIList() {
        // Update the channel filter ui list from filter array
        if (DEBUG) Log.d(LOG_TAG, "updateFilterUIList start");
        mFilterListPreference.removeAll();

        for (CellBroadcastChannel filter : mFilterArray) {
            final CheckBoxPreference channel = new CheckBoxPreference(this);
            int keyId = filter.getKeyId();
            String filterName = filter.getChannelName();
            final int channelId = filter.getChannelId();
            boolean filterState = filter.getChannelStatus();
            String title = filterName + "(" + String.valueOf(channelId) + ")";
            channel.setTitle(title);
            channel.setSummaryOn(R.string.enable);
            channel.setSummaryOff(R.string.disable);
            channel.setChecked(filterState);
            final CellBroadcastChannel oldFilter
                    = new CellBroadcastChannel(keyId, channelId, filterName, filterState);

            channel.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference perf) {
                    setFilterState(channelId, channel.isChecked());
                    CellBroadcastChannel newFilter = new CellBroadcastChannel(oldFilter);
                    newFilter.setChannelStatus(!oldFilter.getChannelStatus());
                    int tempOldChannelId = oldFilter.getChannelId();
                    if (updateCbFilterToProvider(oldFilter, newFilter)) {
                        setCellBroadcastConfig();
                    } else {
                        onErrorFinish(mCBSwitchPreference, ERROR_FINISH_DIALOG);
                    }

                    return false;
                }
            });
            mFilterListPreference.addPreference(channel);
        }
        if (DEBUG) Log.d(LOG_TAG, "updateFilterUIList end");
    }

    private void initChannelFilterMap() {
        mChannelFilterMap = new HashMap<String, CellBroadcastChannel>();

        for (CellBroadcastChannel filterChannel : mFilterArray) {
            if (mCbCustomizedAsyncTask.isCancelled()) {
                break;
            }
            int id = filterChannel.getChannelId();
            mChannelFilterMap.put(String.valueOf(id), filterChannel);
        }
    }

    private void showAddFilterDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(LayoutInflater.from(this).inflate(R.layout.pref_add_channel, null))
                .setTitle(R.string.cb_menu_add_channel)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int where) {
                AlertDialog filterDlg = (AlertDialog) dialog;
                String name = ((EditText) filterDlg.findViewById(R.id.edit_channel_name))
                        .getText().toString();
                String cat = ((EditText) filterDlg.findViewById(R.id.edit_channel_number))
                        .getText().toString();
                boolean checked = ((CheckBox) filterDlg.findViewById(R.id.checkbox_channel_enable))
                        .isChecked();
                // Check input if input is valid
                String errorText = "";
                if (!isFilterNameValid(name)) {
                    errorText += getString(R.string.cb_error_channel_name);
                }
                if (!isCategoryValid(cat)) {
                    errorText += "\n" + getString(R.string.cb_error_channel_num);
                }
                if (errorText.equals("")) {
                    int channelId = Integer.valueOf(cat).intValue();
                    if (!checkCategoryIdExist(channelId)) {
                        dialog.dismiss();
                        CellBroadcastChannel channel
                                = new CellBroadcastChannel(channelId, name, checked);
                        if (insertCbFilterToProvider(channel)) {
                            mFilterArray.add(channel);
                            mChannelFilterMap.put(String.valueOf(channel.getChannelId()), channel);
                            updateFilterUIList();
                            if (channel.getChannelStatus()) {
                                setCellBroadcastConfig();
                            }
                        } else {
                            onErrorFinish(mCBSwitchPreference, ERROR_FINISH_DIALOG);
                        }
                    } else {
                        showToast(getString(R.string.cb_error_channel_id_exist));
                    }
                } else {
                    showToast(errorText);
                }
            }
        }).create();

        dialog.show();
        requestInputPanel(dialog);
    }

    private boolean queryFilters() {
        if (DEBUG) Log.d(LOG_TAG, "queryFilters start");

        if (mFilterArray != null) {
            mFilterArray.clear();
        }
        final String[] projection
                = new String[] { FILTER_ID, FILTER_NAME, FILTER_CATEGORY, FILTER_STAT };
        final int INDEX_ID = 0;
        final int INDEX_NAME = 1;
        final int INDEX_CATEGORY = 2;
        final int INDEX_STAT = 3;
        Cursor c = null;
        try {
            if (DEBUG) Log.i(LOG_TAG, "queryFilters() before query");
            c = this.getContentResolver().query(CB_CHANNEL_URI, projection, null, null,
                    FILTER_CATEGORY);
            if (c != null) {
                while (c.moveToNext()) {
                    CellBroadcastChannel channel
                            = new CellBroadcastChannel(c.getInt(INDEX_ID),
                            c.getInt(INDEX_CATEGORY), c.getString(INDEX_NAME),
                            (c.getInt(INDEX_STAT) == 1/*true*/));
                    mFilterArray.add(channel);
                }
            }
        } catch (IllegalArgumentException e) {
            if (DEBUG) Log.i(LOG_TAG, "IllegalArgumentException e: " + e);
            return false;
        } finally {
            if (DEBUG) Log.i(LOG_TAG, "queryFilters() finally");
            if (c != null) {
                c.close();
            }
        }
        return true;
    }

    private boolean insertCbFilterToProvider(CellBroadcastChannel channel) {
        if (DEBUG) Log.d(LOG_TAG, "to insert: [name: " + channel.getChannelName()
                + "; enable: " + channel.getChannelStatus()
                + "; category: " + channel.getChannelId());
        ContentValues cv = new ContentValues();
        cv.put(FILTER_NAME, channel.getChannelName());
        cv.put(FILTER_CATEGORY, channel.getChannelId());
        cv.put(FILTER_STAT, channel.getChannelStatus());
        try {
            Uri uri = getContentResolver().insert(CB_CHANNEL_URI, cv);
            if (DEBUG) Log.d(LOG_TAG, ", uri: " + cv + "]");
            int insertId = Integer.valueOf(uri.getLastPathSegment());
            channel.setKeyId(insertId);
            if (DEBUG) Log.d(LOG_TAG, "insertCbFilterToProvider(), insertId: " + insertId);
        } catch (IllegalArgumentException exception) {
            if (DEBUG) Log.e(LOG_TAG, "insertCbFilterToProvider (): " + exception);
            return false;
        }
        return true;
    }

    private boolean updateCbFilterToProvider(CellBroadcastChannel oldFilter,
            CellBroadcastChannel newFilter) {
        if (DEBUG) Log.d(LOG_TAG, "updateCbFilterToProvider start oldFilter =" + oldFilter);
        ContentValues cv = new ContentValues();
        cv.put(FILTER_ID, newFilter.getKeyId());
        cv.put(FILTER_NAME, newFilter.getChannelName());
        cv.put(FILTER_CATEGORY, newFilter.getChannelId());
        cv.put(FILTER_STAT, Integer.valueOf(newFilter.getChannelStatus() ? 1 : 0));
        String where = FILTER_ID + "=" + oldFilter.getKeyId();
        try {
            int lines = this.getContentResolver().update(CB_CHANNEL_URI, cv, where, null);
            if (oldFilter.getChannelStatus() ^ newFilter.getChannelStatus()) {
                setFilterState(newFilter);
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (DEBUG) Log.d(LOG_TAG, "updateCbFilterToProvider end newFilter =" + newFilter);
        return true;
    }

    private boolean deleteChannelFromProvider(CellBroadcastChannel oldFilter) {
        String where = FILTER_CATEGORY + "=" + oldFilter.getChannelId();
        try {
            getContentResolver().delete(CB_CHANNEL_URI, where, null);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return true;
    }

    private void setFilterState(CellBroadcastChannel newFilter) {
        //Set enable or disable filter
        if (newFilter.getChannelStatus()) {
            SmsManager.getDefault().enableCellBroadcast(newFilter.getChannelId(),
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        } else {
            SmsManager.getDefault().disableCellBroadcast(newFilter.getChannelId(),
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        }
    }

    private void setFilterState(int cat, boolean isEnabled) {
        //Set enable or disable filter
        if (isEnabled) {
            SmsManager.getDefault().enableCellBroadcast(cat,
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        } else {
            SmsManager.getDefault().disableCellBroadcast(cat,
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        }
    }

    private void updateCurrentFilter(ArrayList<SmsBroadcastConfigInfo> list) {
        if (list == null || list.size() == 0) {
            return;
        }

        for (SmsBroadcastConfigInfo config : list) {
            if (mCbCustomizedAsyncTask.isCancelled()) {
                break;
            }
            dumpCBConfigInfo(config);
            updateFiltersWithSingleConfig(config);
        }
    }

    private void updateFiltersWithSingleConfig(SmsBroadcastConfigInfo config) {
        int channelBeginIndex = config.getFromServiceId();
        int channelEndIndex = config.getToServiceId();
        boolean state = config.isSelected();
        if (DEBUG) Log.d(LOG_TAG, "updateFiltersWithSingleConfig STATE = " + state);

        if (channelBeginIndex != -1) {
            for (int i = channelBeginIndex; i <= channelEndIndex; i++) {
                if (mCbCustomizedAsyncTask.isCancelled()) {
                    break;
                }
                String cat = String.valueOf(i);
                CellBroadcastChannel channel = getFilterFromKey(cat);
                if (channel != null) {
                    channel.setChannelStatus(state);
                } else {
                    // Need to add a new filter.
                    String name = getString(R.string.cb_default_new_channel_name) + cat;
                    CellBroadcastChannel newFilter = new CellBroadcastChannel(i, name, state);
                    if (!insertCbFilterToProvider(newFilter)) {
                        onErrorFinish(mCBSwitchPreference, ERROR_FINISH_DIALOG);
                    }
                    mFilterArray.add(newFilter);
                    mChannelFilterMap.put(cat, newFilter);
                }
            }
        }
    }


    private void setCellBroadcastConfig() {
        if (DEBUG) Log.d(LOG_TAG, "setCellBroadcastConfig start");
        onStartConsume(mCBSwitchPreference, false);
        handlePollCellBroadcastConfigResponse();
    }

    private boolean isCategoryValid(String input) {
        if (input == null || input.length() == 0) {
            return false;
        }
        int catNum = Integer.valueOf(input).intValue();
        if (catNum >=  CB_CATEGORY_MAX || catNum < 0) {
            return false;
        }
        return true;
    }

    private boolean isFilterNameValid(String input) {
        if (input == null || input.length() == 0) {
            input = "";
        }
        if (input.length() > FILTER_NAME_LENGTH) {
            return false;
        }
        return true;
    }

    /* Check if newFilterId exist, which is not of keyId */
    private boolean checkCategoryIdExist(int newFilterId, int keyId) {
        for (CellBroadcastChannel tmpChannel : mFilterArray) {
            int tempCatId = tmpChannel.getChannelId();
            int tempKeyId = tmpChannel.getKeyId();
            if (tempCatId == newFilterId && tempKeyId != keyId) {
                return true;
            }
        }
        return false;
    }

    private boolean checkCategoryIdExist(int cat) {
        for (CellBroadcastChannel tmpChannel : mFilterArray) {
            if (tmpChannel.getChannelId() == cat) {
                return true;
            }
        }
        return false;
    }
    private CellBroadcastChannel getFilterFromKey(String key) {
        return mChannelFilterMap.get(key);
    }

    private void requestInputPanel(Dialog dialog) {
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void dumpCBConfigInfo(SmsBroadcastConfigInfo config) {
        if (DEBUG) Log.d(LOG_TAG, "dump " + config.toString()
                    + ": [ FromServiceId " + config.getFromServiceId()
                    + ", ToServiceId " + config.getToServiceId()
                    + ", FromCodeId " + config.getFromCodeScheme()
                    + ",  ToCodeId " + config.getToCodeScheme()
                    + "]");
    }
}
