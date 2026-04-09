package com.astraval.brightalarm.ui.edit

import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.astraval.brightalarm.alarm.AlarmScheduler
import com.astraval.brightalarm.databinding.ActivityEditAlarmBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class EditAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditAlarmBinding
    private val viewModel: EditAlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        viewModel.load(id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.timePicker.hour = 7
            binding.timePicker.minute = 0
        } else {
            @Suppress("DEPRECATION") binding.timePicker.currentHour = 7
            @Suppress("DEPRECATION") binding.timePicker.currentMinute = 0
        }
        binding.timePicker.setIs24HourView(false)
        binding.ttsCheck.isChecked = true
        binding.ttsCheck.isEnabled = false

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.alarm.collect { a ->
                    a ?: return@collect
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        binding.timePicker.hour = a.hour
                        binding.timePicker.minute = a.minute
                    } else {
                        @Suppress("DEPRECATION") binding.timePicker.currentHour = a.hour
                        @Suppress("DEPRECATION") binding.timePicker.currentMinute = a.minute
                    }
                    binding.messageInput.setText(a.message)
                    binding.vibrateCheck.isChecked = a.isVibrate
                    binding.ttsCheck.isChecked = true
                    dayCheckBoxes().forEachIndexed { idx, cb ->
                        cb.isChecked = (idx + 1) in a.repeatDays
                    }
                }
            }
        }

        binding.saveButton.setOnClickListener { saveAlarm() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun dayCheckBoxes(): List<CheckBox> = listOf(
        binding.dayMon, binding.dayTue, binding.dayWed, binding.dayThu,
        binding.dayFri, binding.daySat, binding.daySun
    )

    private fun saveAlarm() {
        val current = viewModel.alarm.value ?: return
        val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) binding.timePicker.hour
        else @Suppress("DEPRECATION") binding.timePicker.currentHour
        val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) binding.timePicker.minute
        else @Suppress("DEPRECATION") binding.timePicker.currentMinute
        val message = binding.messageInput.text?.toString()?.trim().orEmpty()
        val repeatDays = dayCheckBoxes()
            .mapIndexedNotNull { idx, cb -> if (cb.isChecked) idx + 1 else null }
        val updated = current.copy(
            hour = hour,
            minute = minute,
            message = message,
            isVibrate = binding.vibrateCheck.isChecked,
            useTts = true,
            repeatDays = repeatDays,
            isEnabled = true
        )
        viewModel.save(updated) {
            val next = AlarmScheduler.nextTriggerMillis(updated)
            val fmt = SimpleDateFormat("EEE, MMM d 'at' hh:mm a", Locale.getDefault())
            Toast.makeText(
                this,
                "Alarm set for ${fmt.format(Date(next))}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
