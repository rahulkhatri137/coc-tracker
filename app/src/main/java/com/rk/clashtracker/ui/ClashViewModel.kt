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

    init {
        // Run a sync-check to see if any upgrades completed while the app was closed
        viewModelScope.launch {
            try {
                UpgradeScheduler.checkAndNotifyCompletedUpgrades(application)
            } catch (e: Exception) {
                Log.e("ClashViewModel", "Error running startup completion checks", e)
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

    fun addUpgrade(accountTag: String, structureName: String, targetLevel: Int?, durationSeconds: Long) {
        viewModelScope.launch {
            val upgrade = UpgradeEntity(
                accountTag = accountTag,
                structureName = structureName,
                targetLevel = targetLevel,
                startTime = System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                isCompleted = false
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
            val existingList = repository.allUpgrades.stateIn(viewModelScope).value
            // Since allUpgrades is a flow, let's look through existing items or query
            // Actually, we can check the upgrades.value state flow:
            val existing = upgrades.value.find { it.id == id }
            if (existing != null) {
                val hasNewTimer = newRemainingTimeStr.isNotBlank()
                val seconds = if (hasNewTimer) parseDurationToSeconds(newRemainingTimeStr) else 0L
                val updated = existing.copy(
                    structureName = newName.ifEmpty { existing.structureName },
                    targetLevel = targetLevel,
                    startTime = if (hasNewTimer && seconds > 0) System.currentTimeMillis() else existing.startTime,
                    durationSeconds = if (hasNewTimer && seconds > 0) seconds else existing.durationSeconds,
                    isCompleted = if (hasNewTimer && seconds <= 0) true else existing.isCompleted
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
                        durationSeconds = seconds
                    )
                }
            }
            _parseState.value = ParseState.Idle
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
