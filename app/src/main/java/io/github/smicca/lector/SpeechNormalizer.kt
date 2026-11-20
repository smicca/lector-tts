package io.github.smicca.lector

/**
 * Normalizes text for speech synthesis, expanding biblical citations, ordinals,
 * Catechism paragraphs, and Latin / theological abbreviations into readable English words.
 */
object SpeechNormalizer {
    // Case-sensitive on purpose: "Is 53:5" is Isaiah, "the total is 5" is not.
    private val bookNames: Map<String, String> = buildMap {
        fun add(name: String, vararg aliases: String) {
            put(name, name)
            aliases.forEach { put(it, name) }
        }
        add("Genesis", "Gen", "Gn"); add("Exodus", "Ex", "Exod"); add("Leviticus", "Lev", "Lv")
        add("Numbers", "Num", "Nm"); add("Deuteronomy", "Deut", "Dt"); add("Joshua", "Josh", "Jos")
        add("Judges", "Judg", "Jgs"); add("Ruth", "Ru"); add("1 Samuel", "1 Sam", "1 Sm"); add("2 Samuel", "2 Sam", "2 Sm")
        add("1 Kings", "1 Kgs", "1 Kg"); add("2 Kings", "2 Kgs", "2 Kg"); add("1 Chronicles", "1 Chr", "1 Chron")
        add("2 Chronicles", "2 Chr", "2 Chron"); add("Ezra", "Ezr"); add("Nehemiah", "Neh"); add("Tobit", "Tob", "Tb")
        add("Judith", "Jdt"); add("Esther", "Esth", "Est"); add("1 Maccabees", "1 Macc", "1 Mc"); add("2 Maccabees", "2 Macc", "2 Mc")
        add("Job", "Jb"); add("Psalm", "Psalms", "Ps", "Pss"); add("Proverbs", "Prov", "Prv"); add("Ecclesiastes", "Eccl", "Eccles", "Qoh")
        add("Song of Songs", "Song", "Sg", "Song of Solomon"); add("Wisdom", "Wis"); add("Sirach", "Sir", "Ecclus")
        add("Isaiah", "Isa", "Is"); add("Jeremiah", "Jer"); add("Lamentations", "Lam"); add("Baruch", "Bar")
        add("Ezekiel", "Ezek", "Ez"); add("Daniel", "Dan", "Dn"); add("Hosea", "Hos"); add("Joel", "Jl"); add("Amos", "Am")
        add("Obadiah", "Obad", "Ob"); add("Jonah", "Jon"); add("Micah", "Mic"); add("Nahum", "Nah"); add("Habakkuk", "Hab")
        add("Zephaniah", "Zeph"); add("Haggai", "Hag"); add("Zechariah", "Zech"); add("Malachi", "Mal")
        add("Matthew", "Matt", "Mt"); add("Mark", "Mk"); add("Luke", "Lk"); add("John", "Jn"); add("Acts", "Act")
        add("Romans", "Rom"); add("1 Corinthians", "1 Cor"); add("2 Corinthians", "2 Cor"); add("Galatians", "Gal")
        add("Ephesians", "Eph"); add("Philippians", "Phil"); add("Colossians", "Col"); add("1 Thessalonians", "1 Thess", "1 Thes")
        add("2 Thessalonians", "2 Thess", "2 Thes"); add("1 Timothy", "1 Tim", "1 Tm"); add("2 Timothy", "2 Tim", "2 Tm")
        add("Titus", "Ti"); add("Philemon", "Phlm", "Philem"); add("Hebrews", "Heb"); add("James", "Jas")
        add("1 Peter", "1 Pet", "1 Pt"); add("2 Peter", "2 Pet", "2 Pt"); add("1 John", "1 Jn"); add("2 John", "2 Jn")
        add("3 John", "3 Jn"); add("Jude", "Jud"); add("Revelation", "Rev", "Rv", "Apocalypse")
    }

    private val ordinals = mapOf("1" to "First", "2" to "Second", "3" to "Third")
    private const val DASH = "[-–—]"

    private val bookPattern: String = bookNames.keys.sortedByDescending { it.length }
        .joinToString("|") { name -> name.split(' ').joinToString("\\s") { Regex.escape(it) } }

    private val citation = Regex(
        "\\b($bookPattern)\\.?\\s+(\\d{1,3})(?::(\\d{1,3})(?:\\s?$DASH\\s?(\\d{1,3}|end))?|\\s?$DASH\\s?(\\d{1,3}))?\\b"
    )
    private val orderedBook = Regex("\\b([123])\\s+(Samuel|Kings|Chronicles|Maccabees|Corinthians|Thessalonians|Timothy|Peter|John)\\b")
    private val catechism = Regex("\\bCCC\\s*(?:§|para\\.?|paragraphs?)?\\s*(\\d+)(?:\\s?$DASH\\s?(\\d+))?")
    private val deiVerbum = Regex("\\bDV\\s+(\\d+)")
    private val circa = Regex("\\b(?:c\\.|ca\\.|circa)\\s?(\\d)")
    private val numberRange = Regex("(\\d)\\s?[–—]\\s?(\\d)")

