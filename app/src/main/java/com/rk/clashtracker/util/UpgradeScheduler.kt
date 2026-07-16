package com.rk.clashtracker.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rk.clashtracker.MainActivity
import com.rk.clashtracker.R
import com.rk.clashtracker.data.ClashDatabase
import com.rk.clashtracker.data.UpgradeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UpgradeScheduler {
    private const val TAG = "UpgradeScheduler"
    const val CHANNEL_ID = "clash_upgrades_channel"
    const val LIVE_CHANNEL_ID = "clash_live_channel"
    private const val EXTRA_UPGRADE_ID = "extra_upgrade_id"
    private const val EXTRA_ACCOUNT_TAG = "extra_account_tag"
    private const val EXTRA_STRUCTURE_NAME = "extra_structure_name"
    private const val EXTRA_TARGET_LEVEL = "extra_target_level"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val name = "Clash of Clans Upgrades"
            val descriptionText = "Notifications for completed upgrades"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)

            val liveChannelName = "Live Progress Tracker"
            val liveChannelDesc = "Real-time countdown for in-progress upgrades"
            val liveImportance = NotificationManager.IMPORTANCE_LOW
            val liveChannel = NotificationChannel(LIVE_CHANNEL_ID, liveChannelName, liveImportance).apply {
                description = liveChannelDesc
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(liveChannel)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAlarm(context: Context, upgrade: UpgradeEntity) {
        if (upgrade.isCompleted) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, UpgradeCompletedReceiver::class.java).apply {
            putExtra(EXTRA_UPGRADE_ID, upgrade.id)
            putExtra(EXTRA_ACCOUNT_TAG, upgrade.accountTag)
            putExtra(EXTRA_STRUCTURE_NAME, upgrade.structureName)
            putExtra(EXTRA_TARGET_LEVEL, upgrade.targetLevel ?: -1)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            upgrade.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = upgrade.endTime
        val now = System.currentTimeMillis()

        if (triggerAtMillis <= now) {
            Log.d(TAG, "Upgrade #${upgrade.id} is already completed. Triggering immediate notification.")
            triggerImmediateNotification(context, upgrade)
            return
        }

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            try {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "Scheduled exact AlarmClock for upgrade #${upgrade.id} at $triggerAtMillis (in ${(triggerAtMillis - now) / 1000} seconds)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule exact AlarmClock, trying exact fallback: ${e.message}")
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed to schedule exact alarm fallback, trying standard set: ${ex.message}")
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            }
        } else {
            Log.d(TAG, "Exact alarm permission not granted, scheduling standard alarm as fallback.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    fun cancelAlarm(context: Context, upgradeId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, UpgradeCompletedReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            upgradeId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for upgrade #$upgradeId")
    }

    private fun triggerImmediateNotification(context: Context, upgrade: UpgradeEntity) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        showNotification(context, notificationManager, upgrade.id, upgrade.accountTag, upgrade.structureName, upgrade.targetLevel ?: -1)
        
        CoroutineScope(Dispatchers.IO).launch {
            val db = ClashDatabase.getDatabase(context)
            val dao = db.clashDao()
            val existing = dao.getUpgradeById(upgrade.id)
            if (existing != null && !existing.isCompleted) {
                dao.updateUpgrade(existing.copy(isCompleted = true, notificationTriggered = true))
            }
        }
    }

    fun showNotification(
        context: Context,
        notificationManager: NotificationManager,
        id: Int,
        accountTag: String,
        structureName: String,
        targetLevel: Int
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val levelStr = if (targetLevel > 0) " to Level $targetLevel" else ""
        val title = "Upgrade Finished! 🔨"
        val text = "[$accountTag] $structureName upgrade$levelStr is complete!"

        val appIconLarge = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIconLarge)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    /**
     * Fallback checking on startup or screen load to ensure completed upgrades are marked and shown.
     */
    fun checkAndNotifyCompletedUpgrades(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = ClashDatabase.getDatabase(context)
            val dao = db.clashDao()
            val activeUpgrades = dao.getActiveUpgrades()
            val now = System.currentTimeMillis()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            activeUpgrades.forEach { upgrade ->
                if (upgrade.endTime <= now) {
                    Log.d(TAG, "Discovered completed upgrade in DB check: ${upgrade.structureName}")
                    showNotification(
                        context,
                        notificationManager,
                        upgrade.id,
                        upgrade.accountTag,
                        upgrade.structureName,
                        upgrade.targetLevel ?: -1
                    )
                    dao.updateUpgrade(upgrade.copy(isCompleted = true, notificationTriggered = true))
                } else {
                    scheduleAlarm(context, upgrade)
                }
            }
        }
    }
}

class UpgradeCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val id = intent.getIntExtra("extra_upgrade_id", -1)
        val accountTag = intent.getStringExtra("extra_account_tag") ?: ""
        val structureName = intent.getStringExtra("extra_structure_name") ?: ""
        val targetLevel = intent.getIntExtra("extra_target_level", -1)

        if (id == -1 || structureName.isEmpty()) return

        Log.d("UpgradeCompletedReceiver", "Alarm received for upgrade #$id ($structureName)")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        UpgradeScheduler.showNotification(context, notificationManager, id, accountTag, structureName, targetLevel)

        CoroutineScope(Dispatchers.IO).launch {
            val db = ClashDatabase.getDatabase(context)
            val dao = db.clashDao()
            val existing = dao.getUpgradeById(id)
            if (existing != null) {
                dao.updateUpgrade(existing.copy(isCompleted = true, notificationTriggered = true))
            }
        }
    }
}
