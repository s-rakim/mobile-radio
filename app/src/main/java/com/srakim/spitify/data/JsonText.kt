package com.srakim.spitify.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Small helpers for APIs whose JSON is loose: a field can be a string, a number, a list or missing.

internal fun JsonObject.text(key: String): String? = this[key].firstText()

internal fun JsonObject.texts(key: String): List<String> = when (val v = this[key]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(v.contentOrNull)
    else -> emptyList()
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

private fun JsonElement?.firstText(): String? = when (this) {
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
    is JsonArray -> firstOrNull().firstText()
    else -> null
}

/** "4:29", "1:02:03" or "269.4" to whole seconds. */
internal fun parseDuration(raw: String?): Int {
    if (raw.isNullOrBlank()) return 0
    if (':' in raw) return raw.split(':').fold(0) { acc, part -> acc * 60 + (part.trim().toDoubleOrNull()?.toInt() ?: 0) }
    return raw.trim().toDoubleOrNull()?.toInt() ?: 0
}

internal fun String.stripHtml(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .trim()

/** Maps loose tags ("hip_hop", "electronica") onto the genres the mixes use. */
internal fun genreFromTags(tags: Collection<String>): String? {
    val t = tags.map { it.lowercase().replace('_', ' ').replace('-', ' ').trim() }.toSet()
    fun has(vararg words: String) = words.any { it in t }
    return when {
        has("hip hop", "hiphop", "rap") -> "Hip-Hop/Rap"
        has("lofi", "lo fi") -> "Lo-Fi"
        has("dubstep") -> "Dubstep"
        has("trap") -> "Trap"
        has("house", "deep house") -> "House"
        has("techno") -> "Techno"
        has("electronic", "electronica", "edm", "idm", "downtempo", "chillout") -> "Electronic"
        has("ambient") -> "Ambient"
        has("jazz") -> "Jazz"
        has("rock", "punk", "indie rock", "metal") -> "Rock"
        has("pop") -> "Pop"
        has("soul", "rnb", "r&b") -> "R&B/Soul"
        has("funk") -> "Funk"
        has("reggae", "dub") -> "Reggae"
        has("latin") -> "Latin"
        has("acoustic", "folk") -> "Acoustic"
        has("classical", "orchestral", "piano") -> "Classical"
        has("soundtrack", "cinematic", "film") -> "Soundtrack"
        has("experimental") -> "Experimental"
        has("alternative") -> "Alternative"
        else -> null
    }
}
