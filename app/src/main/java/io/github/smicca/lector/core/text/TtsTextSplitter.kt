package io.github.smicca.lector.core.text

object TtsTextSplitter {
    fun split(text: String, limit: Int = 160): List<String> {
        require(limit > 0)
        if (text.length <= limit) return listOf(text)

        val parts = mutableListOf<String>()
        var start = 0
        while (text.length - start > limit) {
            val end = start + limit
            val boundaries = (start + 1..end).filter { text[it - 1].isWhitespace() || text[it].isWhitespace() }
            val boundary = boundaries.lastOrNull { at ->
                text.substring(start, at).trimEnd().lastOrNull() in listOf('.', '!', '?')
            } ?: boundaries.lastOrNull { at ->
                text.substring(start, at).trimEnd().lastOrNull() in listOf(',', ':', ';')
            } ?: boundaries.lastOrNull()

            if (boundary == null) {
                // Single long word exceeding limit, take up to limit
                parts += text.substring(start, end)
                start = end
            } else {
                parts += text.substring(start, boundary)
                start = boundary
            }
        }
        if (start < text.length) parts += text.substring(start)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
