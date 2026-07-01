package com.lagradost.cloudstream3.utils

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.ui.calendartasks.CalendarTaskCache
import com.lagradost.cloudstream3.ui.calendartasks.CalendarEvent
import com.lagradost.cloudstream3.ui.calendartasks.GoogleTask

class PlannerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.planner_widget)

            // Setup click intent to launch Calendar & Tasks screen
            val launchIntent = Intent(Intent.ACTION_VIEW, "cloudstreamapp://calendar_tasks".toUri(), context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_open_app, pendingIntent)

            // Retrieve local planner cache
            val cacheKey = "local_calendar_tasks_cache"
            val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()

            // Process upcoming events occurrences
            val expandedEvents = getUpcomingOccurrences(cache.events)
            val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())

            // Bind Event 1
            if (expandedEvents.isNotEmpty()) {
                val event1 = expandedEvents[0]
                val timeStr = sdf.format(java.util.Date(event1.startTime))
                views.setTextViewText(R.id.widget_event_1, "• ${event1.title} ($timeStr)")
            } else {
                views.setTextViewText(R.id.widget_event_1, "No upcoming events")
            }

            // Bind Event 2
            if (expandedEvents.size > 1) {
                val event2 = expandedEvents[1]
                val timeStr = sdf.format(java.util.Date(event2.startTime))
                views.setTextViewText(R.id.widget_event_2, "• ${event2.title} ($timeStr)")
                views.setViewVisibility(R.id.widget_event_2, View.VISIBLE)
            } else {
                views.setTextViewText(R.id.widget_event_2, "")
                views.setViewVisibility(R.id.widget_event_2, View.GONE)
            }

            // Process pending tasks
            val pendingTasks = cache.tasks.filter { !it.isCompleted }

            // Bind Task 1
            if (pendingTasks.isNotEmpty()) {
                val task1 = pendingTasks[0]
                val priorityStr = if (task1.priority.isNotBlank() && !task1.priority.equals("Normal", ignoreCase = true)) " [${task1.priority}]" else ""
                views.setTextViewText(R.id.widget_task_1, "□ ${task1.title}$priorityStr")
            } else {
                views.setTextViewText(R.id.widget_task_1, "No pending tasks")
            }

            // Bind Task 2
            if (pendingTasks.size > 1) {
                val task2 = pendingTasks[1]
                val priorityStr = if (task2.priority.isNotBlank() && !task2.priority.equals("Normal", ignoreCase = true)) " [${task2.priority}]" else ""
                views.setTextViewText(R.id.widget_task_2, "□ ${task2.title}$priorityStr")
                views.setViewVisibility(R.id.widget_task_2, View.VISIBLE)
            } else {
                views.setTextViewText(R.id.widget_task_2, "")
                views.setViewVisibility(R.id.widget_task_2, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getUpcomingOccurrences(events: List<CalendarEvent>, days: Int = 7): List<CalendarEvent> {
            val result = mutableListOf<CalendarEvent>()
            val now = System.currentTimeMillis()
            val endLimit = now + days * 86400000L

            for (event in events) {
                if (event.recurrence == "None" || event.recurrence.isBlank()) {
                    if (event.startTime >= now - 7200000) {
                        result.add(event)
                    }
                } else {
                    val interval = when (event.recurrence.lowercase()) {
                        "daily" -> 86400000L
                        "weekly" -> 7 * 86400000L
                        "monthly" -> 30 * 86400000L
                        else -> 0L
                    }
                    if (interval > 0L) {
                        var currentStart = event.startTime
                        var currentEnd = event.endTime

                        while (currentStart < now - 7200000 && currentStart < endLimit) {
                            currentStart += interval
                            currentEnd += interval
                        }

                        var limit = 0
                        while (currentStart < endLimit && limit < 5) {
                            result.add(
                                event.copy(
                                    id = "${event.id}_occ_${currentStart}",
                                    startTime = currentStart,
                                    endTime = currentEnd
                                )
                            )
                            currentStart += interval
                            currentEnd += interval
                            limit++
                        }
                    }
                }
            }
            return result.sortedBy { it.startTime }
        }
    }
}
