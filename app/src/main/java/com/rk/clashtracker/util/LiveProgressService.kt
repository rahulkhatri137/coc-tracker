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

        if (action == "START_TRACKING" || action == "REFRESH") {
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
        if (updateJob != null && updateJob?.isActive == true) {
            serviceScope.launch {
                val db = ClashDatabase.getDatabase(applicationContext)
                val dao = db.clashDao()
                val liveUpgrades = dao.getLiveTrackingUpgrades()
            }
            return
        }

        updateJob = serviceScope.launch {
            val db = ClashDatabase.getDatabase(applicationContext)
            val dao = db.clashDao()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            while (isActive) {
                val liveUpgrades = dao.getLiveTrackingUpgrades()

                if (liveUpgrades.isEmpty()) {
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
                    } else {
                        showProgressNotification(notificationManager, upgrade)
                    }
                }

                delay(15000)
            }
        }
    }

    private fun cancelNotificationForUpgrade(upgradeId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(upgradeId + 10000)
    }

    private fun showProgressNotification(notificationManager: NotificationManager, upgrade: UpgradeEntity) {
        val progressPercent = (upgrade.progress * 100).toInt()
        val remainingStr = formatSecondsToDuration(upgrade.remainingSeconds)
        val notificationId = upgrade.id + 10000

        val levelStr = if (upgrade.targetLevel != null && upgrade.targetLevel > 0) " (Lvl ${upgrade.targetLevel})" else ""
        val title = "🔨 Upgrading ${upgrade.structureName}$levelStr"
        val text = "Progress: $progressPercent% | $remainingStr left"

        val appIconLarge = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(this, UpgradeScheduler.CHANNEL_ID)
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
            .build()

        notificationManager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
