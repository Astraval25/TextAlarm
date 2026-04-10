package com.astraval.brightalarm.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.astraval.brightalarm.BrightAlarmApp
import com.astraval.brightalarm.R
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.data.AlarmRepository
import com.astraval.brightalarm.ui.alarm.AlarmActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var repository: AlarmRepository

    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentAlarmId: Int = -1
    private var currentMessage: String = ""
    private var isAlarmActive: Boolean = false
    private var repeatSpeechJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        if (alarmId == -1) {
            stopSelf()
            return START_NOT_STICKY
        }
        currentAlarmId = alarmId
        isAlarmActive = true
        acquireWakeLock()
        scope.launch(Dispatchers.IO) {
            val alarm = repository.getById(alarmId) ?: return@launch
            currentMessage = alarm.message.ifBlank { alarm.label.ifBlank { "Alarm" } }
            startForeground(NOTIF_ID, buildNotification(alarm))
            launchFullScreen(alarm)
            startAudio()
            startVibration(alarm)
        }
        return START_STICKY
    }

    private fun launchFullScreen(alarm: Alarm) {
        val i = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmActivity.EXTRA_MESSAGE, currentMessage)
        }
        startActivity(i)
    }

    private fun buildNotification(alarm: Alarm): android.app.Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmActivity.EXTRA_MESSAGE, currentMessage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, alarm.id, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPi = PendingIntent.getService(
            this, alarm.id,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BrightAlarmApp.ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Alarm")
            .setContentText(currentMessage)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(0, "Dismiss", dismissPi)
            .build()
    }

    private fun startAudio() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        repeatSpeechJob?.cancel()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS || !isAlarmActive) return@TextToSpeech

            val engine = tts ?: return@TextToSpeech
            engine.setAudioAttributes(attrs)
            if (engine.setLanguage(Locale.getDefault()) < TextToSpeech.LANG_AVAILABLE) {
                engine.language = Locale.US
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    queueNextSpeech()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    queueNextSpeech()
                }
            })
            speakCurrentMessage()
        }
    }

    private fun queueNextSpeech() {
        repeatSpeechJob?.cancel()
        if (!isAlarmActive) return
        repeatSpeechJob = scope.launch {
            delay(SPEECH_REPEAT_DELAY_MS)
            speakCurrentMessage()
        }
    }

    private fun speakCurrentMessage() {
        if (!isAlarmActive) return
        val engine = tts ?: return
        val utteranceId = "alarm_${currentAlarmId}_${System.nanoTime()}"
        engine.speak(currentMessage, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun startVibration(alarm: Alarm) {
        if (!alarm.isVibrate) return
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BrightAlarm:AlarmServiceWakeLock"
        ).apply { acquire(10 * 60 * 1000L) }
    }

    private fun stopAlarm() {
        isAlarmActive = false
        releaseAlarmResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isAlarmActive = false
        releaseAlarmResources()
        super.onDestroy()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun releaseAlarmResources() {
        repeatSpeechJob?.cancel()
        repeatSpeechJob = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        runCatching { vib.cancel() }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val ACTION_STOP = "com.astraval.brightalarm.ACTION_STOP_ALARM"
        private const val NOTIF_ID = 4242
        private const val SPEECH_REPEAT_DELAY_MS = 750L
    }
}
