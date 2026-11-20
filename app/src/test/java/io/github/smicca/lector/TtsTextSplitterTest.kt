package io.github.smicca.lector

import io.github.smicca.lector.core.text.TtsTextSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextSplitterTest {

    @Test
    fun shortTextIsNotSplit() {
        val text = "This is a brief sentence."
        val chunks = TtsTextSplitter.split(text, limit = 160)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun splitsAtSentenceBoundaries() {
        val s1 = "This is the first complete sentence."
        val s2 = "Here is a second sentence that follows."
        val combined = "$s1 $s2"

        val chunks = TtsTextSplitter.split(combined, limit = 40)
        assertEquals(2, chunks.size)
        assertEquals(s1, chunks[0])
        assertEquals(s2, chunks[1])
    }

    @Test
    fun splitsAtPunctuationWhenSentencesAreTooLong() {
        val clause1 = "In the days of Abraham, the enigmatic priest-king offered bread,"
        val clause2 = "prefiguring the eternal priesthood fulfilled at the Last Supper."
        val combined = "$clause1 $clause2"

        val chunks = TtsTextSplitter.split(combined, limit = 70)
        assertTrue(chunks.size >= 2)
        for (chunk in chunks) {
            assertTrue("Chunk length (${chunk.length}) must be <= 70: '$chunk'", chunk.length <= 70)
        }
    }

    @Test
    fun handlesLongWordsGracefully() {
        val longWord = "A".repeat(80)
        val chunks = TtsTextSplitter.split(longWord, limit = 50)
        assertEquals(2, chunks.size)
        assertEquals(50, chunks[0].length)
        assertEquals(30, chunks[1].length)
    }
}
