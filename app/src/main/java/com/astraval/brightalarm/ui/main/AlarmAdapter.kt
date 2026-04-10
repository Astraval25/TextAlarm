package com.astraval.brightalarm.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.databinding.ItemAlarmBinding

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
        holder.b.timeText.text = a.timeFormatted
        holder.b.labelText.text = a.message.ifBlank { a.label.ifBlank { "Alarm" } }
        holder.b.daysText.text = formatRepeat(a.repeatDays)
        holder.b.enabledSwitch.setOnCheckedChangeListener(null)
        holder.b.enabledSwitch.isChecked = a.isEnabled
        holder.b.enabledSwitch.setOnCheckedChangeListener { _, checked -> onToggle(a, checked) }
        holder.b.cardContainer.alpha = if (a.isEnabled) 1f else 0.68f
        holder.b.root.setOnClickListener { onClick(a) }
        holder.b.root.setOnLongClickListener { onLongClick(a); true }
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
