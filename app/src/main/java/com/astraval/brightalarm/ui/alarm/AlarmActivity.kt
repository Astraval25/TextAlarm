package com.astraval.brightalarm.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.astraval.brightalarm.alarm.AlarmReceiver
import com.astraval.brightalarm.alarm.AlarmScheduler
import com.astraval.brightalarm.alarm.AlarmService
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.data.AlarmRepository
import com.astraval.brightalarm.databinding.ActivityAlarmBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler

    private lateinit var binding: ActivityAlarmBinding
    private var alarmId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOnLockScreen()
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeZoneInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val msg = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        binding.alarmMessage.text = msg.ifBlank { "Alarm" }

        binding.dismissButton.setOnClickListener {
            stopService()
            finish()
        }
        binding.snoozeButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val alarm = repository.getById(alarmId)
                if (alarm != null) snoozeAlarm(alarm)
                stopService()
                finish()
            }
        }
    }

    private fun snoozeAlarm(alarm: Alarm) {
        // Schedule a one-shot wake-up after snoozeMinutes using direct AlarmManager via scheduler logic.
        val fireAt = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this, alarm.id, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(fireAt, pi), pi)
        }
    }

    private fun stopService() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun applySafeZoneInsets() {
        val rootStart = binding.root.paddingLeft
        val rootTop = binding.root.paddingTop
        val rootEnd = binding.root.paddingRight
        val rootBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = rootStart + bars.left,
                top = rootTop + bars.top,
                right = rootEnd + bars.right,
                bottom = rootBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_MESSAGE = "message"
    }
}
