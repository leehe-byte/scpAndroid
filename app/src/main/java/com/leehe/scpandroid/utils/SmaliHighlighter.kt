package com.leehe.scpandroid.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Smali 语法高亮器 — 配色对齐 Notepad4 [Android Smali]
 */
object SmaliHighlighter {

    // Notepad4 Android Smali palette
    private val keywordColor    = Color(0xFF00B050)  // Keyword: green
    private val directiveColor  = Color(0xFFFF8000)  // Directive: orange
    private val instructionColor= Color(0xFF0080FF)  // Instruction: blue
    private val registerColor   = Color(0xFFFF8000)  // Register: orange
    private val typeColor       = Color(0xFF007F7F)  // Type: teal bold
    private val fieldColor      = Color(0xFF648000)  // Field: olive
    private val methodColor     = Color(0xFFA46000)  // Method: brown
    private val commentColor    = Color(0xFF608060)  // Comment: gray-green
    private val stringColor     = Color(0xFF008000)  // String: green
    private val labelBgColor    = Color(0xFF404030)  // Label background: dark
    private val numberColor     = Color(0xFFF84C4C)  // Number: red
    private val operatorColor   = Color(0xFFA349A4)  // Operator: purple

    private val instructionRx = Regex(
        """\b(invoke-[a-z/]+|move-result[a-z/]*|move[a-z/]*|return[a-z/-]*|goto[/:]?\w*|if-[a-z-]+|const[/:][a-z/]*|new-[a-z/]+|check-cast|instance-of|array-length|fill-array-data|throw|monitor-enter|monitor-exit|packed-switch|sparse-switch|aget[a-z/]*|aput[a-z/]*|iget[a-z/]*|iput[a-z/]*|sget[a-z/]*|sput[a-z/]*|add-[a-z/]+|sub-[a-z/]+|mul-[a-z/]+|div-[a-z/]+|rem-[a-z/]+|and-[a-z/]+|or-[a-z/]+|xor-[a-z/]+|shl-[a-z/]+|shr-[a-z/]+|ushr-[a-z/]+|neg-[a-z/]+|not-[a-z/]+|cmp[a-z/-]*|nop|fill-new-array|filled-new-array|cmp|cmpl|cmpg|aget|aput|iget|iput|sget|sput)\b""")
    private val registerRx = Regex("""\b[vp]\d+\b""")

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

        // 注释行
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
                // 标签（冒号结尾的词）
                line[pos] == ':' && pos > 0 -> {
                    // 往前找到标签开头
                    val labelStart = pos - 1
                    var s = labelStart
                    while (s >= 0 && !line[s].isWhitespace() && line[s] != '{' && line[s] != '}') s--
                    s++
                    withStyle(SpanStyle(background = labelBgColor)) { append(line.substring(s, pos + 1)) }
                    pos++
                }
                // 逗号/空白/括号
                line[pos].isWhitespace() || line[pos] == ',' || line[pos] == '{' || line[pos] == '}' || line[pos] == '[' || line[pos] == ']' -> {
                    append(line[pos])
                    pos++
                }
                // 伪指令 (.class .method .field 等)
                line[pos] == '.' -> {
                    val start = pos
                    while (pos < line.length && (line[pos].isLetter() || line[pos] == '-' || line[pos] == '_')) pos++
                    val word = line.substring(start, pos)
                    val color = when (word) {
                        ".method", ".end", ".field", ".class", ".super", ".implements",
                        ".annotation", ".prologue", ".locals", ".registers", ".param",
                        ".line", ".local", ".catch", ".catchall" -> directiveColor
                        else -> keywordColor
                    }
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(word) }
                }
                // 数字
                line[pos].isDigit() || (line[pos] == '-' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                    val start = pos
                    if (line[pos] == '-') pos++
                    if (line.startsWith("0x", pos)) pos += 2
                    while (pos < line.length && (line[pos].isDigit() || line[pos] == '.' || line[pos] == 'x' ||
                        line[pos] in 'a'..'f' || line[pos] in 'A'..'F')) pos++
                    withStyle(SpanStyle(color = numberColor)) { append(line.substring(start, pos)) }
                }
                // 操作符
                line[pos] in "=<>+" -> {
                    withStyle(SpanStyle(color = operatorColor)) { append(line[pos]) }
                    pos++
                }
                // 标识符/关键字/类型/方法
                line[pos].isLetter() || line[pos] == '_' || line[pos] == '<' -> {
                    val start = pos
                    while (pos < line.length && !line[pos].isWhitespace() && line[pos] != ',' &&
                        line[pos] != '{' && line[pos] != '}' && line[pos] != '[' && line[pos] != ']' &&
                        line[pos] != '"' && line[pos] != '#' && line[pos] != ':' && line[pos] != '=')
                        pos++
                    val word = line.substring(start, pos)

                    when {
                        // 寄存器
                        registerRx.matches(word) -> withStyle(SpanStyle(color = registerColor)) { append(word) }
                        // 指令
                        instructionRx.matches(word) -> withStyle(SpanStyle(color = instructionColor)) { append(word) }
                        // 方法调用 (包含->)
                        word.contains("->") && word.contains("(") -> withStyle(SpanStyle(color = methodColor)) { append(word) }
                        // 字段引用 (包含->，不含括号)
                        word.contains("->") -> withStyle(SpanStyle(color = fieldColor)) { append(word) }
                        // 类型 (L包名;)
                        word.startsWith("L") && word.endsWith(";") -> withStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.Bold)) { append(word) }
                        // 数组类型
                        word.startsWith("[") && word.length > 1 -> withStyle(SpanStyle(color = typeColor)) { append(word) }
                        // 泛型标记
                        word.startsWith("<") && word.endsWith(">") -> withStyle(SpanStyle(color = typeColor)) { append(word) }
                        else -> append(word)
                    }
                }
                else -> { append(line[pos]); pos++ }
            }
        }
    }
}