    private val abbreviations = listOf(
        Regex("\\bcf\\.\\s?", RegexOption.IGNORE_CASE) to "compare ",
        Regex("\\be\\.g\\.,?\\s?", RegexOption.IGNORE_CASE) to "for example, ",
        Regex("\\bi\\.e\\.,?\\s?", RegexOption.IGNORE_CASE) to "that is, ",
        Regex("\\bvs\\.\\s?") to "versus ",
        Regex("\\bvv\\.\\s?") to "verses ",
        Regex("\\bch\\.\\s?") to "chapter ",
        Regex("\\bSt\\.\\s") to "Saint ",
        Regex("\\bSts\\.\\s") to "Saints ",
        Regex("\\betc\\.") to "and so on",
        Regex("\\bOT\\b") to "Old Testament",
        Regex("\\bNT\\b") to "New Testament",
        Regex("\\bRSV-2CE\\b") to "Revised Standard Version, Second Catholic Edition",
        Regex("\\s&\\s") to " and ",
        Regex("[▾▸•·]") to ", "
    )

    private val units = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    )
    private val tens = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    fun numberToWords(num: Long): String {
        if (num == 0L) return "zero"
        if (num < 0L) return "minus ${numberToWords(-num)}"
        return when {
            num < 20 -> units[num.toInt()]
            num < 100 -> {
                val t = tens[(num / 10).toInt()]
                val u = units[(num % 10).toInt()]
                if (u.isEmpty()) t else "$t-$u"
            }
            num < 1000 -> {
                val h = "${units[(num / 100).toInt()]} hundred"
                val rem = num % 100
                if (rem == 0L) h else "$h ${numberToWords(rem)}"
            }
            num in 1100..1999 && num % 100 != 0L -> {
                val hundreds = (num / 100).toInt()
                val rem = num % 100
                "${numberToWords(hundreds.toLong())} ${numberToWords(rem)}"
            }
            num < 1_000_000 -> {
                val th = "${numberToWords(num / 1000)} thousand"
                val rem = num % 1000
                if (rem == 0L) th else "$th ${numberToWords(rem)}"
            }
            else -> num.toString()
        }
    }

    fun normalize(text: String): String {
        var out = text
        out = catechism.replace(out) { m ->
            val start = numberToWords(m.groupValues[1].toLong())
            val endStr = m.groupValues[2]
            if (endStr.isEmpty()) "Catechism paragraph $start" else "Catechism paragraphs $start to ${numberToWords(endStr.toLong())}"
        }
        out = deiVerbum.replace(out) { "Dei Verbum, section ${numberToWords(it.groupValues[1].toLong())}" }
        out = citation.replace(out) { spokenCitation(it) }
        out = orderedBook.replace(out) { "${ordinals.getValue(it.groupValues[1])} ${it.groupValues[2]}" }
        out = circa.replace(out) { "around ${numberToWords(it.groupValues[1].toLong())}" }
        abbreviations.forEach { (pattern, spoken) -> out = pattern.replace(out, spoken) }
        out = numberRange.replace(out) { "${numberToWords(it.groupValues[1].toLong())} to ${numberToWords(it.groupValues[2].toLong())}" }
        // Parenthetical asides read better as a short pause than as silence the ear cannot place.
        out = out.replace(Regex("\\s*\\(([^)]*)\\)")) { ", ${it.groupValues[1]}," }

        // Clean quotation marks that break tokenizers
        listOf("\"", "“", "”", "‘", "’").forEach { quote -> out = out.replace(quote, "") }
        out = out.replace(Regex("(^|\\s)'"), "$1")
        out = out.replace(Regex("'([.,;:!?]|\\s|$)"), "$1")

        // Expand any remaining standalone digits into words
        out = out.replace(Regex("\\b\\d+\\b")) { m -> numberToWords(m.value.toLong()) }

        return out.replace(Regex(",\\s*([,.;:!?])"), "$1").replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun spokenCitation(m: MatchResult): String {
        val key = m.groupValues[1].replace(Regex("\\s+"), " ")
        val name = bookNames[key] ?: return m.value
        val spokenBook = name.replace(Regex("^([123]) ")) { "${ordinals.getValue(it.groupValues[1])} " }
        val chapter = numberToWords(m.groupValues[2].toLong())
        val verseStartStr = m.groupValues[3]
        val verseEndStr = m.groupValues[4]
        val chapterEndStr = m.groupValues[5]
        val chapterWord = if (name == "Psalm") "" else " chapter"
        return when {
            chapterEndStr.isNotEmpty() -> "$spokenBook${if (name == "Psalm") "s" else " chapters"} $chapter to ${numberToWords(chapterEndStr.toLong())}"
            verseStartStr.isEmpty() -> "$spokenBook$chapterWord $chapter"
            verseEndStr == "end" -> "$spokenBook$chapterWord $chapter, verse ${numberToWords(verseStartStr.toLong())} to the end"
            verseEndStr.isNotEmpty() -> "$spokenBook$chapterWord $chapter, verses ${numberToWords(verseStartStr.toLong())} to ${numberToWords(verseEndStr.toLong())}"
            else -> "$spokenBook$chapterWord $chapter, verse ${numberToWords(verseStartStr.toLong())}"
        }
    }

    /**
     * Splits normalized text into individual sentences or clauses suitable for sequential TTS synthesis.
     */
    fun splitSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?][\"”']?)\\s+(?=[\"“'A-Z0-9])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun chunks(text: String, maxChars: Int = 320): List<String> {
        val sentences = splitSentences(text)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > maxChars) {
                result += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}
