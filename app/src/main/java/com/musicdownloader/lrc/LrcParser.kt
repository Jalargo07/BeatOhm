package com.musicdownloader.lrc

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {

    private val timestampRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")

    fun parse(lrc: String): List<LrcLine> {
        if (lrc.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        for (raw in lrc.lines()) {
            val matches = timestampRegex.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substringAfterLast(']').trim()
            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: continue
                val sec = match.groupValues[2].toLongOrNull() ?: continue
                val msStr = match.groupValues[3]
                val ms = when {
                    msStr.length == 3 -> msStr.toLongOrNull() ?: 0L
                    msStr.length == 2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    msStr.length == 1 -> (msStr.toLongOrNull() ?: 0L) * 100
                    else -> 0L
                }
                val timeMs = min * 60_000 + sec * 1_000 + ms
                lines.add(LrcLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun hasTimestamps(lrc: String): Boolean {
        return timestampRegex.containsMatchIn(lrc)
    }
}
