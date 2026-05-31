package com.astraval.brightalarm.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.databinding.ItemAlarmBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmAdapter(
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onClick: (Alarm) -> Unit,
    private val onLongClick: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.VH>(DIFF) {

    inner class VH(val b: ItemAlarmBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = getItem(position)
        holder.b.timeText.text = formatTime(a)
        holder.b.labelText.text = a.taskTitle.ifBlank { a.message.ifBlank { a.label.ifBlank { "Alarm" } } }
        holder.b.daysText.text = formatSchedule(a)
        holder.b.enabledSwitch.setOnCheckedChangeListener(null)
        holder.b.enabledSwitch.isChecked = a.isEnabled
        holder.b.enabledSwitch.setOnCheckedChangeListener { _, checked -> onToggle(a, checked) }
        holder.b.cardContainer.alpha = if (a.isEnabled) 1f else 0.68f
        holder.b.root.setOnClickListener { onClick(a) }
        holder.b.root.setOnLongClickListener { onLongClick(a); true }
    }

    private fun formatSchedule(alarm: Alarm): String {
        alarm.triggerAtMillis?.let {
            return formatTaskRepeat(alarm)
        }
        return formatRepeat(alarm.repeatDays)
    }

    private fun formatTaskRepeat(alarm: Alarm): String {
        return when (alarm.taskRepeatMode) {
            Alarm.REPEAT_WEEKLY -> {
                val days = formatRepeat(alarm.taskRepeatDays)
                if (days == "Once") "Weekly" else "Weekly: $days"
            }
            Alarm.REPEAT_MONTHLY -> "Monthly"
            Alarm.REPEAT_YEARLY -> "Yearly"
            Alarm.REPEAT_CUSTOM -> {
                val interval = alarm.taskRepeatInterval.coerceAtLeast(1)
                val unit = when (alarm.taskRepeatUnit) {
                    Alarm.UNIT_DAY -> if (interval == 1) "day" else "days"
                    Alarm.UNIT_MONTH -> if (interval == 1) "month" else "months"
                    Alarm.UNIT_YEAR -> if (interval == 1) "year" else "years"
                    else -> if (interval == 1) "week" else "weeks"
                }
                val suffix = if (alarm.taskRepeatUnit == Alarm.UNIT_WEEK && alarm.taskRepeatDays.isNotEmpty()) {
                    " (${formatRepeat(alarm.taskRepeatDays)})"
                } else {
                    ""
                }
                "Every $interval $unit$suffix"
            }
            else -> "One-time task"
        }
    }

    private fun formatTime(alarm: Alarm): String {
        alarm.triggerAtMillis?.let {
            return SimpleDateFormat("EEE, MMM d  hh:mm a", Locale.getDefault()).format(Date(it))
        }
        return alarm.timeFormatted
    }

    private fun formatRepeat(days: List<Int>): String {
        if (days.isEmpty()) return "Once"
        if (days.toSet() == setOf(1, 2, 3, 4, 5)) return "Weekdays"
        if (days.toSet() == setOf(6, 7)) return "Weekends"
        if (days.toSet() == setOf(1, 2, 3, 4, 5, 6, 7)) return "Daily"
        val names = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return days.sorted().joinToString(" ") { names[it - 1] }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Alarm>() {
            override fun areItemsTheSame(a: Alarm, b: Alarm) = a.id == b.id
            override fun areContentsTheSame(a: Alarm, b: Alarm) = a == b
        }
    }
}
