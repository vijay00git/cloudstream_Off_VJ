package com.lagradost.cloudstream3.ui.calendartasks

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startTime: Long, // unix timestamp ms
    val endTime: Long,   // unix timestamp ms
    val location: String?,
    val isAllDay: Boolean = false,
    val status: String = "confirmed",
    val category: String = "General", // Watch, Personal, Urgent, General
    val recurrence: String = "None"  // None, Daily, Weekly, Monthly
)

data class GoogleTask(
    val id: String,
    val title: String,
    val notes: String?,
    val dueDate: Long?, // unix timestamp ms
    val isCompleted: Boolean = false,
    val completedDate: Long? = null,
    val priority: String = "Normal", // High, Normal, Low
    val category: String = "General", // Personal, Watchlist, General
    val recurrence: String = "None"  // None, Daily, Weekly, Monthly
)

data class CalendarTaskCache(
    val events: List<CalendarEvent> = emptyList(),
    val tasks: List<GoogleTask> = emptyList(),
    val lastSyncTime: Long = 0L,
    val googleEmail: String? = null
)
