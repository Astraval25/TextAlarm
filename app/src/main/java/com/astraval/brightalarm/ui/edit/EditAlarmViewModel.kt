package com.astraval.brightalarm.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astraval.brightalarm.alarm.AlarmScheduler
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditAlarmViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    private val _alarm = MutableStateFlow<Alarm?>(null)
    val alarm: StateFlow<Alarm?> = _alarm

    fun load(id: Int) {
        if (id <= 0) {
            _alarm.value = Alarm(hour = 7, minute = 0, message = "")
            return
        }
        viewModelScope.launch {
            _alarm.value = repository.getById(id) ?: Alarm(hour = 7, minute = 0, message = "")
        }
    }

    fun save(alarm: Alarm, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = repository.upsert(alarm)
            val stored = alarm.copy(id = id)
            scheduler.cancel(id)
            if (stored.isEnabled) scheduler.schedule(stored)
            onDone()
        }
    }
}