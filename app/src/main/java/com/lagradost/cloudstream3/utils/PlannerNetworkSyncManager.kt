package com.lagradost.cloudstream3.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.ui.calendartasks.CalendarTaskCache
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils.BackupFile
import com.lagradost.cloudstream3.utils.BackupUtils.BackupVars
import com.lagradost.cloudstream3.utils.BackupUtils.restore
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class PlannerNetworkSyncManager(
    private val context: Context,
    private val onHostsUpdated: (List<NsdServiceInfo>) -> Unit,
    private val onSyncStatus: (String) -> Unit,
    private val onSyncCompleted: () -> Unit
) {
    private val TAG = "PlannerSyncManager"
    private val SERVICE_TYPE = "_csplannersync._tcp."
    private val SERVICE_NAME = "CloudStreamPlanner"
    private val PORT = 8085

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false

    private val discoveredServices = mutableMapOf<String, NsdServiceInfo>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val nonTransferableKeys = listOf(
        "anilist_cached_list",
        "mal_cached_list",
        "kitsu_cached_list",
        "plugins_key",
        "plugins_key_local",
        "account_token",
        "account_ids",
        "biometric_key",
        "nginx_user",
        "download_path_key",
        "download_path_key_visual",
        "backup_path_key",
        "backup_dir_path_key",
        "anilist_token",
        "anilist_user",
        "mal_user",
        "mal_token",
        "mal_refresh_token",
        "mal_unixtime",
        "open_subtitles_user",
        "subdl_user",
        "simkl_token",
        "download_episode_cache_backup",
        "download_episode_cache",
        "download_info",
        "resume_in_queue",
        "resume_packages",
        "queue_key",
        "auto_download_plugins_key2"
    )

    private fun String.isTransferable(): Boolean {
        return !nonTransferableKeys.any { this.contains(it, ignoreCase = true) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getLocalBackup(context: Context): BackupFile {
        val allData = context.getSharedPrefs().all.filter { it.key.isTransferable() }
        val allSettings = context.getDefaultSharedPrefs().all.filter { it.key.isTransferable() }

        val allDataSorted = BackupVars(
            allData.filter { it.value is Boolean } as? Map<String, Boolean>,
            allData.filter { it.value is Int } as? Map<String, Int>,
            allData.filter { it.value is String } as? Map<String, String>,
            allData.filter { it.value is Float } as? Map<String, Float>,
            allData.filter { it.value is Long } as? Map<String, Long>,
            allData.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        val allSettingsSorted = BackupVars(
            allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
            allSettings.filter { it.value is Int } as? Map<String, Int>,
            allSettings.filter { it.value is String } as? Map<String, String>,
            allSettings.filter { it.value is Float } as? Map<String, Float>,
            allSettings.filter { it.value is Long } as? Map<String, Long>,
            allSettings.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        return BackupFile(
            allDataSorted,
            allSettingsSorted
        )
    }

    // --- Server (Host) Side ---

    fun startHosting() {
        if (isServerRunning) return
        isServerRunning = true

        thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Sync server started on port $PORT")
                onSyncStatus("Hosting sync on port $PORT...")

                // Register service with NSD
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = SERVICE_NAME
                    serviceType = SERVICE_TYPE
                    port = PORT
                }
                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)

                while (isServerRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread {
                        handleClientConnection(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}", e)
                onSyncStatus("Host Error: ${e.message}")
            }
        }
    }

    fun stopHosting() {
        isServerRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            // ignore
        }
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: Exception) {
            // ignore
        }
        onSyncStatus("Hosting stopped")
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            Log.d(TAG, "Request: $requestLine")

            val tokens = requestLine.split(" ")
            if (tokens.size < 2) return
            val method = tokens[0]
            val path = tokens[1]

            // Read headers to get Content-Length if POST
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.lowercase().startsWith("content-length:")) {
                    contentLength = line!!.substringAfter(":").trim().toInt()
                }
            }

            if (method == "POST" && path == "/sync") {
                // Read POST body
                val body = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val res = reader.read(body, read, contentLength - read)
                    if (res == -1) break
                    read += res
                }
                val jsonBody = String(body)
                val remoteBackup = parseJson<BackupFile>(jsonBody)

                // Get local backup and merge
                val localBackup = getLocalBackup(context)
                val mergedBackup = mergeBackups(localBackup, remoteBackup)

                // Save merged backup to local storage
                restore(context, mergedBackup, restoreSettings = true, restoreDataStore = true)

                // Update TV Launcher EPG channel if calendar events changed
                val cacheKey = "local_calendar_tasks_cache"
                val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
                CalendarTvChannelHelper.syncToTvLauncher(context, cache)
                updateWidget(context)
                onSyncCompleted()

                // Respond with merged backup JSON
                val responseJson = mergedBackup.toJson()
                sendResponse(out, 200, "OK", "application/json", responseJson)
                Log.d(TAG, "Sync complete, saved locally & responded with merged data")
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection: ${e.message}", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun sendResponse(out: OutputStream, statusCode: Int, statusText: String, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    // --- Client (Discoverer) Side ---

    fun startDiscovery() {
        discoveredServices.clear()
        onHostsUpdated(emptyList())

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                onSyncStatus("Discovery failed to start")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Service discovery started")
                onSyncStatus("Searching for devices on network...")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Service discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service found: $serviceInfo")
                if (serviceInfo == null) return
                val infoType = serviceInfo.serviceType
                if (infoType.contains("csplannersync")) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo?) {
                            Log.d(TAG, "Service resolved: $resolvedServiceInfo")
                            if (resolvedServiceInfo != null) {
                                val key = resolvedServiceInfo.host?.hostAddress ?: resolvedServiceInfo.serviceName
                                discoveredServices[key] = resolvedServiceInfo
                                onHostsUpdated(discoveredServices.values.toList())
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service lost: $serviceInfo")
                if (serviceInfo == null) return
                val key = serviceInfo.host?.hostAddress ?: serviceInfo.serviceName
                discoveredServices.remove(key)
                onHostsUpdated(discoveredServices.values.toList())
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            // ignore
        }
        discoveryListener = null
        onHostsUpdated(emptyList())
        onSyncStatus("Stopped searching")
    }

    fun performSyncWithHost(hostInfo: NsdServiceInfo) {
        onSyncStatus("Connecting to ${hostInfo.host?.hostAddress}...")
        thread {
            try {
                val ip = hostInfo.host?.hostAddress ?: throw Exception("Host address unresolved")
                val port = hostInfo.port
                val url = URL("http://$ip:$port/sync")

                // Retrieve local data
                val localBackup = getLocalBackup(context)
                val jsonPayload = localBackup.toJson()

                // POST to host
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { out ->
                    out.write(jsonPayload.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseJson = conn.inputStream.bufferedReader().readText()
                    val mergedBackup = parseJson<BackupFile>(responseJson)

                    // Overwrite local data with merged cache returned by host
                    restore(context, mergedBackup, restoreSettings = true, restoreDataStore = true)

                    // Update TV launcher and widget
                    val cacheKey = "local_calendar_tasks_cache"
                    val cache = CloudStreamApp.getKey<CalendarTaskCache>(cacheKey) ?: CalendarTaskCache()
                    CalendarTvChannelHelper.syncToTvLauncher(context, cache)
                    updateWidget(context)
                    onSyncCompleted()
                    onSyncStatus("Sync successful!")
                } else {
                    onSyncStatus("Sync failed: HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync transaction error: ${e.message}", e)
                onSyncStatus("Sync Error: ${e.message}")
            }
        }
    }

    // --- Helpers ---

    private fun mergeBackups(local: BackupFile, remote: BackupFile): BackupFile {
        val mergedDatastore = mergeBackupVars(local.datastore, remote.datastore)
        val mergedSettings = mergeBackupVars(local.settings, remote.settings)
        return BackupFile(mergedDatastore, mergedSettings)
    }

    private fun mergeBackupVars(local: BackupVars, remote: BackupVars): BackupVars {
        val mergedBool = (local.bool.orEmpty() + remote.bool.orEmpty())
        val mergedInt = (local.int.orEmpty() + remote.int.orEmpty())
        val mergedFloat = (local.float.orEmpty() + remote.float.orEmpty())
        val mergedLong = (local.long.orEmpty() + remote.long.orEmpty())
        val mergedStringSet = (local.stringSet.orEmpty() + remote.stringSet.orEmpty())

        val mergedString = mutableMapOf<String, String>()
        val allKeys = local.string.orEmpty().keys + remote.string.orEmpty().keys
        for (key in allKeys) {
            val valLocal = local.string.orEmpty()[key]
            val valRemote = remote.string.orEmpty()[key]
            if (valLocal != null && valRemote != null) {
                if (valLocal == valRemote) {
                    mergedString[key] = valLocal
                } else if (valLocal.trim().startsWith("[") && valRemote.trim().startsWith("[")) {
                    try {
                        val listLocal = parseJson<List<Any>>(valLocal)
                        val listRemote = parseJson<List<Any>>(valRemote)
                        val mergedList = (listLocal + listRemote).distinctBy { it.toString() }
                        mergedString[key] = mergedList.toJson()
                    } catch (e: Exception) {
                        mergedString[key] = valRemote
                    }
                } else {
                    mergedString[key] = valRemote
                }
            } else {
                mergedString[key] = valRemote ?: valLocal!!
            }
        }

        return BackupVars(
            bool = mergedBool,
            int = mergedInt,
            string = mergedString,
            float = mergedFloat,
            long = mergedLong,
            stringSet = mergedStringSet
        )
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

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service registered: $nsdServiceInfo")
            onSyncStatus("Hosting Sync (Discovery Active)")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Registration failed: $errorCode")
            onSyncStatus("Registration Failed")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            Log.d(TAG, "Service unregistered")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Unregistration failed: $errorCode")
        }
    }
}
