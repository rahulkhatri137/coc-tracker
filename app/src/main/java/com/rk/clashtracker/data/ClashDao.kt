package com.rk.clashtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClashDao {
    // --- Accounts ---
    @Query("SELECT * FROM accounts ORDER BY lastUpdated DESC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE tag = :tag LIMIT 1")
    suspend fun getAccountByTag(tag: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE tag = :tag")
    suspend fun deleteAccountByTag(tag: String)

    // --- Upgrades ---
    @Query("SELECT * FROM upgrades ORDER BY startTime + (durationSeconds * 1000) ASC")
    fun getAllUpgradesFlow(): Flow<List<UpgradeEntity>>

    @Query("SELECT * FROM upgrades WHERE accountTag = :accountTag ORDER BY startTime + (durationSeconds * 1000) ASC")
    fun getUpgradesForAccountFlow(accountTag: String): Flow<List<UpgradeEntity>>

    @Query("SELECT * FROM upgrades WHERE accountTag = :accountTag")
    suspend fun getUpgradesForAccount(accountTag: String): List<UpgradeEntity>

    @Query("SELECT * FROM upgrades WHERE isCompleted = 0")
    suspend fun getActiveUpgrades(): List<UpgradeEntity>

    @Query("SELECT * FROM upgrades WHERE isCompleted = 0 AND isLiveTracking = 1")
    suspend fun getLiveTrackingUpgrades(): List<UpgradeEntity>

    @Query("SELECT * FROM upgrades WHERE isCompleted = 0 AND isLiveTracking = 1")
    fun getLiveTrackingUpgradesFlow(): Flow<List<UpgradeEntity>>

    @Query("SELECT * FROM upgrades WHERE id = :id LIMIT 1")
    suspend fun getUpgradeById(id: Int): UpgradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpgrade(upgrade: UpgradeEntity): Long

    @Update
    suspend fun updateUpgrade(upgrade: UpgradeEntity)

    @Query("DELETE FROM upgrades WHERE id = :id")
    suspend fun deleteUpgradeById(id: Int)

    @Query("DELETE FROM upgrades WHERE accountTag = :accountTag")
    suspend fun deleteUpgradesForAccount(accountTag: String)
}
