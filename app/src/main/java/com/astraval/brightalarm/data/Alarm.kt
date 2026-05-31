package com.astraval.brightalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val taskTitle: String = "",
    val taskNotes: String = "",
    val message: String,
    val triggerAtMillis: Long? = null,
    val taskRepeatMode: String = REPEAT_NONE,
    val taskRepeatInterval: Int = 1,
    val taskRepeatUnit: String = UNIT_WEEK,
    val taskRepeatDays: List<Int> = emptyList(), // 1=Mon..7=Sun (ISO)
    val isEnabled: Boolean = true,
    val isVibrate: Boolean = true,
    val useTts: Boolean = true,
    val ringtoneUri: String? = null,
    val snoozeMinutes: Int = 5,
    val repeatDays: List<Int> = emptyList(), // 1=Mon..7=Sun (ISO)
    val label: String = ""
) {
    val timeFormatted: String
        get() {
            val h = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            return String.format("%02d:%02d %s", h, minute, amPm)
        }

    val isTask: Boolean
        get() = triggerAtMillis != null

    val isRepeatingTask: Boolean
        get() = isTask && taskRepeatMode != REPEAT_NONE

    companion object {
        const val REPEAT_NONE = "NONE"
        const val REPEAT_WEEKLY = "WEEKLY"
        const val REPEAT_MONTHLY = "MONTHLY"
        const val REPEAT_YEARLY = "YEARLY"
        const val REPEAT_CUSTOM = "CUSTOM"

        const val UNIT_DAY = "DAY"
        const val UNIT_WEEK = "WEEK"
        const val UNIT_MONTH = "MONTH"
        const val UNIT_YEAR = "YEAR"
    }
}
