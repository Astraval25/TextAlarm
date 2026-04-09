package com.astraval.brightalarm.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao
) {
    fun observeAlarms(): Flow<List<Alarm>> = dao.getAllAlarms()
    suspend fun getEnabled(): List<Alarm> = dao.getEnabledAlarms()
    suspend fun getById(id: Int): Alarm? = dao.getAlarmById(id)
    suspend fun upsert(alarm: Alarm): Int {
        return if (alarm.id == 0) dao.insertAlarm(alarm).toInt()
        else { dao.updateAlarm(alarm); alarm.id }
    }
    suspend fun delete(alarm: Alarm) = dao.deleteAlarm(alarm)
    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) =
        dao.updateAlarm(alarm.copy(isEnabled = enabled))
}