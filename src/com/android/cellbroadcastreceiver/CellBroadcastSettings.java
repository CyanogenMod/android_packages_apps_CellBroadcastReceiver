/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.PhoneConstants;

import java.util.List;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastSettings extends PreferenceActivity {

    public static final String TAG = "CellBroadcastSettings";

    // Preference key for whether to enable emergency notifications (default enabled).
    public static final String KEY_ENABLE_EMERGENCY_ALERTS = "enable_emergency_alerts";

    // Duration of alert sound (in seconds).
    public static final String KEY_ALERT_SOUND_DURATION = "alert_sound_duration";

    // Default alert duration (in seconds).
    public static final String ALERT_SOUND_DEFAULT_DURATION = "4";

    // Enable vibration on alert (unless master volume is silent).
    public static final String KEY_ENABLE_ALERT_VIBRATE = "enable_alert_vibrate";

    // Speak contents of alert after playing the alert sound.
    public static final String KEY_ENABLE_ALERT_SPEECH = "enable_alert_speech";

    // Preference category for emergency alert and CMAS settings.
    public static final String KEY_CATEGORY_ALERT_SETTINGS = "category_alert_settings";

    // Preference category for ETWS related settings.
    public static final String KEY_CATEGORY_ETWS_SETTINGS = "category_etws_settings";

    // Whether to display CMAS extreme threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS =
            "enable_cmas_extreme_threat_alerts";

    // Whether to display CMAS severe threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS =
            "enable_cmas_severe_threat_alerts";

    // Whether to display CMAS amber alert messages (default is enabled).
    public static final String KEY_ENABLE_CMAS_AMBER_ALERTS = "enable_cmas_amber_alerts";

    // Preference category for development settings (enabled by settings developer options toggle).
    public static final String KEY_CATEGORY_DEV_SETTINGS = "category_dev_settings";

    // Whether to display ETWS test messages (default is disabled).
    public static final String KEY_ENABLE_ETWS_TEST_ALERTS = "enable_etws_test_alerts";

    // Whether to display CMAS monthly test messages (default is disabled).
    public static final String KEY_ENABLE_CMAS_TEST_ALERTS = "enable_cmas_test_alerts";

    // Preference category for Brazil specific settings.
    public static final String KEY_CATEGORY_BRAZIL_SETTINGS = "category_brazil_settings";

    // Preference key for whether to enable channel 50 notifications
    // Enabled by default for phones sold in Brazil, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS = "enable_channel_50_alerts";

    // Preference key for initial opt-in/opt-out dialog.
    public static final String KEY_SHOW_CMAS_OPT_OUT_DIALOG = "show_cmas_opt_out_dialog";

    // Alert reminder interval ("once" = single 2 minute reminder).
    public static final String KEY_ALERT_REMINDER_INTERVAL = "alert_reminder_interval";

    // Default reminder interval is off.
    public static final String ALERT_REMINDER_INTERVAL_DEFAULT_DURATION = "0";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        int phoneId = getIntent().getIntExtra(PhoneConstants.PHONE_KEY,
                SubscriptionManager.INVALID_PHONE_INDEX);

        if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX
                && TelephonyManager.getDefault().getPhoneCount() > 1) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            actionBar.setDisplayShowTitleEnabled(true);
            for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                String title = getTitleBySlotId(i);
                actionBar.addTab(actionBar.newTab().setText(title).setTabListener(
                        new SubTabListener(createSettingsFragment(i), title)));
            }
        } else {
            // Display the fragment as the main content.
            if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                phoneId = SubscriptionManager.getDefaultVoicePhoneId();
            } else {
                actionBar.setSubtitle(getTitleBySlotId(phoneId));
            }
            getFragmentManager().beginTransaction().replace(android.R.id.content,
                    createSettingsFragment(phoneId)).commit();
        }
    }

    private CellBroadcastSettingsFragment createSettingsFragment(int phoneId) {
        Bundle args = new Bundle();
        args.putInt(PhoneConstants.PHONE_KEY, phoneId);

        CellBroadcastSettingsFragment f = new CellBroadcastSettingsFragment();
        f.setArguments(args);
        return f;
    }

    private String getTitleBySlotId(int slotId) {
        SubscriptionInfo sir = SubscriptionManager.from(this)
                .getActiveSubscriptionInfoForSimSlotIndex(slotId);
        if (sir != null) {
            return sir.getDisplayName().toString();
        } else {
            return getResources().getString(R.string.sim_card_number_title, slotId+1);
        }
    }

    private class SubTabListener implements ActionBar.TabListener {
        private CellBroadcastSettingsFragment mFragment;
        private String tag;

        public SubTabListener(CellBroadcastSettingsFragment cbFragment, String tag) {
            this.mFragment = cbFragment;
            this.tag = tag;
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            ft.add(android.R.id.content, mFragment, tag);
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.remove(mFragment);
            }
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }

    /**
     * New fragment-style implementation of preferences.
     */
    public static class CellBroadcastSettingsFragment extends PreferenceFragment {
        private int mPhoneId;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            mPhoneId = getArguments().getInt(PhoneConstants.PHONE_KEY, 0);

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    boolean isExtreme =
                            pref.getKey().equals(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
                    if (isExtreme) {
                        boolean isExtremeAlertChecked =
                            ((Boolean) newValue).booleanValue();
                        CheckBoxPreference severeCheckBox = (CheckBoxPreference)
                            findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
                        if (severeCheckBox != null) {
                            severeCheckBox.setEnabled(isExtremeAlertChecked);
                            severeCheckBox.setChecked(false);
                        }
                    }

                    if (pref instanceof CheckBoxPreference) {
                        SharedPreferences.Editor editor = pref.getSharedPreferences().edit();
                        editor.putBoolean(pref.getKey() + mPhoneId, (Boolean) newValue);
                        editor.commit();
                    }
                    CellBroadcastReceiver.startConfigService(pref.getContext(), mPhoneId);

                    return true;
                }
            };

            //Listener for non-radio functionality
            Preference.OnPreferenceChangeListener startListener =
                    new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    SharedPreferences.Editor editor = pref.getSharedPreferences().edit();

                    if (pref instanceof CheckBoxPreference) {
                        editor.putBoolean(pref.getKey() + mPhoneId, (Boolean) newValue);
                    } else if (pref instanceof ListPreference) {
                        ListPreference lp = (ListPreference) pref;
                        final int idx = lp.findIndexOfValue((String) newValue);
                        editor.putString(pref.getKey() + mPhoneId, (String) newValue);
                        pref.setSummary(lp.getEntries()[idx]);
                    }
                    editor.commit();
                    return true;
                }
            };

            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getActivity());
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            // Emergency alert preference category (general and CMAS preferences).
            PreferenceCategory alertCategory =
                    (PreferenceCategory) findPreference(KEY_CATEGORY_ALERT_SETTINGS);
            initCheckBox(KEY_ENABLE_EMERGENCY_ALERTS, prefs, true, startConfigServiceListener);
            initList(KEY_ALERT_SOUND_DURATION, prefs, ALERT_SOUND_DEFAULT_DURATION, startListener);
            initList(KEY_ALERT_REMINDER_INTERVAL, prefs,
                    ALERT_REMINDER_INTERVAL_DEFAULT_DURATION, startListener);
            initCheckBox(KEY_ENABLE_CHANNEL_50_ALERTS, prefs, true, startConfigServiceListener);
            initCheckBox(KEY_ENABLE_ETWS_TEST_ALERTS, prefs, false, startConfigServiceListener);
            initCheckBox(KEY_ENABLE_CMAS_AMBER_ALERTS, prefs, true, startConfigServiceListener);
            initCheckBox(KEY_ENABLE_CMAS_TEST_ALERTS, prefs, false, startConfigServiceListener);
            initCheckBox(KEY_ENABLE_ALERT_SPEECH, prefs, true, startListener);
            initCheckBox(KEY_ENABLE_ALERT_VIBRATE, prefs, true, startListener);

            final CheckBoxPreference enableCmasExtremeAlerts = initCheckBox(
                    KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS,
                    prefs, true, startConfigServiceListener);
            final CheckBoxPreference enableCmasSevereAlerts = initCheckBox(
                    KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, prefs, true, startConfigServiceListener);

            // Show extra settings when developer options is enabled in settings.
            boolean enableDevSettings = Settings.Global.getInt(getActivity().getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

            Resources res = getResources();
            boolean showEtwsSettings = res.getBoolean(R.bool.show_etws_settings);

            // Show alert settings and ETWS categories for ETWS builds and developer mode.
            if (!enableDevSettings && !showEtwsSettings) {
                // Remove general emergency alert preference items (not shown for CMAS builds).
                alertCategory.removePreference(findPreference(KEY_ENABLE_EMERGENCY_ALERTS));
                alertCategory.removePreference(findPreference(KEY_ALERT_SOUND_DURATION));
                alertCategory.removePreference(findPreference(KEY_ENABLE_ALERT_SPEECH));
                // Remove ETWS preference category.
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ETWS_SETTINGS));
            }

            if (!res.getBoolean(R.bool.show_cmas_settings)) {
                // Remove CMAS preference items in emergency alert category.
                alertCategory.removePreference(
                        findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS));
                alertCategory.removePreference(
                        findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS));
                alertCategory.removePreference(findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS));
            }

            TelephonyManager tm = (TelephonyManager) getActivity().getSystemService(
                    Context.TELEPHONY_SERVICE);

            boolean enableChannel50Support = res.getBoolean(R.bool.show_brazil_settings) ||
                    "br".equals(tm.getSimCountryIso());

            if (!enableChannel50Support) {
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_BRAZIL_SETTINGS));
            }
            if (!enableDevSettings) {
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_DEV_SETTINGS));
            }
            if (enableCmasSevereAlerts != null && enableCmasExtremeAlerts != null) {
                boolean isExtremeAlertChecked = enableCmasExtremeAlerts.isChecked();
                enableCmasSevereAlerts.setEnabled(isExtremeAlertChecked);
            }
        }

        private CheckBoxPreference initCheckBox(String key, SharedPreferences prefs,
                boolean defaultValue, Preference.OnPreferenceChangeListener listener) {
            final CheckBoxPreference pref = (CheckBoxPreference) findPreference(key);
            if (pref != null) {
                pref.setChecked(prefs.getBoolean(key + mPhoneId, defaultValue));
                pref.setOnPreferenceChangeListener(listener);
            }
            return pref;
        }

        private ListPreference initList(String key, SharedPreferences prefs,
                String defaultValue, Preference.OnPreferenceChangeListener listener) {
            final ListPreference pref = (ListPreference) findPreference(key);
            if (pref != null) {
                String value = prefs.getString(key + mPhoneId, defaultValue);
                final int idx = pref.findIndexOfValue(value);
                pref.setSummary(pref.getEntries()[idx]);
                pref.setValue(value);
            }
            return pref;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }
}
