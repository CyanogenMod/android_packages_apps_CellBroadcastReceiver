/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
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
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimSmsManager;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import com.android.internal.telephony.MSimConstants;

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

    public static String subTag = "SUB";
    public int mSubscription = MSimConstants.DEFAULT_SUBSCRIPTION;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            mSubscription = MSimConstants.SUB1;
            if (actionBar != null) {
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
                actionBar.setDisplayShowTitleEnabled(true);
                for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                    mActionBar.addTab(actionBar.newTab().setText(subTag+(i+1)).setTabListener(
                            new MySubTabListener(new CellBroadcastSettingsFragment(),
                            subTag+(i+1), i)));
                }
            }
        } else {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction().replace(android.R.id.content,
                    new CellBroadcastSettingsFragment()).commit();
        }
    }

    private class MySubTabListener implements ActionBar.TabListener {

        private CellBroadcastSettingsFragment mFragment;
        private String tag;
        private int subScription;

        public MySubTabListener(CellBroadcastSettingsFragment cbFragment, String tag,
                int subScription) {
            this.mFragment = cbFragment;
            this.tag = tag;
            this.subScription = subScription;
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            ft.add(android.R.id.content, mFragment, tag);
            mSubscription = subScription;
            Log.d(TAG, "onTabSelected  mSubscription:" + mSubscription);
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
    public class CellBroadcastSettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.d(TAG, "onCreate CellBroadcastSettingsFragment  mSubscription :" + mSubscription);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getActivity());
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            // Emergency alert preference category (general and CMAS preferences).
            PreferenceCategory alertCategory =
                    (PreferenceCategory) findPreference(KEY_CATEGORY_ALERT_SETTINGS);
            final CheckBoxPreference enablePwsAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_EMERGENCY_ALERTS);
            final ListPreference duration =
                    (ListPreference) findPreference(KEY_ALERT_SOUND_DURATION);
            final ListPreference interval =
                    (ListPreference) findPreference(KEY_ALERT_REMINDER_INTERVAL);
            final CheckBoxPreference enableChannel50Alerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_CHANNEL_50_ALERTS);
            final CheckBoxPreference enableEtwsAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_ETWS_TEST_ALERTS);
            final CheckBoxPreference enableCmasExtremeAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
            final CheckBoxPreference enableCmasSevereAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
            final CheckBoxPreference enableCmasAmberAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS);
            final CheckBoxPreference enableCmasTestAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_CMAS_TEST_ALERTS);
            final CheckBoxPreference enableSpeakerAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_ALERT_SPEECH);
            final CheckBoxPreference enableVibrateAlerts =
                    (CheckBoxPreference) findPreference(KEY_ENABLE_ALERT_VIBRATE);

            final int idx = interval.findIndexOfValue(
                    (String)prefs.getString(KEY_ALERT_REMINDER_INTERVAL + mSubscription,
                    ALERT_REMINDER_INTERVAL_DEFAULT_DURATION));
            interval.setSummary(interval.getEntries()[idx]);
            interval.setValue(prefs.getString(KEY_ALERT_REMINDER_INTERVAL
                    + mSubscription, ALERT_REMINDER_INTERVAL_DEFAULT_DURATION));

            final int index = duration.findIndexOfValue(
                    (String)prefs.getString(KEY_ALERT_SOUND_DURATION + mSubscription,
                    ALERT_SOUND_DEFAULT_DURATION));
            duration.setSummary(duration.getEntries()[index]);
            duration.setValue(prefs.getString(KEY_ALERT_SOUND_DURATION
                    + mSubscription, ALERT_SOUND_DEFAULT_DURATION));

            enablePwsAlerts.setChecked(prefs.getBoolean( KEY_ENABLE_EMERGENCY_ALERTS
                    + mSubscription, true));
            enableChannel50Alerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_CHANNEL_50_ALERTS + mSubscription, true));
            enableEtwsAlerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_ETWS_TEST_ALERTS + mSubscription, false));
            enableCmasExtremeAlerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS + mSubscription, true));
            enableCmasSevereAlerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS + mSubscription, true));
            enableCmasAmberAlerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_CMAS_AMBER_ALERTS + mSubscription, true));
            enableCmasTestAlerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_CMAS_TEST_ALERTS + mSubscription, false));
            enableSpeakerAlerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_ALERT_SPEECH + mSubscription, true));
            enableVibrateAlerts.setChecked(prefs.getBoolean(
                    KEY_ENABLE_ALERT_VIBRATE + mSubscription, true));

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    String value = String.valueOf(newValue);
                    SharedPreferences.Editor editor = prefs.edit();

                    if (pref == enablePwsAlerts) {
                        editor.putBoolean(KEY_ENABLE_EMERGENCY_ALERTS
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == enableChannel50Alerts) {
                        editor.putBoolean(KEY_ENABLE_CHANNEL_50_ALERTS
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == enableEtwsAlerts) {
                        editor.putBoolean(KEY_ENABLE_ETWS_TEST_ALERTS
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == enableCmasExtremeAlerts) {
                        editor.putBoolean(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == enableCmasSevereAlerts) {
                        editor.putBoolean(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == enableCmasAmberAlerts) {
                        editor.putBoolean(KEY_ENABLE_CMAS_AMBER_ALERTS
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == enableCmasTestAlerts) {
                        editor.putBoolean(KEY_ENABLE_CMAS_TEST_ALERTS
                                + mSubscription, Boolean.valueOf((value)));
                    }
                    editor.commit();
                    CellBroadcastReceiver.startConfigService(pref.getContext(), mSubscription);

                    return true;
                }
            };

            //Listener for non-radio functionality
            Preference.OnPreferenceChangeListener startListener =
                    new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    String value = String.valueOf(newValue);
                    SharedPreferences.Editor editor = prefs.edit();

                    if (pref == enableSpeakerAlerts) {
                        editor.putBoolean(KEY_ENABLE_ALERT_SPEECH
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == enableVibrateAlerts) {
                        editor.putBoolean(KEY_ENABLE_ALERT_VIBRATE
                                + mSubscription, Boolean.valueOf((value)));
                    } else if (pref == interval) {
                        final int idx = interval.findIndexOfValue((String) newValue);

                        editor.putString(KEY_ALERT_REMINDER_INTERVAL  + mSubscription,
                                String.valueOf(newValue));
                        interval.setSummary(interval.getEntries()[idx]);
                    }
                    editor.commit();
                    return true;
                }
            };

            // Show extra settings when developer options is enabled in settings.
            boolean enableDevSettings = Settings.Global.getInt(getActivity().getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

            Resources res = getResources();
            boolean showEtwsSettings = res.getBoolean(R.bool.show_etws_settings);

            // Show alert settings and ETWS categories for ETWS builds and developer mode.
            if (enableDevSettings || showEtwsSettings) {
                // enable/disable all alerts
                if (enablePwsAlerts != null) {
                    enablePwsAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
                }

                // alert sound duration
                duration.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference pref, Object newValue) {
                        final int idx = duration.findIndexOfValue((String) newValue);
                        duration.setSummary(duration.getEntries()[idx]);
                        prefs.edit().putString(KEY_ALERT_SOUND_DURATION  + mSubscription,
                            String.valueOf(newValue)).commit();
                        return true;
                    }
                });
            } else {
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

            if (enableChannel50Alerts != null) {
                enableChannel50Alerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (enableEtwsAlerts != null) {
                enableEtwsAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (enableCmasExtremeAlerts != null) {
                enableCmasExtremeAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (enableCmasSevereAlerts != null) {
                enableCmasSevereAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (enableCmasAmberAlerts != null) {
                enableCmasAmberAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (enableCmasTestAlerts != null) {
                enableCmasTestAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            //Setting the listerner for non-radio functionality
            if (enableSpeakerAlerts != null) {
                enableSpeakerAlerts.setOnPreferenceChangeListener(startListener);
            }
            if (enableVibrateAlerts != null) {
                enableVibrateAlerts.setOnPreferenceChangeListener(startListener);
            }
            if (interval != null) {
                interval.setOnPreferenceChangeListener(startListener);
            }
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
