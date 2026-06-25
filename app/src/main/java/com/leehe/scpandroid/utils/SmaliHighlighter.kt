package com.leehe.scpandroid.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object SmaliHighlighter {
    private val keywordColor = Color(0xFF0033B3)
    private val instructionColor = Color(0xFF008080)
    private val registerColor = Color(0xFF94558D)
    private val stringColor = Color(0xFF067D17)
    private val commentColor = Color(0xFF8C8C8C)
    private val methodColor = Color(0xFF7A7A43)
    private val labelColor = Color(0xFF000000)

    private val instructionPattern = Regex("""\b(invoke-[a-z/]+|move-result[a-z/]*|move[a-z/]*|return[a-z/]*|goto[/:]?\w*|if-[a-z]+[a-z/]*|const[/:][a-z/]*|new-[a-z/]+|check-cast|instance-of|array-length|fill-array-data|throw|monitor-enter|monitor-exit|packed-switch|sparse-switch|aget[a-z/]*|aput[a-z/]*|iget[a-z/]*|iput[a-z/]*|sget[a-z/]*|sput[a-z/]*|add-[a-z/]+|sub-[a-z/]+|mul-[a-z/]+|div-[a-z/]+|rem-[a-z/]+|and-[a-z/]+|or-[a-z/]+|xor-[a-z/]+|shl-[a-z/]+|shr-[a-z/]+|ushr-[a-z/]+|neg-[a-z/]+|not-[a-z/]+|cmp[a-z/]*|nop|fill-new-array|cmp)\b""")
    private val registerPattern = Regex("""\b[vp]\d+\b""")

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

        // 注释
        if (trimmed.startsWith("#")) {
            withStyle(SpanStyle(color = commentColor)) { append(line) }
            return
        }

        var pos = 0
        while (pos < line.length) {
            when {
                // 字符串
                line[pos] == '"' -> {
                    val start = pos
                    pos++
                    while (pos < line.length) {
                        if (line[pos] == '\\' && pos + 1 < line.length) { pos += 2; continue }
                        if (line[pos] == '"') { pos++; break }
                        pos++
                    }
                    withStyle(SpanStyle(color = stringColor)) { append(line.substring(start, pos)) }
                }
                // 逗号/空格
                line[pos].isWhitespace() || line[pos] == ',' || line[pos] == '{' || line[pos] == '}' -> {
                    append(line[pos])
                    pos++
                }
                // 标签
                line[pos] == ':' -> {
                    val start = pos
                    while (pos < line.length && !line[pos].isWhitespace()) pos++
                    withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(line.substring(start, pos)) }
                }
                // 指令/伪指令
                line[pos] == '.' -> {
                    val start = pos
                    while (pos < line.length && (line[pos].isLetter() || line[pos] == '-')) pos++
                    withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) { append(line.substring(start, pos)) }
                }
                // 方法调用
                line[pos].isLetter() -> {
                    val start = pos
                    while (pos < line.length && !line[pos].isWhitespace() && line[pos] != ',' && line[pos] != '{' && line[pos] != '}') pos++
                    val word = line.substring(start, pos)
                    when {
                        word.endsWith(":") -> withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(word) }
                        registerPattern.matches(word) -> withStyle(SpanStyle(color = registerColor)) { append(word) }
                        instructionPattern.matches(word) -> withStyle(SpanStyle(color = instructionColor)) { append(word) }
                        word.contains("->") -> withStyle(SpanStyle(color = methodColor)) { append(word) }
                        word.startsWith("L") && word.endsWith(";") -> withStyle(SpanStyle(color = keywordColor)) { append(word) }
                        else -> append(word)
                    }
                }
                else -> {
                    append(line[pos])
                    pos++
                }
            }
        }
    }
}
