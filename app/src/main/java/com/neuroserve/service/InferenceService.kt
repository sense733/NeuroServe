package com.neuroserve.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.neuroserve.data.SettingsRepository
import com.neuroserve.server.ApiServer
import com.neuroserve.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** 推理前台服务 - 托管 Ktor 服务器和 Nexa 引擎，使用 Foreground Service 保活 */
@AndroidEntryPoint
class InferenceService : LifecycleService() {

    @Inject
    lateinit var apiServer: ApiServer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "neuroserve_inference"
        private const val ACTION_STOP = "com.neuroserve.service.ACTION_STOP"
        private const val TAG = "InferenceService"

        fun start(context: Context) {
            val intent = Intent(context, InferenceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, InferenceService::class.java).apply {
                action = ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())

        lifecycleScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.settingsFlow.first()

            if (settings.keepCpuAwake && wakeLock?.isHeld != true) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NeuroServe:InferenceLock")
                @Suppress("WakelockTimeout")
                wakeLock?.acquire()
            }

            if (!apiServer.isRunning()) {
                apiServer.start()
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Stop server synchronously to ensure it shuts down before service is destroyed
        runBlocking(Dispatchers.IO) {
            try {
                apiServer.stop()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error stopping apiServer", e)
            }
        }
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "推理服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI 推理服务运行状态"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, InferenceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NeuroServe is Running")
            .setContentText("Local API Server Active: http://127.0.0.1:8000")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
