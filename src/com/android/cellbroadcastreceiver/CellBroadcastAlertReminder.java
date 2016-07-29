/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.media.AudioManager;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;
import com.android.internal.telephony.PhoneConstants;

/**
 * Manages alert reminder notification.
 */
public class CellBroadcastAlertReminder extends Service {
    private static final String TAG = "CellBroadcastAlertReminder";

    /** Action to wake up and play alert reminder sound. */
    static final String ACTION_PLAY_ALERT_REMINDER = "ACTION_PLAY_ALERT_REMINDER";

    /**
     * Pending intent for alert reminder. This is static so that we don't have to start the
     * service in order to cancel any pending reminders when user dismisses the alert dialog.
     */
    private static PendingIntent sPlayReminderIntent;

    /**
     * Alert reminder for current ringtone being played.
     */
    private static Ringtone sPlayReminderRingtone;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // No intent or unrecognized action; tell the system not to restart us.
        if (intent == null || !ACTION_PLAY_ALERT_REMINDER.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int phoneId = intent.getIntExtra(PhoneConstants.SLOT_KEY,
                SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultSmsSubscriptionId()));
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        AudioManager audioManager = (AudioManager)this.getSystemService(
                Context.AUDIO_SERVICE);
        if (getResources().getBoolean(
                R.bool.config_regional_wea_alert_reminder_interval)) {
            CellBroadcastMessage message = intent.getParcelableExtra("CellBroadcastMessage");
            playAlertReminderAudio(message, prefs, phoneId);
            if (queueAlertReminderAudio(this, false, message)) {
                return START_STICKY;
            } else {
                log("no reminders queued");
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        log("playing alert reminder");
        playAlertReminderSound();

        if (queueAlertReminder(this, false)) {
            return START_STICKY;
        } else {
            log("no reminders queued");
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void playAlertReminderAudio(CellBroadcastMessage message,
            final SharedPreferences prefs, int phoneId) {
        Intent audioIntent = new Intent(this, CellBroadcastAlertAudio.class);
        audioIntent.setAction(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO);
        int duration;   // alert audio duration in ms
        if (message.isCmasMessage()) {
            // CMAS requirement: duration of the audio attention signal is 10.5 seconds.
            duration = 10500;
        } else {
            duration = Integer.parseInt(prefs.getString(
                        CellBroadcastSettings.KEY_ALERT_SOUND_DURATION,
                        CellBroadcastSettings.ALERT_SOUND_DEFAULT_DURATION)) * 1000;
        }
        audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_DURATION_EXTRA, duration);

        if (!getResources().getBoolean(
                R.bool.config_regional_presidential_wea_with_tone_vibrate)
                && message.isEtwsMessage()) {
            // For ETWS, always vibrate, even in silent mode.
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA, true);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_ETWS_VIBRATE_EXTRA, true);
        } else if ((getResources().getBoolean(
                R.bool.config_regional_presidential_wea_with_tone_vibrate))
                && (message.isCmasMessage())
                && (message.getCmasMessageClass()
                == SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT)){
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA, true);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_TONE_EXTRA, true);
            audioIntent.putExtra(
                    CellBroadcastAlertAudio.ALERT_AUDIO_PRESIDENT_TONE_VIBRATE_EXTRA, true);
        } else {
            // For other alerts, vibration can be disabled in app settings.
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA,
                    prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_VIBRATE, true));
        }

        if (getResources().getBoolean(
                R.bool.config_regional_wea_alert_tone_enable)) {
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_TONE_EXTRA,
                    prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_TONE, true));
        }

        audioIntent.putExtra("isFirstTime", false);

        String messageBody = message.getMessageBody();

        if (prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_SPEECH, true)) {
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY, messageBody);

            String language = message.getLanguageCode();
            if (message.isEtwsMessage() && !"ja".equals(language)) {
                Log.w(TAG, "bad language code for ETWS - using Japanese TTS");
                language = "ja";
            } else if (message.isCmasMessage() && !"en".equals(language)) {
                Log.w(TAG, "bad language code for CMAS - using English TTS");
                language = "en";
            }
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_PREFERRED_LANGUAGE,
                    language);
        }
        startService(audioIntent);
    }

    /**
     * Use the RingtoneManager to play the alert reminder sound.
     */
    private void playAlertReminderSound() {
        Uri notificationUri = RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_NOTIFICATION);
        if (notificationUri == null) {
            loge("Can't get URI for alert reminder sound");
            return;
        }
        Ringtone r = RingtoneManager.getRingtone(this, notificationUri);
        r.setStreamType(AudioManager.STREAM_NOTIFICATION);

        if (r != null) {
            log("playing alert reminder sound");
            r.play();
        } else {
            loge("can't get Ringtone for alert reminder sound");
        }
    }

    /**
     * Helper method to start the alert reminder service to queue the alert reminder.
     * @return true if a pending reminder was set; false if there are no more reminders
     */
    static boolean queueAlertReminder(Context context, boolean firstTime) {
        // Stop any alert reminder sound and cancel any previously queued reminders.
        cancelAlertReminder();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String prefStr = prefs.getString(CellBroadcastSettings.KEY_ALERT_REMINDER_INTERVAL, null);

        if (prefStr == null) {
            if (DBG) log("no preference value for alert reminder");
            return false;
        }

        int interval;
        try {
            interval = Integer.valueOf(prefStr);
        } catch (NumberFormatException ignored) {
            loge("invalid alert reminder interval preference: " + prefStr);
            return false;
        }

        if (interval == 0 || (interval == 1 && !firstTime)) {
            return false;
        }
        if (interval == 1) {
            interval = 2;   // "1" = one reminder after 2 minutes
        }

        if (DBG) log("queueAlertReminder() in " + interval + " minutes");

        Intent playIntent = new Intent(context, CellBroadcastAlertReminder.class);
        playIntent.setAction(ACTION_PLAY_ALERT_REMINDER);
        sPlayReminderIntent = PendingIntent.getService(context, 0, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            loge("can't get Alarm Service");
            return false;
        }

        // remind user after 2 minutes or 15 minutes
        long triggerTime = SystemClock.elapsedRealtime() + (interval * 60000);
        // We use setExact instead of set because this is for emergency reminder.
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                triggerTime, sPlayReminderIntent);
        return true;
    }

    static boolean queueAlertReminderAudio(final Context context, boolean firstTime,
            CellBroadcastMessage message) {
        int phoneId = SubscriptionManager.getPhoneId(message.getSubId());
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (context.getResources().getBoolean(
                R.bool.config_regional_wea_alert_reminder_interval)) {
            // Stop any alert reminder sound and cancel any previously queued reminders.
            cancelAlertReminder();
            SharedPreferences.Editor editor = prefs.edit();
            Bundle bundle = new Bundle();
            bundle.putParcelable("CellBroadcastMessage", message);
            // if it the first time.
            String prefStr = prefs.getString("reminder_times", null);
            int interval;
            int counter;
            if (firstTime) {
                interval = 1;
                editor.putString("reminder_times", "2");
            } else {
                prefStr = prefs.getString("reminder_times", null);
                try {
                    counter = Integer.valueOf(prefStr);
                    interval = 2;
                } catch (NumberFormatException ignored) {
                    loge("invalid alert reminder times: " + prefStr);
                    return false;
                }
                if (counter > 3) {
                    return false;
                } else {
                    editor.putString("reminder_times", counter + 1 + "");
                }
            }
            editor.commit();
            if (DBG) log("queueAlertReminder() in " + interval + " minutes");

            Intent playIntent = new Intent(context, CellBroadcastAlertReminder.class);
            playIntent.putExtras(bundle);
            playIntent.setAction(ACTION_PLAY_ALERT_REMINDER);
            playIntent.putExtra(PhoneConstants.SLOT_KEY, phoneId);
            sPlayReminderIntent = PendingIntent.getService(context, 0, playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(
                    Context.ALARM_SERVICE);
            if (alarmManager == null) {
                loge("can't get Alarm Service");
                return false;
            }

            // remind user after 2 minutes or 15 minutes
            long triggerTime = SystemClock.elapsedRealtime() + (interval * 60000);
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime,
                    sPlayReminderIntent);
            return true;
        } else {
            return queueAlertReminder(context, firstTime);
        }
    }

    /**
     * Stops alert reminder and cancels any queued reminders.
     */
    static void cancelAlertReminder() {
        if (DBG) log("cancelAlertReminder()");
        if (sPlayReminderRingtone != null) {
            if (DBG) log("stopping play reminder ringtone");
            sPlayReminderRingtone.stop();
            sPlayReminderRingtone = null;
        }
        if (sPlayReminderIntent != null) {
            if (DBG) log("canceling pending play reminder intent");
            sPlayReminderIntent.cancel();
            sPlayReminderIntent = null;
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
