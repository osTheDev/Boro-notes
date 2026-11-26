package com.boro.notes.sketch

import kotlinx.serialization.json.Json

object SketchCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(shapes: List<Shape>): String = json.encodeToString(shapes)

    fun decode(text: String): List<Shape> =
        runCatching { json.decodeFromString<List<Shape>>(text) }.getOrDefault(emptyList())
}
