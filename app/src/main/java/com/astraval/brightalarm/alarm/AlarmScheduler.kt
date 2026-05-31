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
        private const val MILLIS_PER_WEEK = 7L * 24L * 60L * 60L * 1000L

        fun nextTriggerMillis(alarm: Alarm, from: Long = System.currentTimeMillis()): Long {
            alarm.triggerAtMillis?.let { return nextTaskTriggerMillis(alarm, from) }
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

        private fun nextTaskTriggerMillis(alarm: Alarm, from: Long): Long {
            val first = alarm.triggerAtMillis ?: return from
            if (!alarm.isRepeatingTask || first > from) return first
            val seed = Calendar.getInstance().apply { timeInMillis = first }

            return when (alarm.taskRepeatMode) {
                Alarm.REPEAT_WEEKLY -> nextWeeklyTaskTrigger(seed, from, 1, alarm.taskRepeatDays)
                Alarm.REPEAT_MONTHLY -> nextCalendarTaskTrigger(seed, from, Calendar.MONTH, 1)
                Alarm.REPEAT_YEARLY -> nextCalendarTaskTrigger(seed, from, Calendar.YEAR, 1)
                Alarm.REPEAT_CUSTOM -> when (alarm.taskRepeatUnit) {
                    Alarm.UNIT_DAY -> nextCalendarTaskTrigger(seed, from, Calendar.DAY_OF_YEAR, alarm.taskRepeatInterval)
                    Alarm.UNIT_MONTH -> nextCalendarTaskTrigger(seed, from, Calendar.MONTH, alarm.taskRepeatInterval)
                    Alarm.UNIT_YEAR -> nextCalendarTaskTrigger(seed, from, Calendar.YEAR, alarm.taskRepeatInterval)
                    else -> nextWeeklyTaskTrigger(seed, from, alarm.taskRepeatInterval, alarm.taskRepeatDays)
                }
                else -> first
            }
        }

        private fun nextCalendarTaskTrigger(
            seed: Calendar,
            from: Long,
            field: Int,
            interval: Int
        ): Long {
            val safeInterval = interval.coerceAtLeast(1)
            val next = seed.clone() as Calendar
            while (next.timeInMillis <= from) {
                next.add(field, safeInterval)
            }
            return next.timeInMillis
        }

        private fun nextWeeklyTaskTrigger(
            seed: Calendar,
            from: Long,
            interval: Int,
            selectedDays: List<Int>
        ): Long {
            val safeInterval = interval.coerceAtLeast(1)
            val days = selectedDays.ifEmpty { listOf(isoDay(seed)) }.toSet()
            val seedWeekStart = isoWeekStartMillis(seed)
            val horizonDays = safeInterval * 7 + 14
            for (offset in 0..horizonDays) {
                val candidate = Calendar.getInstance().apply {
                    timeInMillis = from
                    add(Calendar.DAY_OF_YEAR, offset)
                    set(Calendar.HOUR_OF_DAY, seed.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, seed.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (candidate.timeInMillis <= from || isoDay(candidate) !in days) continue
                val weeksSinceSeed =
                    ((isoWeekStartMillis(candidate) - seedWeekStart) / MILLIS_PER_WEEK).toInt()
                if (weeksSinceSeed >= 0 && weeksSinceSeed % safeInterval == 0) {
                    return candidate.timeInMillis
                }
            }
            return nextCalendarTaskTrigger(seed, from, Calendar.WEEK_OF_YEAR, safeInterval)
        }

        private fun isoDay(calendar: Calendar): Int {
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            return if (day == Calendar.SUNDAY) 7 else day - 1
        }

        private fun isoWeekStartMillis(calendar: Calendar): Long {
            return (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -(isoDay(this) - 1))
            }.timeInMillis
        }
    }
}
