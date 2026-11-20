package io.github.smicca.lector.core.model

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

object VoiceModelCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCatalog(context: Context): List<CatalogEntry> {
        return try {
            context.assets.open("voices_catalog.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    json.decodeFromString<List<CatalogEntry>>(reader.readText())
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
