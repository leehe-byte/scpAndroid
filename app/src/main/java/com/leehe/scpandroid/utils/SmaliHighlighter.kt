package com.leehe.scpandroid.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object SmaliHighlighter {
    private val keywordColor = Color(0xFF0033B3) // Blue
    private val instructionColor = Color(0xFF008080) // Teal
    private val registerColor = Color(0xFF94558D) // Purple
    private val stringColor = Color(0xFF067D17) // Green
    private val commentColor = Color(0xFF8C8C8C) // Grey
    private val methodColor = Color(0xFF7A7A43) // Brownish
    private val labelColor = Color(0xFF000000)

    fun highlight(code: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = code.split("\n")
            lines.forEachIndexed { index, line ->
                highlightLine(line)
                if (index < lines.size - 1) append("\n")
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(line: String) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("#")) {
            withStyle(SpanStyle(color = commentColor)) { append(line) }
            return
        }

        var currentIndex = 0
        
        // Very basic regex-based highlighting
        val patterns = listOf(
            Regex("""^\.[a-z]+"""), // Directives
            Regex("""\b(invoke-[a-z/]+|move-[a-z/]*|return-[a-z/]*|goto|if-[a-z/]+|const-[a-z/]*|new-[a-z/]*|check-cast|instance-of|array-length|fill-array-data|throw|monitor-enter|monitor-exit|packed-switch|sparse-switch|aget[a-z/]*|aput[a-z/]*|iget[a-z/]*|iput[a-z/]*|sget[a-z/]*|sput[a-z/]*)\b"""), // Instructions
            Regex("""\b[vp]\d+\b"""), // Registers
            Regex("""\"[^\"]*\""""), // Strings
            Regex(""":[a-z_0-9]+"""), // Labels
            Regex("""->[a-zA-Z0-9_$<>]+""") // Methods
        )

        val words = line.split(Regex("""(?<=[ \t,()])|(?=[ \t,()])"""))
        for (word in words) {
            when {
                word.startsWith(".") -> withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) { append(word) }
                word.startsWith("#") -> {
                    val remaining = line.substring(line.indexOf(word, currentIndex))
                    withStyle(SpanStyle(color = commentColor)) { append(remaining) }
                    return
                }
                word.startsWith("\"") -> withStyle(SpanStyle(color = stringColor)) { append(word) }
                word.startsWith(":") -> withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(word) }
                word.contains("->") -> withStyle(SpanStyle(color = methodColor)) { append(word) }
                word.matches(Regex("""\b(v|p)\d+\b""")) -> withStyle(SpanStyle(color = registerColor)) { append(word) }
                word.matches(Regex("""\b(invoke-[a-z/]+|move-[a-z/]*|return-[a-z/]*|goto|if-[a-z/]+|const-[a-z/]*|new-[a-z/]*|check-cast|instance-of|array-length|fill-array-data|throw|monitor-enter|monitor-exit|packed-switch|sparse-switch|aget[a-z/]*|aput[a-z/]*|iget[a-z/]*|iput[a-z/]*|sget[a-z/]*|sput[a-z/]*)\b""")) -> withStyle(SpanStyle(color = instructionColor)) { append(word) }
                else -> append(word)
            }
        }
    }
}
