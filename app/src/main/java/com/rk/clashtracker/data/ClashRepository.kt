package com.rk.clashtracker.data

import android.content.Context
import com.rk.clashtracker.util.UpgradeScheduler
import kotlinx.coroutines.flow.Flow

class ClashRepository(
    private val context: Context,
    private val clashDao: ClashDao
) {
    val allAccounts: Flow<List<AccountEntity>> = clashDao.getAllAccountsFlow()
    val allUpgrades: Flow<List<UpgradeEntity>> = clashDao.getAllUpgradesFlow()

    fun getUpgradesForAccount(accountTag: String): Flow<List<UpgradeEntity>> {
        return clashDao.getUpgradesForAccountFlow(accountTag)
    }

    suspend fun getAccountByTag(tag: String): AccountEntity? {
        return clashDao.getAccountByTag(tag)
    }

    suspend fun insertAccount(account: AccountEntity) {
        clashDao.insertAccount(account)
    }

    suspend fun deleteAccountByTag(tag: String) {
        // Cancel alarms for all upgrades of this account
        val dbUpgrades = clashDao.getUpgradesForAccountFlow(tag)
        // Since we are in suspend function, let's query the flow value once or get active list
        val active = clashDao.getActiveUpgrades().filter { it.accountTag == tag }
        active.forEach { upgrade ->
            UpgradeScheduler.cancelAlarm(context, upgrade.id)
        }
        clashDao.deleteUpgradesForAccount(tag)
        clashDao.deleteAccountByTag(tag)
    }

    suspend fun insertUpgrade(upgrade: UpgradeEntity): Int {
        val id = clashDao.insertUpgrade(upgrade).toInt()
        val savedUpgrade = upgrade.copy(id = id)
        // Schedule Alarm
        UpgradeScheduler.scheduleAlarm(context, savedUpgrade)
        return id
    }

    suspend fun updateUpgrade(upgrade: UpgradeEntity) {
        clashDao.updateUpgrade(upgrade)
        // Re-schedule alarm if not completed
        if (upgrade.isCompleted) {
            UpgradeScheduler.cancelAlarm(context, upgrade.id)
        } else {
            UpgradeScheduler.scheduleAlarm(context, upgrade)
        }
    }

    suspend fun getUpgradeById(id: Int): UpgradeEntity? {
        return clashDao.getUpgradeById(id)
    }

    suspend fun deleteUpgradeById(id: Int) {
        UpgradeScheduler.cancelAlarm(context, id)
        clashDao.deleteUpgradeById(id)
    }
}
