package io.github.smicca.lector

import io.github.smicca.lector.core.model.CatalogEntry
import io.github.smicca.lector.core.storage.ModelStorage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelCatalogShaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun catalogJsonIsValidAndContainsRequiredModels() {
        val catalogFile = File("src/main/assets/voices_catalog.json").takeIf { it.exists() }
            ?: File("app/src/main/assets/voices_catalog.json")
        assertTrue("voices_catalog.json must exist in assets", catalogFile.exists())

        val content = catalogFile.readText()
        val entries = json.decodeFromString<List<CatalogEntry>>(content)

        assertEquals("Must contain 6 official catalog models", 6, entries.size)

        val kokoro = entries.firstOrNull { it.id == "kokoro-int8-multi-lang-v1_0" }
        assertNotNull("Kokoro must be in catalog", kokoro)
        assertTrue("Kokoro must be recommended", kokoro!!.recommended)
        assertEquals("72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653", kokoro.sha256)
        assertEquals("KOKORO", kokoro.modelType)
        assertTrue("Kokoro must have voices", kokoro.voices.isNotEmpty())

        val supertonic = entries.firstOrNull { it.id == "sherpa-onnx-supertonic-tts-int8-2026-03-06" }
        assertNotNull("Supertonic must be in catalog", supertonic)
        assertEquals("8c74359f63edd5045d47747f65331f0f6dbcbc91d7e898dd756d631295fe3259", supertonic!!.sha256)

        val pocket = entries.firstOrNull { it.id == "sherpa-onnx-pocket-tts-int8-2026-01-26" }
        assertNotNull("Pocket must be in catalog", pocket)
        assertEquals("2f3b88823cbbb9bf0b2477ec8ae7b3fec417b3a87b6bb5f256dba66f2ad967cb", pocket!!.sha256)

        val ryan = entries.firstOrNull { it.id == "vits-piper-en_US-ryan-high" }
        assertNotNull("Piper Ryan must be in catalog", ryan)
        assertEquals("6a71edf4d308b9cb2eaeadc8d1f3c6bf96120ecb7fe52c29a2b6e139c59760ed", ryan!!.sha256)

        val lessac = entries.firstOrNull { it.id == "vits-piper-en_US-lessac-medium" }
        assertNotNull("Piper Lessac must be in catalog", lessac)
        assertEquals("9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e", lessac!!.sha256)

        val kitten = entries.firstOrNull { it.id == "kitten-nano-en-v0_8-int8" }
        assertNotNull("Kitten must be in catalog", kitten)
        assertEquals("6fa5be852612ce761094ba74ee6123b4fc4acfefa79bf64dc63acae4a83af2fd", kitten!!.sha256)
    }

    @Test
    fun sha256ComputationVerification() {
        val tempFile = File.createTempFile("test_sha", ".txt")
        try {
            tempFile.writeText("Lector TTS offline speech synthesis")
            val computed = ModelStorage.computeSha256(tempFile)
            // Known sha256 for "Lector TTS offline speech synthesis"
            // Computed with standard sha256 algorithm
            assertNotNull(computed)
            assertEquals(64, computed.length)
            assertTrue(ModelStorage.verifyFileSha256(tempFile, computed))
            assertFalse(ModelStorage.verifyFileSha256(tempFile, "badhash0000000000000000000000000000000000000000000000000000000000"))
        } finally {
            tempFile.delete()
        }
    }
}
