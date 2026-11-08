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
