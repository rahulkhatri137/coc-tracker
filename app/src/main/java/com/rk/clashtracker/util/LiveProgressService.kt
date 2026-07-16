package com.rk.clashtracker.util

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.rk.clashtracker.R
import com.rk.clashtracker.data.ClashDatabase
import com.rk.clashtracker.data.UpgradeEntity
import com.rk.clashtracker.data.formatSecondsToDuration
import kotlinx.coroutines.*

class LiveProgressService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 9999
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        val initialNotification = createServiceNotification(0)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (action == "START_TRACKING" || action == "REFRESH" || action == "NOTIFICATION_DISMISSED") {
            startTrackingLoop()
        } else if (action == "STOP_TRACKING") {
            val upgradeId = intent.getIntExtra("upgrade_id", -1)
            if (upgradeId != -1) {
                cancelNotificationForUpgrade(upgradeId)
            }
            startTrackingLoop()
        }
        
        return START_STICKY
    }

    private fun startTrackingLoop() {
        updateJob?.cancel()

        updateJob = serviceScope.launch {
            val db = ClashDatabase.getDatabase(applicationContext)
            val dao = db.clashDao()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            while (isActive) {
                val liveUpgrades = dao.getLiveTrackingUpgrades()

                if (liveUpgrades.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                    break
                }

                liveUpgrades.forEach { upgrade ->
                    val remaining = upgrade.remainingSeconds
                    if (remaining <= 0) {
                        UpgradeScheduler.showNotification(
                            applicationContext,
                            notificationManager,
                            upgrade.id,
                            upgrade.accountTag,
                            upgrade.structureName,
                            upgrade.targetLevel ?: -1
                        )
                        dao.updateUpgrade(
                            upgrade.copy(
                                isCompleted = true,
                                isLiveTracking = false,
                                notificationTriggered = true
                            )
                        )
                        cancelNotificationForUpgrade(upgrade.id)
                    }
                }

                val currentLiveUpgrades = dao.getLiveTrackingUpgrades()
                updateNotifications(currentLiveUpgrades, notificationManager)

                if (currentLiveUpgrades.isEmpty()) {
                    break
                }

                // Dynamically adjust tick rate based on remaining time of the closest upgrade to optimize battery/memory
                val minRemaining = currentLiveUpgrades.minOfOrNull { it.remainingSeconds } ?: 0L
                val delayMs = when {
                    minRemaining < 60 -> 5000L
                    minRemaining < 3600 -> 30000L
                    minRemaining < 43200 -> 300000L
                    else -> 900000L
                }
                delay(delayMs)
            }
        }
    }

    private fun updateNotifications(liveUpgrades: List<UpgradeEntity>, notificationManager: NotificationManager) {
        if (liveUpgrades.isEmpty()) {
            stopForeground(true)
            stopSelf()
            return
        }

        val firstUpgrade = liveUpgrades[0]
        val firstNotification = buildProgressNotification(firstUpgrade)
        val firstNotificationId = firstUpgrade.id + 10000

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(firstNotificationId, firstNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(firstNotificationId, firstNotification)
            }
            notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for (i in 1 until liveUpgrades.size) {
            val upgrade = liveUpgrades[i]
            val notification = buildProgressNotification(upgrade)
            try {
                notificationManager.notify(upgrade.id + 10000, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createServiceNotification(activeCount: Int): Notification {
        val title = "🔨 Clash Upgrade Tracker"
        val text = "Service active. Starting tracking..."
        val appIconLarge = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, UpgradeScheduler.LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIconLarge)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun cancelNotificationForUpgrade(upgradeId: Int) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(upgradeId + 10000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildProgressNotification(upgrade: UpgradeEntity): Notification {
        val progressPercent = (upgrade.progress * 100).toInt()
        val remainingStr = formatSecondsToDuration(upgrade.remainingSeconds)

        val levelStr = if (upgrade.targetLevel != null && upgrade.targetLevel > 0) " (Lvl ${upgrade.targetLevel})" else ""
        val title = "🔨 Upgrading ${upgrade.structureName}$levelStr"
        val text = "Progress: $progressPercent% | $remainingStr left"

        val appIconLarge = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val deleteIntent = Intent(this, LiveProgressService::class.java).apply {
            action = "NOTIFICATION_DISMISSED"
            putExtra("upgrade_id", upgrade.id)
        }
        val deletePendingIntent = PendingIntent.getService(
            this,
            upgrade.id + 20000,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, UpgradeScheduler.LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIconLarge)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, false)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(upgrade.endTime)
            .setDeleteIntent(deletePendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
