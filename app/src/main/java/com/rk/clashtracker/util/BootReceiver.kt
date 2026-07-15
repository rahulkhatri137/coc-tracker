package com.rk.clashtracker.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rk.clashtracker.data.ClashDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted. Recovering upgrade scheduler and active alarms.")
            val appContext = context.applicationContext
            try {
                UpgradeScheduler.checkAndNotifyCompletedUpgrades(appContext)
                
                // Recover LiveProgressService if there are any live tracking upgrades
                CoroutineScope(Dispatchers.IO).launch {
                    val db = ClashDatabase.getDatabase(appContext)
                    val dao = db.clashDao()
                    val liveUpgrades = dao.getLiveTrackingUpgrades()
                    if (liveUpgrades.isNotEmpty()) {
                        Log.d("BootReceiver", "Resuming LiveProgressService for ${liveUpgrades.size} live upgrades.")
                        val serviceIntent = Intent(appContext, LiveProgressService::class.java).apply {
                            action = "START_TRACKING"
                        }
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                appContext.startForegroundService(serviceIntent)
                            } else {
                                appContext.startService(serviceIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("BootReceiver", "Failed to auto-restart LiveProgressService on boot", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to recover alarms on boot", e)
            }
        }
    }
}
