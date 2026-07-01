package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.ui.calendartasks.CalendarTaskCache
import com.lagradost.cloudstream3.ui.calendartasks.CalendarEvent
import com.lagradost.cloudstream3.ui.calendartasks.GoogleTask

object CalendarTvChannelHelper {

    private const val CHANNEL_NAME = "Events"

    @SuppressLint("RestrictedApi")
    fun syncToTvLauncher(context: Context, cache: CalendarTaskCache) {
        try {
            // Clean up old legacy channel name if exists
            val oldChannelId = TvChannelUtils.getChannelId(context, "Calendar & Tasks")
            if (oldChannelId != null) {
                TvChannelUtils.deleteTvChannel(context, oldChannelId)
            }

            val channelId = TvChannelUtils.getChannelId(context, CHANNEL_NAME)
                ?: createCalendarChannel(context)
                
            if (channelId != null) {
                TvChannelUtils.clearProgramsForChannel(context, channelId)
                
                // Add expanded events (including recurring occurrences)
                val expandedEvents = getUpcomingOccurrences(cache.events)
                addEventPrograms(context, channelId, expandedEvents)
                
                // Add Tasks (only uncompleted tasks)
                addTaskPrograms(context, channelId, cache.tasks.filter { !it.isCompleted })
            }
        } catch (e: Exception) {
            Log.e("CalendarTvChannelHelper", "Failed to sync to TV Launcher: ${e.message}", e)
        }
    }

    private fun getUpcomingOccurrences(events: List<CalendarEvent>, days: Int = 7): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        val now = System.currentTimeMillis()
        val endLimit = now + days * 86400000L

        for (event in events) {
            if (event.recurrence == "None" || event.recurrence.isBlank()) {
                if (event.startTime >= now - 7200000) { // include events started in the last 2 hours
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

                    // Fast forward to present window if the event start is in the past
                    while (currentStart < now - 7200000 && currentStart < endLimit) {
                        currentStart += interval
                        currentEnd += interval
                    }

                    var limit = 0
                    while (currentStart < endLimit && limit < 15) {
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

    private fun createCalendarChannel(context: Context): Long? {
        val componentName = ComponentName(context, MainActivity::class.java)
        val iconUri = "android.resource://${context.packageName}/mipmap/ic_launcher".toUri()
        val inputId = TvContractCompat.buildInputId(componentName)
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setAppLinkIconUri(iconUri)
            .setDisplayName(CHANNEL_NAME)
            .setAppLinkIntent(Intent(Intent.ACTION_VIEW).apply {
                data = "cloudstreamapp://calendar_tasks".toUri()
            })
            .setInputId(inputId)
            .build()

        return try {
            val channelUri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
            )

            channelUri?.let {
                val channelId = ContentUris.parseId(it)
                TvContractCompat.requestChannelBrowsable(context, channelId)
                Log.d("CalendarTvChannelHelper", "Events TV Channel Created: $channelId")
                channelId
            }
        } catch (e: Exception) {
            Log.e("CalendarTvChannelHelper", "Failed to create Events channel", e)
            null
        }
    }

    @SuppressLint("RestrictedApi")
    private fun addEventPrograms(context: Context, channelId: Long, events: List<CalendarEvent>) {
        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        
        for (event in events) {
            try {
                // Strip virtual suffix for redirection intent target
                val cleanId = event.id.substringBefore("_occ_")
                val intentUri = "cloudstreamapp://calendar_tasks?event_id=${cleanId}"
                val desc = "Starts: ${sdf.format(java.util.Date(event.startTime))}" + 
                           (event.location?.let { " @ $it" } ?: "")
                
                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setTitle(event.title)
                    .setDescription(desc)
                    .setContentId(event.id)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_EVENT)
                    .setIntentUri(intentUri.toUri())
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                
                val program = builder.build()
                context.contentResolver.insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    program.toContentValues()
                )
            } catch (e: Exception) {
                Log.e("CalendarTvChannelHelper", "Error adding event program: ${event.title}", e)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun addTaskPrograms(context: Context, channelId: Long, tasks: List<GoogleTask>) {
        val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        
        for (task in tasks) {
            try {
                val intentUri = "cloudstreamapp://calendar_tasks?task_id=${task.id}"
                val desc = task.dueDate?.let { "Due: ${sdf.format(java.util.Date(it))}" } ?: "No due date"
                
                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setTitle("[Task] ${task.title}")
                    .setDescription(desc)
                    .setContentId(task.id)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                    .setIntentUri(intentUri.toUri())
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                
                val program = builder.build()
                context.contentResolver.insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    program.toContentValues()
                )
            } catch (e: Exception) {
                Log.e("CalendarTvChannelHelper", "Error adding task program: ${task.title}", e)
            }
        }
    }
}
