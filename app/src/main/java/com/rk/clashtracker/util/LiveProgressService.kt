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
import kotlinx.coroutines.flow.collectLatest

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

        if (action == "START_TRACKING" || action == "REFRESH" || action == "NOTIFICATION_DISMISSED" || action == "STOP_TRACKING") {
            val upgradeId = intent?.getIntExtra("upgrade_id", -1) ?: -1
            if (action == "STOP_TRACKING" && upgradeId != -1) {
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

            dao.getLiveTrackingUpgradesFlow().collectLatest { liveUpgrades ->
                if (liveUpgrades.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                    return@collectLatest
                }

                while (isActive) {
                    var listChanged = false
                    val updatedList = liveUpgrades.map { upgrade ->
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
                            val completedUpgrade = upgrade.copy(
                                isCompleted = true,
                                isLiveTracking = false,
                                notificationTriggered = true
                            )
                            // Perform database update in an IO block
                            launch(Dispatchers.IO) {
                                dao.updateUpgrade(completedUpgrade)
                            }
                            cancelNotificationForUpgrade(upgrade.id)
                            listChanged = true
                            completedUpgrade
                        } else {
                            upgrade
                        }
                    }

                    if (listChanged) {
                        break
                    }

                    val activeTracking = updatedList.filter { !it.isCompleted }
                    if (activeTracking.isEmpty()) {
                        break
                    }

                    updateNotifications(activeTracking, notificationManager)

                    val minRemaining = activeTracking.minOfOrNull { it.remainingSeconds } ?: 0L
                    val delayMs = when {
                        minRemaining < 60 -> 2000L      // fast tick close to finished
                        minRemaining < 3600 -> 10000L    // 10s tick under 1 hour
                        minRemaining < 43200 -> 30000L   // 30s tick under 12 hours
                        else -> 60000L                  // 1m tick for long ones
                    }
                    delay(delayMs)
                }
            }
        }
    }

    private fun updateNotifications(liveUpgrades: List<UpgradeEntity>, notificationManager: NotificationManager) {
        if (liveUpgrades.isEmpty()) {
            stopForeground(true)
            stopSelf()
            return
        }

        // Show all live upgrades as normal notifications with their respective progress
        liveUpgrades.forEach { upgrade ->
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
        val text = if (activeCount <= 0) {
            "Service active. Starting tracking..."
        } else if (activeCount == 1) {
            "Tracking 1 active upgrade live..."
        } else {
            "Tracking $activeCount active upgrades live..."
        }
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
