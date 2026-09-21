package com.openjarvis.automation

import android.content.Context
import androidx.room.*
import androidx.work.*
import com.openjarvis.graphify.GraphifyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

class AutomationManager(private val context: Context) {
    
    private val db = AutomationDB.getInstance(context)
    private val dao = db.automationDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _automationsFlow = MutableStateFlow<List<Automation>>(emptyList())
    val automationsFlow: StateFlow<List<Automation>> = _automationsFlow
    
    suspend fun loadAutomations() {
        _automationsFlow.value = dao.getAll().map { it.toAutomation() }
    }
    
    suspend fun createAutomation(automation: Automation): String = withContext(Dispatchers.IO) {
        dao.insert(automation.toEntity())
        
        scheduleAutomation(automation)
        
        _automationsFlow.value = dao.getAll().map { it.toAutomation() }
        automation.id
    }
    
    suspend fun updateAutomation(automation: Automation) = withContext(Dispatchers.IO) {
        dao.update(automation.toEntity())
        cancelAutomation(automation.id)
        
        if (automation.enabled) {
            scheduleAutomation(automation)
        }
        
        _automationsFlow.value = dao.getAll().map { it.toAutomation() }
    }
    
    suspend fun deleteAutomation(id: String) = withContext(Dispatchers.IO) {
        cancelAutomation(id)
        dao.delete(id)
        _automationsFlow.value = dao.getAll().map { it.toAutomation() }
    }
    
    suspend fun toggleAutomation(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        val automation = entity.toAutomation()
        val updated = automation.copy(enabled = enabled)
        dao.update(updated.toEntity())
        
        if (enabled) {
            scheduleAutomation(updated)
        } else {
            cancelAutomation(id)
        }
        
        _automationsFlow.value = dao.getAll().map { it.toAutomation() }
    }
    
    suspend fun runNow(id: String) = withContext(Dispatchers.IO) {
        val automation = dao.getById(id)?.toAutomation() ?: return@withContext
        executeAutomation(automation)
    }
    
    private suspend fun scheduleAutomation(automation: Automation) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()
        
        val inputData = workDataOf(
            "automation_id" to automation.id,
            "automation_command" to automation.command
        )
        
