package com.lagradost.cloudstream3.ui.calendartasks

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.CloudStreamApp

object GoogleSyncClient {
    // Easiest option: Placeholders that can be configured by the user/dev
    private const val CLIENT_ID = "YOUR_CLIENT_ID.apps.googleusercontent.com"
    private const val CLIENT_SECRET = "YOUR_CLIENT_SECRET"
    
    private const val OAUTH_TOKEN_KEY = "google_oauth_tokens"
    
    data class GoogleTokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiryTime: Long // Epoch ms
    )
    
    fun getSavedTokens(): GoogleTokens? {
        return CloudStreamApp.getKey(OAUTH_TOKEN_KEY)
    }
    
    fun saveTokens(tokens: GoogleTokens) {
        CloudStreamApp.setKey(OAUTH_TOKEN_KEY, tokens)
    }
    
    fun removeTokens() {
        CloudStreamApp.removeKey(OAUTH_TOKEN_KEY)
    }
    
    fun isAuthenticated(): Boolean {
        return getSavedTokens() != null
    }

    fun getClientId(): String {
        return CloudStreamApp.getKey<String>("google_calendar_client_id") ?: CLIENT_ID
    }
    
    fun getClientSecret(): String {
        return CloudStreamApp.getKey<String>("google_calendar_client_secret") ?: CLIENT_SECRET
    }
    
    fun saveCredentials(clientId: String, clientSecret: String) {
        CloudStreamApp.setKey("google_calendar_client_id", clientId)
        CloudStreamApp.setKey("google_calendar_client_secret", clientSecret)
    }

    suspend fun getOrRefreshToken(): String? {
        val tokens = getSavedTokens() ?: return null
        if (System.currentTimeMillis() < tokens.expiryTime - 60_000) { // 1 min buffer
            return tokens.accessToken
        }
        
        val refresh = tokens.refreshToken ?: return null
        try {
            val response = app.post(
                "https://oauth2.googleapis.com/token",
                params = mapOf(
                    "client_id" to getClientId(),
                    "client_secret" to getClientSecret(),
                    "refresh_token" to refresh,
                    "grant_type" to "refresh_token"
                )
            ).parsedSafe<GoogleTokenResponse>()
            
            if (response != null) {
                val updatedTokens = GoogleTokens(
                    accessToken = response.access_token,
                    refreshToken = response.refresh_token ?: refresh,
                    expiryTime = System.currentTimeMillis() + (response.expires_in * 1000)
                )
                saveTokens(updatedTokens)
                return updatedTokens.accessToken
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    data class GoogleTokenResponse(
        val access_token: String,
        val expires_in: Long,
        val refresh_token: String?,
        val scope: String?,
        val token_type: String
    )
    
    suspend fun fetchCalendarEvents(): List<CalendarEvent> {
        val token = getOrRefreshToken() ?: return emptyList()
        try {
            val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
            val response = app.get(
                url,
                headers = mapOf("Authorization" to "Bearer $token"),
                params = mapOf(
                    "timeMin" to formatIsoTimestamp(System.currentTimeMillis() - 7 * 24 * 3600 * 1000L), // 1 week ago
                    "singleEvents" to "true",
                    "orderBy" to "startTime"
                )
            ).parsedSafe<GoogleCalendarEventsResponse>()
            
            if (response != null) {
                return response.items.mapNotNull { item ->
                    val startMs = parseIsoTimestamp(item.start?.dateTime ?: item.start?.date) ?: return@mapNotNull null
                    val endMs = parseIsoTimestamp(item.end?.dateTime ?: item.end?.date) ?: (startMs + 3600000L)
                    CalendarEvent(
                        id = item.id,
                        title = item.summary ?: "No Title",
                        description = item.description,
                        startTime = startMs,
                        endTime = endMs,
                        location = item.location,
                        isAllDay = item.start?.date != null,
                        status = item.status ?: "confirmed"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
    
    suspend fun fetchTasks(): List<GoogleTask> {
        val token = getOrRefreshToken() ?: return emptyList()
        try {
            val listsResponse = app.get(
                "https://www.googleapis.com/tasks/v1/users/@me/lists",
                headers = mapOf("Authorization" to "Bearer $token")
            ).parsedSafe<GoogleTaskListResponse>()
            
            val defaultListId = listsResponse?.items?.firstOrNull()?.id ?: "@default"
            
            val tasksResponse = app.get(
                "https://www.googleapis.com/tasks/v1/lists/$defaultListId/tasks",
                headers = mapOf("Authorization" to "Bearer $token")
            ).parsedSafe<GoogleTasksResponse>()
            
            if (tasksResponse != null) {
                return tasksResponse.items.map { item ->
                    val dueMs = item.due?.let { parseIsoTimestamp(it) }
                    val completedMs = item.completed?.let { parseIsoTimestamp(it) }
                    GoogleTask(
                        id = item.id,
                        title = item.title ?: "No Title",
                        notes = item.notes,
                        dueDate = dueMs,
                        isCompleted = item.status == "completed",
                        completedDate = completedMs
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
    
    suspend fun updateTaskStatus(task: GoogleTask): Boolean {
        val token = getOrRefreshToken() ?: return false
        try {
            val defaultListId = "@default"
            val status = if (task.isCompleted) "completed" else "needsAction"
            val body = mapOf("status" to status)
            val response = app.patch(
                "https://www.googleapis.com/tasks/v1/lists/$defaultListId/tasks/${task.id}",
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                ),
                json = body
            )
            return response.code == 200
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun fetchEmail(): String? {
        val token = getOrRefreshToken() ?: return null
        try {
            val response = app.get(
                "https://www.googleapis.com/oauth2/v2/userinfo",
                headers = mapOf("Authorization" to "Bearer $token")
            ).parsedSafe<GoogleUserInfo>()
            return response?.email
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun insertTask(title: String, notes: String?): GoogleTask? {
        val token = getOrRefreshToken() ?: return null
        try {
            val defaultListId = "@default"
            val body = mapOf(
                "title" to title,
                "notes" to notes
            )
            val response = app.post(
                "https://www.googleapis.com/tasks/v1/lists/$defaultListId/tasks",
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                ),
                json = body
            )
            val item = response.parsedSafe<TaskItem>()
            if (item != null) {
                val dueMs = item.due?.let { parseIsoTimestamp(it) }
                val completedMs = item.completed?.let { parseIsoTimestamp(it) }
                return GoogleTask(
                    id = item.id,
                    title = item.title ?: "No Title",
                    notes = item.notes,
                    dueDate = dueMs,
                    isCompleted = item.status == "completed",
                    completedDate = completedMs
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun deleteTask(taskId: String): Boolean {
        val token = getOrRefreshToken() ?: return false
        try {
            val defaultListId = "@default"
            val response = app.delete(
                "https://www.googleapis.com/tasks/v1/lists/$defaultListId/tasks/$taskId",
                headers = mapOf(
                    "Authorization" to "Bearer $token"
                )
            )
            return response.code == 204 || response.code == 200
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun insertCalendarEvent(
        title: String,
        description: String?,
        startTimeMs: Long,
        endTimeMs: Long
    ): CalendarEvent? {
        val token = getOrRefreshToken() ?: return null
        try {
            val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
            val body = mapOf(
                "summary" to title,
                "description" to description,
                "start" to mapOf("dateTime" to formatIsoTimestamp(startTimeMs)),
                "end" to mapOf("dateTime" to formatIsoTimestamp(endTimeMs))
            )
            val response = app.post(
                url,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                ),
                json = body
            )
            val item = response.parsedSafe<CalendarEventItem>()
            if (item != null) {
                val startMs = parseIsoTimestamp(item.start?.dateTime ?: item.start?.date) ?: startTimeMs
                val endMs = parseIsoTimestamp(item.end?.dateTime ?: item.end?.date) ?: endTimeMs
                return CalendarEvent(
                    id = item.id,
                    title = item.summary ?: "No Title",
                    description = item.description,
                    startTime = startMs,
                    endTime = endMs,
                    location = item.location,
                    isAllDay = item.start?.date != null,
                    status = item.status ?: "confirmed"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun deleteCalendarEvent(eventId: String): Boolean {
        val token = getOrRefreshToken() ?: return false
        try {
            val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId"
            val response = app.delete(
                url,
                headers = mapOf("Authorization" to "Bearer $token")
            )
            return response.code == 204 || response.code == 200
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun formatIsoTimestamp(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(ms))
    }
    
    private fun parseIsoTimestamp(isoStr: String?): Long? {
        if (isoStr.isNullOrBlank()) return null
        return try {
            val cleanStr = isoStr.replace("Z", "+0000")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            if (cleanStr.contains("T")) {
                sdf.parse(cleanStr)?.time
            } else {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(cleanStr)?.time
            }
        } catch (e: Exception) {
            null
        }
    }
}

// Google JSON Models
data class GoogleCalendarEventsResponse(val items: List<CalendarEventItem> = emptyList())
data class CalendarEventItem(
    val id: String,
    val summary: String?,
    val description: String?,
    val location: String?,
    val status: String?,
    val start: CalendarTime?,
    val end: CalendarTime?
)
data class CalendarTime(val dateTime: String?, val date: String?)

data class GoogleTaskListResponse(val items: List<TaskListItem> = emptyList())
data class TaskListItem(val id: String, val title: String?)

data class GoogleTasksResponse(val items: List<TaskItem> = emptyList())
data class TaskItem(
    val id: String,
    val title: String?,
    val notes: String?,
    val status: String?,
    val due: String?,
    val completed: String?
)

data class GoogleUserInfo(val email: String?)
