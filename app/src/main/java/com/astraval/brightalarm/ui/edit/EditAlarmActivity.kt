package com.astraval.brightalarm.ui.edit

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.astraval.brightalarm.alarm.AlarmScheduler
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.databinding.ActivityEditAlarmBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class EditAlarmActivity : AppCompatActivity() {

    private enum class EditorMode { ALARM, TASK }

    private lateinit var binding: ActivityEditAlarmBinding
    private val viewModel: EditAlarmViewModel by viewModels()
    private var selectedTriggerAtMillis: Long? = null
    private var editorMode: EditorMode = EditorMode.ALARM
    private var alarmId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeZoneInsets()

        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        editorMode = modeFromIntent()
        viewModel.load(alarmId)

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
        setupTaskRepeatControls()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.alarm.collect { a ->
                    a ?: return@collect
                    editorMode = if (alarmId > 0 && a.triggerAtMillis != null) {
                        EditorMode.TASK
                    } else if (alarmId > 0) {
                        EditorMode.ALARM
                    } else {
                        modeFromIntent()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        binding.timePicker.hour = a.hour
                        binding.timePicker.minute = a.minute
                    } else {
                        @Suppress("DEPRECATION") binding.timePicker.currentHour = a.hour
                        @Suppress("DEPRECATION") binding.timePicker.currentMinute = a.minute
                    }
                    binding.taskTitleInput.setText(a.taskTitle)
                    binding.taskNotesInput.setText(a.taskNotes)
                    binding.messageInput.setText(a.message)
                    binding.vibrateCheck.isChecked = a.isVibrate
                    binding.ttsCheck.isChecked = true
                    dayCheckBoxes().forEachIndexed { idx, cb ->
                        cb.isChecked = (idx + 1) in a.repeatDays
                    }
                    setTaskRepeatUi(a)
                    selectedTriggerAtMillis = if (editorMode == EditorMode.TASK) a.triggerAtMillis else null
                    renderEditorMode()
                }
            }
        }

        binding.pickDateTimeButton.setOnClickListener { showDateTimePicker() }
        binding.saveButton.setOnClickListener { saveAlarm() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun dayCheckBoxes(): List<CheckBox> = listOf(
        binding.dayMon, binding.dayTue, binding.dayWed, binding.dayThu,
        binding.dayFri, binding.daySat, binding.daySun
    )

    private fun taskDayCheckBoxes(): List<CheckBox> = listOf(
        binding.taskDayMon, binding.taskDayTue, binding.taskDayWed, binding.taskDayThu,
        binding.taskDayFri, binding.taskDaySat, binding.taskDaySun
    )

    private fun setupTaskRepeatControls() {
        binding.taskRepeatPresetSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Does not repeat", "Weekly", "Monthly", "Yearly", "Custom")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.taskRepeatUnitSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Days", "Weeks", "Months", "Years")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.taskRepeatPresetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateTaskRepeatVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.taskRepeatUnitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateTaskRepeatVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setTaskRepeatUi(alarm: Alarm) {
        val repeatMode = if (editorMode == EditorMode.TASK) alarm.taskRepeatMode else Alarm.REPEAT_NONE
        binding.taskRepeatPresetSpinner.setSelection(
            when (repeatMode) {
                Alarm.REPEAT_WEEKLY -> 1
                Alarm.REPEAT_MONTHLY -> 2
                Alarm.REPEAT_YEARLY -> 3
                Alarm.REPEAT_CUSTOM -> 4
                else -> 0
            }
        )
        binding.taskRepeatIntervalInput.setText(alarm.taskRepeatInterval.coerceAtLeast(1).toString())
        binding.taskRepeatUnitSpinner.setSelection(
            when (alarm.taskRepeatUnit) {
                Alarm.UNIT_DAY -> 0
                Alarm.UNIT_MONTH -> 2
                Alarm.UNIT_YEAR -> 3
                else -> 1
            }
        )
        taskDayCheckBoxes().forEachIndexed { idx, cb ->
            cb.isChecked = (idx + 1) in alarm.taskRepeatDays
        }
        updateTaskRepeatVisibility()
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

    private fun saveAlarm() {
        val current = viewModel.alarm.value ?: return
        val isTaskAlarm = editorMode == EditorMode.TASK
        val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) binding.timePicker.hour
        else @Suppress("DEPRECATION") binding.timePicker.currentHour
        val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) binding.timePicker.minute
        else @Suppress("DEPRECATION") binding.timePicker.currentMinute
        val taskTitle = binding.taskTitleInput.text?.toString()?.trim().orEmpty()
        val taskNotes = binding.taskNotesInput.text?.toString()?.trim().orEmpty()
        val message = binding.messageInput.text?.toString()?.trim().orEmpty()
        if (message.isBlank()) {
            val itemName = if (isTaskAlarm) "task alarm text" else "alarm text"
            Toast.makeText(this, "Please enter $itemName.", Toast.LENGTH_SHORT).show()
            return
        }
        val repeatDays = dayCheckBoxes()
            .mapIndexedNotNull { idx, cb -> if (cb.isChecked) idx + 1 else null }
        val triggerAtMillis = if (isTaskAlarm) selectedTriggerAtMillis else null
        if (isTaskAlarm && triggerAtMillis == null) {
            Toast.makeText(this, "Please pick a task date and time.", Toast.LENGTH_SHORT).show()
            return
        }
        if (triggerAtMillis != null && triggerAtMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, "Task time must be in the future.", Toast.LENGTH_SHORT).show()
            return
        }
        val taskRepeatMode = if (isTaskAlarm) selectedTaskRepeatMode() else Alarm.REPEAT_NONE
        val taskRepeatUnit = if (isTaskAlarm) selectedTaskRepeatUnit() else Alarm.UNIT_WEEK
        val taskRepeatInterval = if (isTaskAlarm) {
            binding.taskRepeatIntervalInput.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        } else {
            1
        }
        val taskRepeatDays = if (isTaskAlarm) selectedTaskRepeatDays(taskRepeatMode, taskRepeatUnit) else emptyList()

        val taskHour = triggerAtMillis?.let { millis ->
            Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
        } ?: hour
        val taskMinute = triggerAtMillis?.let { millis ->
            Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MINUTE)
        } ?: minute

        val updated = current.copy(
            hour = taskHour,
            minute = taskMinute,
            taskTitle = if (isTaskAlarm) taskTitle else "",
            taskNotes = if (isTaskAlarm) taskNotes else "",
            message = message,
            triggerAtMillis = triggerAtMillis,
            taskRepeatMode = taskRepeatMode,
            taskRepeatInterval = taskRepeatInterval,
            taskRepeatUnit = taskRepeatUnit,
            taskRepeatDays = taskRepeatDays,
            isVibrate = binding.vibrateCheck.isChecked,
            useTts = true,
            repeatDays = if (isTaskAlarm) emptyList() else repeatDays,
            isEnabled = true
        )
        viewModel.save(updated) {
            val next = AlarmScheduler.nextTriggerMillis(updated)
            val fmt = SimpleDateFormat("EEE, MMM d 'at' hh:mm a", Locale.getDefault())
            Toast.makeText(
                this,
                "${if (isTaskAlarm) "Task" else "Alarm"} set for ${fmt.format(Date(next))}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun renderEditorMode() {
        val isTask = editorMode == EditorMode.TASK
        binding.editorEyebrow.text = if (isTask) "TASK MANAGER" else "ALARM EDITOR"
        binding.editorTitle.text = if (isTask) {
            "Create a task with a spoken alarm."
        } else {
            "Create an alarm with a spoken message."
        }
        binding.scheduleSectionTitle.text = if (isTask) "Task date and time" else "Alarm time"
        binding.scheduleSectionDescription.text = if (isTask) {
            "Pick the exact date and time when this task should ring."
        } else {
            "Pick a time that should start the repeated spoken message."
        }
        binding.messageSectionTitle.text = if (isTask) "Task details and alarm text" else "Alarm message"
        binding.saveButton.text = if (isTask) "Save task" else "Save alarm"
        binding.specificDateCheck.isChecked = isTask
        binding.pickDateTimeButton.visibility = if (isTask) View.VISIBLE else View.GONE
        binding.timePicker.visibility = if (isTask) View.GONE else View.VISIBLE
        binding.taskTitleLayout.visibility = if (isTask) View.VISIBLE else View.GONE
        binding.taskNotesLayout.visibility = if (isTask) View.VISIBLE else View.GONE
        binding.taskRepeatCard.visibility = if (isTask) View.VISIBLE else View.GONE
        binding.repeatCard.visibility = if (isTask) View.GONE else View.VISIBLE
        if (!isTask) {
            selectedTriggerAtMillis = null
        } else {
            updatePickDateLabel()
        }
        updateTaskRepeatVisibility()
    }

    private fun updateTaskRepeatVisibility() {
        if (editorMode != EditorMode.TASK) {
            binding.taskCustomRepeatContainer.visibility = View.GONE
            binding.taskWeekdayGrid.visibility = View.GONE
            return
        }
        val isCustom = binding.taskRepeatPresetSpinner.selectedItemPosition == 4
        val selectedUnit = selectedTaskRepeatUnit()
        val showWeekdays =
            binding.taskRepeatPresetSpinner.selectedItemPosition == 1 ||
                    (isCustom && selectedUnit == Alarm.UNIT_WEEK)
        binding.taskCustomRepeatContainer.visibility = if (isCustom) View.VISIBLE else View.GONE
        binding.taskWeekdayGrid.visibility = if (showWeekdays) View.VISIBLE else View.GONE
    }

    private fun selectedTaskRepeatMode(): String {
        return when (binding.taskRepeatPresetSpinner.selectedItemPosition) {
            1 -> Alarm.REPEAT_WEEKLY
            2 -> Alarm.REPEAT_MONTHLY
            3 -> Alarm.REPEAT_YEARLY
            4 -> Alarm.REPEAT_CUSTOM
            else -> Alarm.REPEAT_NONE
        }
    }

    private fun selectedTaskRepeatUnit(): String {
        return when (binding.taskRepeatUnitSpinner.selectedItemPosition) {
            0 -> Alarm.UNIT_DAY
            2 -> Alarm.UNIT_MONTH
            3 -> Alarm.UNIT_YEAR
            else -> Alarm.UNIT_WEEK
        }
    }

    private fun selectedTaskRepeatDays(repeatMode: String, repeatUnit: String): List<Int> {
        val usesWeekdays = repeatMode == Alarm.REPEAT_WEEKLY ||
                (repeatMode == Alarm.REPEAT_CUSTOM && repeatUnit == Alarm.UNIT_WEEK)
        if (!usesWeekdays) return emptyList()
        val checkedDays = taskDayCheckBoxes()
            .mapIndexedNotNull { idx, cb -> if (cb.isChecked) idx + 1 else null }
        return checkedDays.ifEmpty { selectedTriggerAtMillis?.let { listOf(isoDay(it)) } ?: emptyList() }
    }

    private fun modeFromIntent(): EditorMode {
        return when (intent.getStringExtra(EXTRA_CREATE_MODE)) {
            MODE_TASK -> EditorMode.TASK
            else -> EditorMode.ALARM
        }
    }

    private fun showDateTimePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select task date")
            .setSelection(selectedTriggerAtMillis ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { dateMillis ->
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            val hourSeed = if (selectedTriggerAtMillis != null) {
                Calendar.getInstance().apply { timeInMillis = selectedTriggerAtMillis!! }.get(Calendar.HOUR_OF_DAY)
            } else {
                cal.get(Calendar.HOUR_OF_DAY)
            }
            val minuteSeed = if (selectedTriggerAtMillis != null) {
                Calendar.getInstance().apply { timeInMillis = selectedTriggerAtMillis!! }.get(Calendar.MINUTE)
            } else {
                cal.get(Calendar.MINUTE)
            }
            android.app.TimePickerDialog(
                this,
                { _, h, m ->
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    cal.set(Calendar.MINUTE, m)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    selectedTriggerAtMillis = cal.timeInMillis
                    if (taskDayCheckBoxes().none { it.isChecked }) {
                        taskDayCheckBoxes().forEachIndexed { idx, cb ->
                            cb.isChecked = idx + 1 == isoDay(cal.timeInMillis)
                        }
                    }
                    updatePickDateLabel()
                },
                hourSeed,
                minuteSeed,
                false
            ).show()
        }
        datePicker.show(supportFragmentManager, "taskDatePicker")
    }

    private fun updatePickDateLabel() {
        val label = selectedTriggerAtMillis?.let {
            val fmt = SimpleDateFormat("EEE, MMM d 'at' hh:mm a", Locale.getDefault())
            "Date & time: ${fmt.format(Date(it))}"
        } ?: "Pick date and time"
        binding.pickDateTimeButton.text = label
    }

    private fun isoDay(timeInMillis: Long): Int {
        val day = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
            .get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_CREATE_MODE = "create_mode"
        const val MODE_ALARM = "alarm"
        const val MODE_TASK = "task"
    }
}
