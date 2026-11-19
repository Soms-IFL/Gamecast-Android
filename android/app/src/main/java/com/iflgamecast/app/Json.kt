package com.iflgamecast.app

import org.json.JSONArray
import org.json.JSONObject

// Null-safe accessors over org.json: a missing key, a JSON null and a wrong type all fall back to a default instead of throwing.
fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.sn(k: String): String? = if (isNull(k)) null else optString(k, "").ifEmpty { null }
fun JSONObject.i(k: String, d: Int = 0): Int = if (isNull(k)) d else optInt(k, d)
fun JSONObject.l(k: String, d: Long = 0L): Long = if (isNull(k)) d else optLong(k, d)
fun JSONObject.d(k: String, d: Double = 0.0): Double = if (isNull(k)) d else optDouble(k, d)
fun JSONObject.b(k: String): Boolean = !isNull(k) && optBoolean(k, false)
fun JSONObject.o(k: String): JSONObject? = optJSONObject(k)
fun JSONObject.a(k: String): JSONArray = optJSONArray(k) ?: JSONArray()
fun JSONObject.hasValue(k: String): Boolean = has(k) && !isNull(k)

inline fun <T> JSONArray.mapObjs(f: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (n in 0 until length()) optJSONObject(n)?.let { out.add(f(it)) }
    return out
}

fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it, "") }
fun JSONArray.ints(): List<Int> = (0 until length()).map { optInt(it, 0) }
fun JSONObject.keyList(): List<String> { val out = ArrayList<String>(); val it = keys(); while (it.hasNext()) out.add(it.next()); return out }

/** Like optJSONObject but never null: composables use this so a missing field can't crash the UI while composing. */
fun JSONObject.obj(k: String): JSONObject = optJSONObject(k) ?: JSONObject()