        val request = when (val schedule = automation.schedule) {
            is AutomationSchedule.Daily -> {
                PeriodicWorkRequestBuilder<AutomationWorker>(
                    24, TimeUnit.HOURS,
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .setInitialDelay(calculateDelay(schedule.hour, schedule.minute), TimeUnit.MILLISECONDS)
                    .addTag(automation.id)
                    .build()
            }
            is AutomationSchedule.Weekly -> {
                PeriodicWorkRequestBuilder<AutomationWorker>(
                    7, TimeUnit.DAYS,
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .setInitialDelay(calculateWeeklyDelay(schedule.dayOfWeek, schedule.hour, schedule.minute), TimeUnit.MILLISECONDS)
                    .addTag(automation.id)
                    .build()
            }
            is AutomationSchedule.Interval -> {
                val safeInterval = maxOf(schedule.intervalMs, TimeUnit.MINUTES.toMillis(15))
                PeriodicWorkRequestBuilder<AutomationWorker>(
                    safeInterval, TimeUnit.MILLISECONDS,
                    1, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .addTag(automation.id)
                    .build()
            }
            is AutomationSchedule.Once -> {
                val delay = schedule.atMs - System.currentTimeMillis()
                if (delay <= 0) return
                
                OneTimeWorkRequestBuilder<AutomationWorker>()
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .addTag(automation.id)
                    .build()
            }
        }
        
        WorkManager.getInstance(context)
            .enqueueUniqueWork(automation.id, ExistingWorkPolicy.REPLACE, request)
    }
    
    private fun cancelAutomation(id: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(id)
    }
    
    private suspend fun executeAutomation(automation: Automation) {
        val result = try {
            val agent = com.openjarvis.agent.AgentCore(context.applicationContext)
            val job = agent.executeTask(automation.command)
            job.join()
            if (agent.state.value is com.openjarvis.agent.AgentState.Done) "success"
            else "failed"
        } catch (e: Exception) {
            "error: ${e.message}"
        }

        val updated = automation.copy(
            lastRun = System.currentTimeMillis(),
            lastResult = result,
            runCount = automation.runCount + 1
        )
        dao.update(updated.toEntity())
    }
    
    private fun calculateDelay(targetHour: Int, targetMinute: Int): Long {
        val cal = java.util.Calendar.getInstance()
        val now = cal.timeInMillis
        
        cal.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
        cal.set(java.util.Calendar.MINUTE, targetMinute)
        cal.set(java.util.Calendar.SECOND, 0)
        
        var delay = cal.timeInMillis - now
        if (delay < 0) delay += 24 * 60 * 60 * 1000
        
        return delay
    }
    
    private fun calculateWeeklyDelay(dayOfWeek: Int, targetHour: Int, targetMinute: Int): Long {
        val cal = java.util.Calendar.getInstance()
        val now = cal.timeInMillis
        
        cal.set(java.util.Calendar.DAY_OF_WEEK, dayOfWeek)
        cal.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
        cal.set(java.util.Calendar.MINUTE, targetMinute)
        cal.set(java.util.Calendar.SECOND, 0)
        
        var delay = cal.timeInMillis - now
        if (delay < 0) delay += 7 * 24 * 60 * 60 * 1000
        
        return delay
    }
    
    fun parseSchedule(input: String): AutomationSchedule? {
        val lower = input.lowercase()
        
        val dailyMatch = Regex("""every day at (\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE).find(lower)
        if (dailyMatch != null) {
            var hour = dailyMatch.groupValues[1].toInt()
            val minute = dailyMatch.groupValues[2].toIntOrNull() ?: 0
            val isPM = dailyMatch.groupValues[3].lowercase() == "pm"
            if (isPM && hour != 12) hour += 12
            if (!isPM && hour == 12) hour = 0
            return AutomationSchedule.Daily(hour, minute)
        }
        
        val weeklyMatch = Regex("""every (monday|tuesday|wednesday|thursday|friday|saturday|sunday) at (\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE).find(lower)
        if (weeklyMatch != null) {
            val day = mapOf(
                "sunday" to java.util.Calendar.SUNDAY, "monday" to java.util.Calendar.MONDAY,
                "tuesday" to java.util.Calendar.TUESDAY, "wednesday" to java.util.Calendar.WEDNESDAY,
                "thursday" to java.util.Calendar.THURSDAY, "friday" to java.util.Calendar.FRIDAY,
                "saturday" to java.util.Calendar.SATURDAY
            )[weeklyMatch.groupValues[1].lowercase()] ?: java.util.Calendar.MONDAY
            var hour = weeklyMatch.groupValues[2].toInt()
            val minute = weeklyMatch.groupValues[3].toIntOrNull() ?: 0
            val ampm = weeklyMatch.groupValues[4].lowercase()
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return AutomationSchedule.Weekly(day, hour, minute)
        }

        val intervalMatch = Regex("""every (\d+)\s*(minute|hour|day)s?""", RegexOption.IGNORE_CASE).find(lower)
        if (intervalMatch != null) {
            val value = intervalMatch.groupValues[1].toInt()
            val unit = intervalMatch.groupValues[2]
            val ms = when (unit) {
                "minute" -> value * 60 * 1000L
                "hour" -> value * 60 * 60 * 1000L
                "day" -> value * 24 * 60 * 60 * 1000L
                else -> 60 * 60 * 1000L
            }
            return AutomationSchedule.Interval(ms)
        }
        
        return null
    }
    
    private fun Automation.toEntity(): AutomationEntity = AutomationEntity(
        id = id, name = name, command = command,
        scheduleType = when (schedule) {
            is AutomationSchedule.Daily -> "daily"
            is AutomationSchedule.Weekly -> "weekly"
            is AutomationSchedule.Interval -> "interval"
            is AutomationSchedule.Once -> "once"
        },
        scheduleHour = when (schedule) {
            is AutomationSchedule.Daily -> schedule.hour
            is AutomationSchedule.Weekly -> schedule.hour
            else -> 0
        },
        scheduleMinute = when (schedule) {
            is AutomationSchedule.Daily -> schedule.minute
            is AutomationSchedule.Weekly -> schedule.minute
            else -> 0
        },
        scheduleDayOfWeek = (schedule as? AutomationSchedule.Weekly)?.dayOfWeek ?: 0,
        scheduleIntervalMs = when (schedule) {
            is AutomationSchedule.Interval -> schedule.intervalMs
            is AutomationSchedule.Once -> schedule.atMs
            else -> 0
        },
        enabled = enabled, lastRun = lastRun, lastResult = lastResult, runCount = runCount
    )

    private fun AutomationEntity.toAutomation(): Automation {
        val schedule = when (scheduleType) {
            "weekly" -> AutomationSchedule.Weekly(scheduleDayOfWeek, scheduleHour, scheduleMinute)
            "interval" -> AutomationSchedule.Interval(scheduleIntervalMs)
            "once" -> AutomationSchedule.Once(scheduleIntervalMs)
            else -> AutomationSchedule.Daily(scheduleHour, scheduleMinute)
        }
        return Automation(id, name, command, schedule, enabled, lastRun, lastResult, runCount)
    }

    data class Automation(
        val id: String,
        val name: String,
        val command: String,
        val schedule: AutomationSchedule,
        val enabled: Boolean = true,
        val lastRun: Long? = null,
        val lastResult: String? = null,
        val runCount: Int = 0
    )
    
    sealed class AutomationSchedule {
        data class Daily(val hour: Int, val minute: Int) : AutomationSchedule()
        data class Weekly(val dayOfWeek: Int, val hour: Int, val minute: Int) : AutomationSchedule()
        data class Interval(val intervalMs: Long) : AutomationSchedule()
        data class Once(val atMs: Long) : AutomationSchedule()
    }
}