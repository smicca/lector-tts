package io.github.smicca.lector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechNormalizerTest {

    @Test
    fun expandsAbbreviatedCitations() {
        assertEquals("First Corinthians chapter fifteen, verses three to eight", SpeechNormalizer.normalize("1 Cor 15:3-8"))
        assertEquals("Matthew chapter sixteen, verse eighteen", SpeechNormalizer.normalize("Mt 16:18"))
        assertEquals("Psalm twenty-two", SpeechNormalizer.normalize("Ps 22"))
        assertEquals("John chapter six, verse fifty-three", SpeechNormalizer.normalize("Jn 6:53"))
    }

    @Test
    fun expandsCatechismAndChurchDocuments() {
        assertEquals("Catechism paragraph nineteen ninety-six", SpeechNormalizer.normalize("CCC 1996"))
        assertEquals("Catechism paragraphs eighty to eighty-three", SpeechNormalizer.normalize("CCC 80-83"))
        assertEquals("Dei Verbum, section nine", SpeechNormalizer.normalize("DV 9"))
    }

    @Test
    fun leavesOrdinaryProseAloneExceptDigits() {
        assertEquals("The total is five and Job was patient.", SpeechNormalizer.normalize("The total is 5 and Job was patient."))
    }

    @Test
    fun testPassageContainsAllSentences() {
        val sentences = TestPassage.getNormalizedSentences()
        assertTrue("Should have at least 15 sentences", sentences.size >= 15)
        for (hw in TestPassage.HARD_WORDS) {
            val normalizedTarget = SpeechNormalizer.normalize(hw)
            val found = sentences.any { it.contains(normalizedTarget, ignoreCase = true) }
            assertTrue("Should contain hard word/phrase: $hw (normalized: $normalizedTarget)", found)
        }
    }
}
