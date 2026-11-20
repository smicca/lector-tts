package io.github.smicca.lector.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import io.github.smicca.lector.R
import io.github.smicca.lector.core.model.CatalogEntry
import io.github.smicca.lector.core.model.DownloadState
import io.github.smicca.lector.core.model.VoiceModelCatalog
import io.github.smicca.lector.core.storage.ModelStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelDownloadService"
private const val CHANNEL_ID = "lector_model_downloads"
private const val NOTIFICATION_ID = 2001

class ModelDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra("modelId")
        if (modelId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val catalog = VoiceModelCatalog.loadCatalog(this)
        val entry = catalog.firstOrNull { it.id == modelId }
        if (entry == null) {
            Log.e(TAG, "Model ID not found in catalog: $modelId")
            _downloadState.value = DownloadState.Error("Model not found in catalog")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification("Starting download for ${entry.name}...", 0)

        downloadJob?.cancel()
        downloadJob = scope.launch {
            runDownload(entry)
        }

        return START_REDELIVER_INTENT
    }

    private suspend fun runDownload(entry: CatalogEntry) {
        val modelsDir = ModelStorage.getModelsDir(this)
        val cacheArchive = File(cacheDir, entry.archiveName)

        try {
            _downloadState.value = DownloadState.Downloading(0, 0, entry.archiveSizeBytes)
            updateNotification("Downloading ${entry.name}...", 0)

            var downloaded = 0L
            val url = URL(entry.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }

            val totalLength = if (connection.contentLengthLong > 0) connection.contentLengthLong else entry.archiveSizeBytes

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(cacheArchive).use { output ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    var lastReportTime = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 250) {
                            val percent = if (totalLength > 0) ((downloaded * 100) / totalLength).toInt().coerceIn(0, 100) else 0
                            _downloadState.value = DownloadState.Downloading(percent, downloaded, totalLength)
                            updateNotification("Downloading ${entry.name} ($percent%)...", percent)
                            lastReportTime = now
                        }
                    }
                }
            }

            // Verify SHA-256
            updateNotification("Verifying integrity...", -1)
            val valid = ModelStorage.verifyFileSha256(cacheArchive, entry.sha256)
            if (!valid) {
                cacheArchive.delete()
                val msg = "Checksum mismatch: download corrupted"
                Log.e(TAG, msg)
                _downloadState.value = DownloadState.Error(msg)
                updateNotification("Download failed: integrity check error", -1)
                return
            }

            // Extract
            _downloadState.value = DownloadState.Extracting(0)
            updateNotification("Extracting ${entry.name}...", 0)

            val destDir = File(modelsDir, entry.folderName)
            if (destDir.exists()) {
                destDir.deleteRecursively()
            }
            destDir.mkdirs()

            ModelStorage.extractArchive(cacheArchive, destDir) { percent ->
                _downloadState.value = DownloadState.Extracting(percent)
                updateNotification("Extracting ${entry.name} ($percent%)...", percent)
            }

            // Clean archive
            cacheArchive.delete()

            _downloadState.value = DownloadState.Completed
            updateNotification("${entry.name} ready to use", 100)
            Log.i(TAG, "Successfully downloaded and extracted ${entry.name} to ${destDir.absolutePath}")

            // Save as selected model
            getSharedPreferences("lector_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("selected_model_id", entry.id)
                .putString("selected_voice_id", entry.defaultVoiceId)
                .apply()

        } catch (t: Throwable) {
            Log.e(TAG, "Download failed for ${entry.name}: ${t.message}", t)
            cacheArchive.delete()
            _downloadState.value = DownloadState.Error(t.message ?: "Download failed")
            updateNotification("Download failed: ${t.message}", -1)
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of downloading speech synthesis models"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(text: String, progress: Int) {
        val notification = buildNotification(text, progress)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String, progress: Int) {
        val notification = buildNotification(text, progress)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Lector Voice Manager")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(progress in 0..99)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        fun startDownload(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                putExtra("modelId", modelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
