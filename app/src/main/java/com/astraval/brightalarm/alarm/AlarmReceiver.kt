package com.astraval.brightalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.astraval.brightalarm.data.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_FIRED) return
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId == -1) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = repository.getById(alarmId) ?: return@launch
                // Start foreground alarm service
                val svcIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
                // If repeating, schedule next occurrence
                if (alarm.repeatDays.isNotEmpty() && alarm.isEnabled) {
                    scheduler.schedule(alarm)
                } else if (alarm.repeatDays.isEmpty()) {
                    // one-shot: mark disabled
                    repository.setEnabled(alarm, false)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM_FIRED = "com.astraval.brightalarm.ALARM_FIRED"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}