package com.rk.clashtracker.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rk.clashtracker.data.*
import com.rk.clashtracker.util.JsonParser
import com.rk.clashtracker.util.UpgradeScheduler
import com.rk.clashtracker.util.LiveProgressService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ParseState {
    object Idle : ParseState
    object Loading : ParseState
    data class Success(val upgrades: List<JsonParser.ExtractedUpgrade>) : ParseState
    data class Error(val message: String) : ParseState
}

class ClashViewModel(
    application: Application,
    private val repository: ClashRepository
) : AndroidViewModel(application) {

    val accounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val upgrades: StateFlow<List<UpgradeEntity>> = repository.allUpgrades
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _parseState = MutableStateFlow<ParseState>(ParseState.Idle)
    val parseState: StateFlow<ParseState> = _parseState.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("clash_tracker_prefs", android.content.Context.MODE_PRIVATE)
    
    private val _defaultAccountTag = MutableStateFlow(sharedPrefs.getString("default_account_tag", "") ?: "")
    val defaultAccountTag: StateFlow<String> = _defaultAccountTag.asStateFlow()

    fun setDefaultAccountTag(tag: String) {
        sharedPrefs.edit().putString("default_account_tag", tag).apply()
        _defaultAccountTag.value = tag
    }

    init {
        // Run a sync-check to see if any upgrades completed while the app was closed
        viewModelScope.launch {
            try {
                UpgradeScheduler.checkAndNotifyCompletedUpgrades(application)
            } catch (e: Exception) {
                Log.e("ClashViewModel", "Error running startup completion checks", e)
            }
        }

        // Keep alarms and notifications synchronized with the database state
        viewModelScope.launch {
            try {
                repository.allUpgrades.collect { upgradesList ->
                    try {
                        val context = getApplication<Application>()
                        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        
                        // 1. Get previously known upgrade IDs
                        val sharedPrefs = context.getSharedPreferences("clash_tracker_prefs", android.content.Context.MODE_PRIVATE)
                        val knownIdsStr = sharedPrefs.getStringSet("known_upgrade_ids", emptySet()) ?: emptySet()
                        val knownIds = knownIdsStr.mapNotNull { it.toIntOrNull() }.toSet()
                        
                        // 2. Get current upgrade IDs from the database
                        val dbIds = upgradesList.map { it.id }.toSet()
                        
                        // 3. Any ID that was known but is no longer in the DB has been deleted!
                        val deletedIds = knownIds - dbIds
                        deletedIds.forEach { id ->
                            Log.d("ClashViewModel", "Sync: Upgrade #$id deleted, cancelling alarm and notifications")
                            UpgradeScheduler.cancelAlarm(context, id)
                            notificationManager.cancel(id)
                            notificationManager.cancel(id + 10000)
                        }
                        
                        // 4. Any ID that is in the DB but is completed, cancel its scheduled alarm and live notification
                        upgradesList.forEach { upgrade ->
                            if (upgrade.isCompleted) {
                                UpgradeScheduler.cancelAlarm(context, upgrade.id)
                                notificationManager.cancel(upgrade.id + 10000)
                            }
                        }
                        
                        // 5. Update the stored known IDs
                        val newKnownIdsStr = dbIds.map { it.toString() }.toSet()
                        sharedPrefs.edit().putStringSet("known_upgrade_ids", newKnownIdsStr).apply()
                    } catch (e: Exception) {
                        Log.e("ClashViewModel", "Error synchronizing alarms and notifications", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("ClashViewModel", "Error collecting upgrades for sync", e)
            }
        }
    }

    fun addAccount(tag: String, name: String, townHallLevel: Int) {
        viewModelScope.launch {
            val formattedTag = if (tag.startsWith("#")) tag.uppercase() else "#${tag.uppercase()}"
            val newAccount = AccountEntity(
                tag = formattedTag,
                name = name.ifEmpty { "Village $formattedTag" },
                townHallLevel = townHallLevel,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertAccount(newAccount)
        }
    }

    fun deleteAccount(tag: String) {
        viewModelScope.launch {
            repository.deleteAccountByTag(tag)
        }
    }

    fun addUpgrade(
        accountTag: String,
        structureName: String,
        targetLevel: Int?,
        durationSeconds: Long,
        villageType: String? = null,
        categoryType: String? = null
    ) {
        viewModelScope.launch {
            val mappingInfo = if (villageType == null || categoryType == null) {
                JsonParser.getMappingInfoByName(getApplication(), structureName)
            } else null

            val finalVillage = villageType ?: mappingInfo?.villageType ?: "Town Hall"
            val finalCategory = categoryType ?: mappingInfo?.categoryType ?: "Building"

            val upgrade = UpgradeEntity(
                accountTag = accountTag,
                structureName = structureName,
                targetLevel = targetLevel,
                startTime = System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                isCompleted = false,
                villageType = finalVillage,
                categoryType = finalCategory
            )
            repository.insertUpgrade(upgrade)
        }
    }

    fun toggleUpgradeCompletion(upgrade: UpgradeEntity) {
        viewModelScope.launch {
            val updated = upgrade.copy(
                isCompleted = !upgrade.isCompleted,
                notificationTriggered = if (!upgrade.isCompleted) true else upgrade.notificationTriggered,
                isLiveTracking = if (!upgrade.isCompleted) false else upgrade.isLiveTracking
            )
            repository.updateUpgrade(updated)
            if (updated.isCompleted) {
                // Cancel live notification
                val intent = Intent(getApplication(), LiveProgressService::class.java).apply {
                    action = "STOP_TRACKING"
                    putExtra("upgrade_id", upgrade.id)
                }
                startTrackingService(intent)
            }
        }
    }

    fun toggleLiveTracking(upgrade: UpgradeEntity) {
        viewModelScope.launch {
            val updated = upgrade.copy(isLiveTracking = !upgrade.isLiveTracking)
            repository.updateUpgrade(updated)
            
            // Send intent to start/stop tracking service
            val intent = Intent(getApplication(), LiveProgressService::class.java).apply {
                action = if (updated.isLiveTracking) "START_TRACKING" else "STOP_TRACKING"
                putExtra("upgrade_id", upgrade.id)
            }
            startTrackingService(intent)
        }
    }

    fun deleteUpgrade(id: Int) {
        viewModelScope.launch {
            val upgrade = repository.getUpgradeById(id)
            val wasLiveTracking = upgrade?.isLiveTracking == true
            
            repository.deleteUpgradeById(id)
            
            // Explicitly dismiss any live notification progress bar directly
            try {
                val notificationManager = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(id + 10000)
            } catch (e: Exception) {
                Log.e("ClashViewModel", "Error directly cancelling notification", e)
            }

            if (wasLiveTracking) {
                try {
                    val intent = Intent(getApplication(), LiveProgressService::class.java).apply {
                        action = "STOP_TRACKING"
                        putExtra("upgrade_id", id)
                    }
                    startTrackingService(intent)
                } catch (e: Exception) {
                    Log.e("ClashViewModel", "Error starting service for stop tracking", e)
                }
            }
        }
    }

    fun deleteMultipleUpgrades(upgrades: List<UpgradeEntity>) {
        viewModelScope.launch {
            upgrades.forEach { upgrade ->
                val id = upgrade.id
                val wasLiveTracking = upgrade.isLiveTracking
                
                repository.deleteUpgradeById(id)
                
                // Explicitly dismiss any live notification progress bar directly
                try {
                    val notificationManager = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    notificationManager.cancel(id + 10000)
                } catch (e: Exception) {
                    Log.e("ClashViewModel", "Error directly cancelling notification", e)
                }

                if (wasLiveTracking) {
                    try {
                        val intent = Intent(getApplication(), LiveProgressService::class.java).apply {
                            action = "STOP_TRACKING"
                            putExtra("upgrade_id", id)
                        }
                        startTrackingService(intent)
                    } catch (e: Exception) {
                        Log.e("ClashViewModel", "Error starting service for stop tracking", e)
                    }
                }
            }
        }
    }

    private fun startTrackingService(intent: Intent) {
        val app = getApplication<Application>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("ClashViewModel", "Failed to start live progress service", e)
        }
    }

    fun updateAccount(tag: String, newName: String, newTownHall: Int) {
        viewModelScope.launch {
            val existing = repository.getAccountByTag(tag)
            if (existing != null) {
                val updated = existing.copy(
                    name = newName.ifEmpty { existing.name },
                    townHallLevel = newTownHall,
                    lastUpdated = System.currentTimeMillis()
                )
                repository.insertAccount(updated)
            }
        }
    }

    fun updateUpgradeDetails(id: Int, newName: String, newRemainingTimeStr: String, targetLevel: Int? = null) {
        viewModelScope.launch {
            val existing = upgrades.value.find { it.id == id }
            if (existing != null) {
                val hasNewTimer = newRemainingTimeStr.isNotBlank()
                val seconds = if (hasNewTimer) parseDurationToSeconds(newRemainingTimeStr) else 0L
                val mappingInfo = JsonParser.getMappingInfoByName(getApplication(), newName.ifEmpty { existing.structureName })
                val updated = existing.copy(
                    structureName = newName.ifEmpty { existing.structureName },
                    targetLevel = targetLevel,
                    startTime = if (hasNewTimer && seconds > 0) System.currentTimeMillis() else existing.startTime,
                    durationSeconds = if (hasNewTimer && seconds > 0) seconds else existing.durationSeconds,
                    isCompleted = if (hasNewTimer && seconds <= 0) true else existing.isCompleted,
                    villageType = mappingInfo?.villageType ?: existing.villageType,
                    categoryType = mappingInfo?.categoryType ?: existing.categoryType
                )
                repository.updateUpgrade(updated)
            }
        }
    }

    fun parseManualJson(jsonText: String) {
        viewModelScope.launch {
            _parseState.value = ParseState.Loading
            try {
                val results = JsonParser.parseJsonUpgrades(getApplication<Application>().assets, jsonText)
                if (results.isEmpty()) {
                    _parseState.value = ParseState.Error("No valid upgrades found in JSON. Format: {\"data\":1000014,\"lvl\":2,\"timer\":25479}")
                } else {
                    _parseState.value = ParseState.Success(results)
                }
            } catch (e: Exception) {
                Log.e("ClashViewModel", "Manual JSON parsing failed", e)
                _parseState.value = ParseState.Error(e.message ?: "Invalid JSON format")
            }
        }
    }

    fun resetParseState() {
        _parseState.value = ParseState.Idle
    }

    fun importParsedUpgrades(accountTag: String, parsedUpgrades: List<JsonParser.ExtractedUpgrade>) {
        viewModelScope.launch {
            repository.clearUpgradesForAccount(accountTag)
            parsedUpgrades.forEach { parsed ->
                val seconds = parseDurationToSeconds(parsed.timeLeftString)
                if (seconds > 0) {
                    addUpgrade(
                        accountTag = accountTag,
                        structureName = parsed.structureName,
                        targetLevel = parsed.targetLevel,
                        durationSeconds = seconds,
                        villageType = parsed.villageType,
                        categoryType = parsed.categoryType
                    )
                }
            }
            _parseState.value = ParseState.Idle
        }
    }

    fun applyPotionBoost(accountTag: String, potionType: String) {
        viewModelScope.launch {
            val affectedCategoryTypes = when (potionType) {
                "Builder" -> listOf("Building", "Hero")
                "Research" -> listOf("Troop")
                "Pet" -> listOf("Pet")
                else -> emptyList()
            }
            val secondsToDeduct = when (potionType) {
                "Builder" -> 9L * 3600L // 9 hours
                "Research", "Pet" -> 23L * 3600L // 23 hours
                else -> 0L
            }

            if (affectedCategoryTypes.isEmpty() || secondsToDeduct == 0L) return@launch

            val currentUpgrades = upgrades.value.filter { upgrade ->
                !upgrade.isCompleted &&
                upgrade.villageType == "Town Hall" &&
                (accountTag == "All" || upgrade.accountTag == accountTag) &&
                upgrade.categoryType in affectedCategoryTypes
            }

            val context = getApplication<Application>()
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            for (upgrade in currentUpgrades) {
                val remainingMs = upgrade.endTime - System.currentTimeMillis()
                val remainingSeconds = (remainingMs / 1000).coerceAtLeast(0)
                
                if (remainingSeconds <= secondsToDeduct) {
                    val updated = upgrade.copy(
                        durationSeconds = upgrade.durationSeconds - remainingSeconds,
                        isCompleted = true,
                        isLiveTracking = false
                    )
                    repository.updateUpgrade(updated)

                    // Trigger finished notification
                    try {
                        UpgradeScheduler.showNotification(
                            context = context,
                            notificationManager = notificationManager,
                            id = upgrade.id,
                            accountTag = upgrade.accountTag,
                            structureName = upgrade.structureName,
                            targetLevel = upgrade.targetLevel ?: -1
                        )
                        if (upgrade.isLiveTracking) {
                            val intent = Intent(context, LiveProgressService::class.java).apply {
                                action = "STOP_TRACKING"
                                putExtra("upgrade_id", upgrade.id)
                            }
                            startTrackingService(intent)
                        }
                    } catch (e: Exception) {
                        Log.e("ClashViewModel", "Error triggering potion completed notification", e)
                    }
                } else {
                    val updated = upgrade.copy(
                        durationSeconds = upgrade.durationSeconds - secondsToDeduct,
                        isCompleted = false
                    )
                    repository.updateUpgrade(updated)
                }
            }
        }
    }

    fun applyHelperBoost(upgradeId: Int, hoursToDeduct: Int) {
        viewModelScope.launch {
            val upgrade = upgrades.value.find { it.id == upgradeId } ?: return@launch
            val secondsToDeduct = hoursToDeduct * 3600L
            val remainingMs = upgrade.endTime - System.currentTimeMillis()
            val remainingSeconds = (remainingMs / 1000).coerceAtLeast(0)

            val context = getApplication<Application>()
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            if (remainingSeconds <= secondsToDeduct) {
                val updated = upgrade.copy(
                    durationSeconds = upgrade.durationSeconds - remainingSeconds,
                    isCompleted = true,
                    isLiveTracking = false
                )
                repository.updateUpgrade(updated)

                // Trigger finished notification
                try {
                    UpgradeScheduler.showNotification(
                        context = context,
                        notificationManager = notificationManager,
                        id = upgrade.id,
                        accountTag = upgrade.accountTag,
                        structureName = upgrade.structureName,
                        targetLevel = upgrade.targetLevel ?: -1
                    )
                    if (upgrade.isLiveTracking) {
                        val intent = Intent(context, LiveProgressService::class.java).apply {
                            action = "STOP_TRACKING"
                            putExtra("upgrade_id", upgrade.id)
                        }
                        startTrackingService(intent)
                    }
                } catch (e: Exception) {
                    Log.e("ClashViewModel", "Error triggering helper completed notification", e)
                }
            } else {
                val updated = upgrade.copy(
                    durationSeconds = upgrade.durationSeconds - secondsToDeduct,
                    isCompleted = false
                )
                repository.updateUpgrade(updated)
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClashViewModel::class.java)) {
                val database = ClashDatabase.getDatabase(application)
                val repository = ClashRepository(application, database.clashDao())
                @Suppress("UNCHECKED_CAST")
                return ClashViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
