package com.lagradost.cloudstream3.ui.calendartasks

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.utils.CalendarTvChannelHelper
import com.lagradost.cloudstream3.utils.PlannerWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CalendarViewModel : ViewModel() {

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _tasks = MutableStateFlow<List<GoogleTask>>(emptyList())
    val tasks: StateFlow<List<GoogleTask>> = _tasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val cacheKey = "local_calendar_tasks_cache"

    fun loadData() {
        _isLoading.value = true
        try {
            val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
            _events.value = cache.events.sortedBy { it.startTime }
            _tasks.value = cache.tasks
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isLoading.value = false
        }
    }

    private fun updateWidget(context: Context) {
        try {
            val intent = Intent(context, PlannerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, PlannerWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addTask(context: Context, title: String, notes: String?, priority: String = "Normal", dueMs: Long? = null, recurrence: String = "None") {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val newTask = GoogleTask(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    notes = notes,
                    dueDate = dueMs,
                    priority = priority,
                    isCompleted = false,
                    recurrence = recurrence
                )
                val currentTasks = _tasks.value + newTask
                _tasks.value = currentTasks
                
                val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
                val updatedCache = cache.copy(
                    tasks = currentTasks,
                    lastSyncTime = System.currentTimeMillis()
                )
                CloudStreamApp.setKey(cacheKey, updatedCache)
                
                CalendarTvChannelHelper.syncToTvLauncher(context, updatedCache)
                updateWidget(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleTask(context: Context, task: GoogleTask) {
        val isChecking = !task.isCompleted
        
        // If checking a recurring task with a due date, advance it rather than closing it
        val updatedTask = if (isChecking && task.recurrence != "None" && task.recurrence.isNotBlank() && task.dueDate != null) {
            val interval = when (task.recurrence.lowercase()) {
                "daily" -> 86400000L
                "weekly" -> 7 * 86400000L
                "monthly" -> 30 * 86400000L
                else -> 0L
            }
            if (interval > 0L) {
                task.copy(dueDate = task.dueDate + interval, isCompleted = false)
            } else {
                task.copy(isCompleted = isChecking)
            }
        } else {
            task.copy(isCompleted = isChecking)
        }

        val currentTasks = _tasks.value.map { if (it.id == task.id) updatedTask else it }
        _tasks.value = currentTasks
        
        val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
        val updatedCache = cache.copy(
            tasks = currentTasks,
            lastSyncTime = System.currentTimeMillis()
        )
        CloudStreamApp.setKey(cacheKey, updatedCache)
        
        CalendarTvChannelHelper.syncToTvLauncher(context, updatedCache)
        updateWidget(context)
    }

    fun deleteTask(context: Context, task: GoogleTask) {
        val currentTasks = _tasks.value.filter { it.id != task.id }
        _tasks.value = currentTasks
        
        val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
        val updatedCache = cache.copy(
            tasks = currentTasks,
            lastSyncTime = System.currentTimeMillis()
        )
        CloudStreamApp.setKey(cacheKey, updatedCache)
        
        CalendarTvChannelHelper.syncToTvLauncher(context, updatedCache)
        updateWidget(context)
    }

    fun addEvent(
        context: Context,
        title: String,
        description: String?,
        category: String,
        recurrence: String,
        startTimeMs: Long,
        endTimeMs: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val newEvent = CalendarEvent(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    startTime = startTimeMs,
                    endTime = endTimeMs,
                    location = null,
                    category = category,
                    recurrence = recurrence
                )
                val currentEvents = (_events.value + newEvent).sortedBy { it.startTime }
                _events.value = currentEvents
                
                val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
                val updatedCache = cache.copy(
                    events = currentEvents,
                    lastSyncTime = System.currentTimeMillis()
                )
                CloudStreamApp.setKey(cacheKey, updatedCache)
                
                CalendarTvChannelHelper.syncToTvLauncher(context, updatedCache)
                updateWidget(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEvent(context: Context, event: CalendarEvent) {
        val currentEvents = _events.value.filter { it.id != event.id }
        _events.value = currentEvents
        
        val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
        val updatedCache = cache.copy(
            events = currentEvents,
            lastSyncTime = System.currentTimeMillis()
        )
        CloudStreamApp.setKey(cacheKey, updatedCache)
        
        CalendarTvChannelHelper.syncToTvLauncher(context, updatedCache)
        updateWidget(context)
    }

    fun clearAll(context: Context) {
        _events.value = emptyList()
        _tasks.value = emptyList()
        CloudStreamApp.setKey(cacheKey, CalendarTaskCache())
        
        val channelId = com.lagradost.cloudstream3.utils.TvChannelUtils.getChannelId(context, "Calendar & Tasks")
        if (channelId != null) {
            com.lagradost.cloudstream3.utils.TvChannelUtils.deleteTvChannel(context, channelId)
        }
        
        val eventsChannelId = com.lagradost.cloudstream3.utils.TvChannelUtils.getChannelId(context, "Events")
        if (eventsChannelId != null) {
            com.lagradost.cloudstream3.utils.TvChannelUtils.deleteTvChannel(context, eventsChannelId)
        }
        
        updateWidget(context)
    }
}
