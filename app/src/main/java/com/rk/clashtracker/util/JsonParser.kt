package com.rk.clashtracker.util

import android.content.res.AssetManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object JsonParser {
    private const val TAG = "JsonParser"

    data class ExtractedUpgrade(
        val structureName: String,
        val targetLevel: Int? = null,
        val timeLeftString: String,
        val villageType: String = "Town Hall",
        val categoryType: String = "Building"
    )

    data class MappingInfo(
        val name: String,
        val villageType: String,
        val categoryType: String
    )

    private var cachedDetailedMapping: Map<String, MappingInfo>? = null

    private fun loadDetailedMapping(assetManager: AssetManager): Map<String, MappingInfo> {
        cachedDetailedMapping?.let { return it }
        return try {
            val jsonString = assetManager.open("mapping.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val detailedMap = mutableMapOf<String, MappingInfo>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val groupKey = keys.next()
                val groupObj = root.optJSONObject(groupKey)
                if (groupObj != null) {
                    val (village, category) = when (groupKey) {
                        "bh_buildings" -> "Builder Hall" to "Building"
                        "bh_troops" -> "Builder Hall" to "Troop"
                        "heroes" -> "Town Hall" to "Hero"
                        "pets" -> "Town Hall" to "Pet"
                        "th_buildings" -> "Town Hall" to "Building"
                        "th_troops" -> "Town Hall" to "Troop"
                        else -> "Town Hall" to "Building"
                    }
                    val idKeys = groupObj.keys()
                    while (idKeys.hasNext()) {
                        val id = idKeys.next()
                        val rawName = groupObj.getString(id)
                        val name = rawName.replace("_", " ")
                        
                        // Treat Builder Hall heroes like Battle Machine or Battle Copter as Hero
                        val finalCategory = if (groupKey == "bh_buildings" && (id == "28000003" || id == "28000005")) {
                            "Hero"
                        } else {
                            category
                        }
                        
                        detailedMap[id] = MappingInfo(name, village, finalCategory)
                    }
                }
            }
            cachedDetailedMapping = detailedMap
            detailedMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading detailed mapping.json", e)
            emptyMap()
        }
    }

    fun getMappingInfoByName(context: android.content.Context, name: String): MappingInfo? {
        val detailed = loadDetailedMapping(context.assets)
        val cleanQuery = name.trim().lowercase()
        return detailed.values.find { it.name.lowercase() == cleanQuery }
    }

    fun getMappingInfoById(assetManager: AssetManager, id: String): MappingInfo? {
        return loadDetailedMapping(assetManager)[id]
    }

    fun parseJsonUpgrades(assetManager: AssetManager, jsonStr: String): List<ExtractedUpgrade> {
        val list = mutableListOf<ExtractedUpgrade>()
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return list

        val detailedMap = loadDetailedMapping(assetManager)

        try {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                extractFromArr(array, detailedMap, list)
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                extractFromObj(obj, detailedMap, list)
            } else {
                val objects = findJsonObjects(trimmed)
                for (jsonObjStr in objects) {
                    try {
                        val obj = JSONObject(jsonObjStr)
                        extractFromObj(obj, detailedMap, list)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            val objects = findJsonObjects(trimmed)
            for (jsonObjStr in objects) {
                try {
                    val obj = JSONObject(jsonObjStr)
                    extractFromObj(obj, detailedMap, list)
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
        return list
    }

    private fun extractFromObj(obj: JSONObject, detailedMap: Map<String, MappingInfo>, list: MutableList<ExtractedUpgrade>) {
        if (obj.has("data") && obj.has("timer")) {
            val dataVal = obj.get("data").toString()
            val timerSeconds = obj.optLong("timer", -1L)
            if (timerSeconds > 0 && dataVal.isNotEmpty()) {
                val timeLeftStr = formatSecondsToDuration(timerSeconds)
                val mappedInfo = detailedMap[dataVal]
                val friendlyName = if (mappedInfo != null && mappedInfo.name.isNotBlank()) {
                    mappedInfo.name
                } else {
                    "Building $dataVal"
                }
                val village = mappedInfo?.villageType ?: "Town Hall"
                val category = mappedInfo?.categoryType ?: "Building"
                val lvlVal = if (obj.has("lvl")) {
                    val l = obj.optInt("lvl", -1)
                    if (l != -1) l + 1 else null
                } else {
                    null
                }
                list.add(
                    ExtractedUpgrade(
                        structureName = friendlyName,
                        targetLevel = lvlVal,
                        timeLeftString = timeLeftStr,
                        villageType = village,
                        categoryType = category
                    )
                )
            }
        }

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            if (value is JSONObject) {
                extractFromObj(value, detailedMap, list)
            } else if (value is JSONArray) {
                extractFromArr(value, detailedMap, list)
            }
        }
    }

    private fun extractFromArr(arr: JSONArray, detailedMap: Map<String, MappingInfo>, list: MutableList<ExtractedUpgrade>) {
        for (i in 0 until arr.length()) {
            val value = arr.opt(i)
            if (value is JSONObject) {
                extractFromObj(value, detailedMap, list)
            } else if (value is JSONArray) {
                extractFromArr(value, detailedMap, list)
            }
        }
    }

    private var cachedMapping: Map<String, String>? = null

    private fun loadMapping(assetManager: AssetManager): Map<String, String> {
        cachedMapping?.let { return it }
        return try {
            val detailed = loadDetailedMapping(assetManager)
            val flatMap = detailed.mapValues { it.value.name }
            cachedMapping = flatMap
            flatMap
        } catch (e: Exception) {
            Log.e(TAG, "Error flattening mapping", e)
            emptyMap()
        }
    }

    private var cachedMappedNames: List<String>? = null

    fun getAutofillSuggestions(assetManager: AssetManager, input: String): List<String> {
        val names = cachedMappedNames ?: run {
            val loaded = loadMapping(assetManager).values
                .filter { it.isNotBlank() }
                .map { it.replace("_", " ") }
                .distinct()
                .sorted()
            cachedMappedNames = loaded
            loaded
        }
        if (input.isBlank()) return emptyList()
        val query = input.trim().lowercase()
        val prefixMatches = names.filter { it.lowercase().startsWith(query) }
        if (prefixMatches.size >= 3) {
            return prefixMatches.take(3)
        }
        val containMatches = names.filter { 
            val itemLower = it.lowercase()
            itemLower.contains(query) && !itemLower.startsWith(query) 
        }
        return (prefixMatches + containMatches).take(3)
    }

    private fun findJsonObjects(str: String): List<String> {
        val list = mutableListOf<String>()
        var depth = 0
        var startIdx = -1
        for (i in str.indices) {
            val char = str[i]
            if (char == '{') {
                if (depth == 0) {
                    startIdx = i
                }
                depth++
            } else if (char == '}') {
                if (depth > 0) {
                    depth--
                    if (depth == 0 && startIdx != -1) {
                        list.add(str.substring(startIdx, i + 1))
                    }
                }
            }
        }
        return list
    }

    private fun formatSecondsToDuration(seconds: Long): String {
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
}
