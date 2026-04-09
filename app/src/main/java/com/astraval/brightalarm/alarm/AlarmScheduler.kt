package com.astraval.brightalarm.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.ui.alarm.AlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("MissingPermission")
    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) return
        val triggerAt = nextTriggerMillis(alarm)
        val pi = broadcastPendingIntent(alarm.id)
        val showPi = PendingIntent.getActivity(
            context, alarm.id,
            Intent(context, AlarmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Use setAlarmClock: survives Doze, shows in status bar, highest priority.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fall back to inexact; user must grant SCHEDULE_EXACT_ALARM for reliability.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
    }

    fun cancel(alarmId: Int) {
        alarmManager.cancel(broadcastPendingIntent(alarmId))
    }

    private fun broadcastPendingIntent(alarmId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context, alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        fun nextTriggerMillis(alarm: Alarm, from: Long = System.currentTimeMillis()): Long {
            val base = Calendar.getInstance().apply {
                timeInMillis = from
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (alarm.repeatDays.isEmpty()) {
                if (base.timeInMillis <= from) base.add(Calendar.DAY_OF_YEAR, 1)
                return base.timeInMillis
            }
            for (i in 0..7) {
                val c = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                val dow = c.get(Calendar.DAY_OF_WEEK)
                val iso = if (dow == Calendar.SUNDAY) 7 else dow - 1
                if (iso in alarm.repeatDays && c.timeInMillis > from) return c.timeInMillis
            }
            return base.timeInMillis
        }
    }
}