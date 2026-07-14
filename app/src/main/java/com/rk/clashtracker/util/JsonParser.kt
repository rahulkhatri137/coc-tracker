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
        val timeLeftString: String
    )

    fun parseJsonUpgrades(assetManager: AssetManager, jsonStr: String): List<ExtractedUpgrade> {
        val list = mutableListOf<ExtractedUpgrade>()
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return list

        // Load mappings from mapping.json
        val mapping = loadMapping(assetManager)

        try {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                extractFromArr(array, mapping, list)
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                extractFromObj(obj, mapping, list)
            } else {
                // Try searching for JSON objects via brace-matching if it starts with arbitrary fields or text
                val objects = findJsonObjects(trimmed)
                for (jsonObjStr in objects) {
                    try {
                        val obj = JSONObject(jsonObjStr)
                        extractFromObj(obj, mapping, list)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: brace matching
            val objects = findJsonObjects(trimmed)
            for (jsonObjStr in objects) {
                try {
                    val obj = JSONObject(jsonObjStr)
                    extractFromObj(obj, mapping, list)
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
        return list
    }

    private fun extractFromObj(obj: JSONObject, mapping: Map<String, String>, list: MutableList<ExtractedUpgrade>) {
        if (obj.has("data") && obj.has("timer")) {
            val dataVal = obj.get("data").toString()
            val timerSeconds = obj.optLong("timer", -1L)
            if (timerSeconds > 0 && dataVal.isNotEmpty()) {
                val timeLeftStr = formatSecondsToDuration(timerSeconds)
                val mappedName = mapping[dataVal]
                val friendlyName = if (mappedName != null && mappedName.isNotBlank()) {
                    mappedName.replace("_", " ")
                } else if (mappedName != null && mappedName.isBlank()) {
                    "Building $dataVal"
                } else {
                    dataVal
                }
                list.add(
                    ExtractedUpgrade(
                        structureName = friendlyName,
                        targetLevel = null, // ignore lvl values as requested by user
                        timeLeftString = timeLeftStr
                    )
                )
            }
        }

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            if (value is JSONObject) {
                extractFromObj(value, mapping, list)
            } else if (value is JSONArray) {
                extractFromArr(value, mapping, list)
            }
        }
    }

    private fun extractFromArr(arr: JSONArray, mapping: Map<String, String>, list: MutableList<ExtractedUpgrade>) {
        for (i in 0 until arr.length()) {
            val value = arr.opt(i)
            if (value is JSONObject) {
                extractFromObj(value, mapping, list)
            } else if (value is JSONArray) {
                extractFromArr(value, mapping, list)
            }
        }
    }

    private fun loadMapping(assetManager: AssetManager): Map<String, String> {
        return try {
            val jsonString = assetManager.open("mapping.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
            map
        } catch (e: Exception) {
            Log.e(TAG, "Error loading mapping.json from assets", e)
            emptyMap()
        }
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
