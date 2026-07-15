package com.rk.clashtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale
import java.util.regex.Pattern

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val tag: String, // e.g. "#P8Y9R2"
    val name: String,           // e.g. "Main Village"
    val townHallLevel: Int = 11, // e.g. 12
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "upgrades")
data class UpgradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountTag: String,       // foreign reference to AccountEntity.tag
    val structureName: String,     // e.g. "Archer Tower"
    val targetLevel: Int?,         // e.g. 13 (nullable if unknown)
    val startTime: Long,          // timestamp in ms when upgrade was started
    val durationSeconds: Long,     // total duration of the upgrade in seconds
    val isCompleted: Boolean = false,
    val notificationTriggered: Boolean = false,
    val isLiveTracking: Boolean = false
) {
    val endTime: Long
        get() = startTime + (durationSeconds * 1000)

    val remainingSeconds: Long
        get() {
            val now = System.currentTimeMillis()
            return ((endTime - now) / 1000).coerceAtLeast(0)
        }

    val progress: Float
        get() {
            if (durationSeconds <= 0) return 1f
            val elapsed = (System.currentTimeMillis() - startTime) / 1000f
            return (elapsed / durationSeconds).coerceIn(0f, 1f)
        }
}

/**
 * Utility to parse human-readable CoC duration strings (e.g., "5d 12h", "18h 30m", "45m") into seconds.
 */
fun parseDurationToSeconds(durationStr: String): Long {
    var seconds = 0L
    val cleanStr = durationStr.lowercase(Locale.getDefault()).trim()
    
    // Pattern to match values like "5d", "12h", "30m", "45s"
    val pattern = Pattern.compile("(\\d+)\\s*([dhms])")
    val matcher = pattern.matcher(cleanStr)
    var matchedAny = false
    
    while (matcher.find()) {
        matchedAny = true
        val value = matcher.group(1)?.toLongOrNull() ?: 0L
        val unit = matcher.group(2) ?: ""
        seconds += when (unit) {
            "d" -> value * 24 * 60 * 60
            "h" -> value * 60 * 60
            "m" -> value * 60
            "s" -> value
            else -> 0L
        }
    }
    
    if (!matchedAny) {
        // Fallback: try parsing raw number or simple words
        val number = cleanStr.filter { it.isDigit() }.toLongOrNull() ?: 0L
        return when {
            cleanStr.contains("day") || cleanStr.contains("d") -> number * 24 * 60 * 60
            cleanStr.contains("hour") || cleanStr.contains("h") -> number * 60 * 60
            cleanStr.contains("min") || cleanStr.contains("m") -> number * 60
            else -> number
        }
    }
    
    return seconds
}

/**
 * Formats seconds into CoC format e.g., "5d 12h", "18h 30m", or "Finished"
 */
fun formatSecondsToDuration(seconds: Long): String {
    if (seconds <= 0) return "Finished"
    
    val days = seconds / (24 * 60 * 60)
    var remaining = seconds % (24 * 60 * 60)
    val hours = remaining / (60 * 60)
    remaining %= (60 * 60)
    val minutes = remaining / 60
    val secs = remaining % 60
    
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
