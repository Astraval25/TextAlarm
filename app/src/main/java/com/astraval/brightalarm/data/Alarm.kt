package com.astraval.brightalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val message: String,
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
}