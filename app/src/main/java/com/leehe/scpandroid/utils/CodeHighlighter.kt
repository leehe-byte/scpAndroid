package com.leehe.scpandroid.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object CodeHighlighter {
    private val keywordColor = Color(0xFFCF8E6D)
    private val stringColor = Color(0xFF6A8759)
    private val commentColor = Color(0xFF808080)
    private val numberColor = Color(0xFF6897BB)
    private val functionColor = Color(0xFFFFC66D)
    private val typeColor = Color(0xFF287BDE)

    fun highlight(code: String, extension: String): AnnotatedString {
        return buildAnnotatedString {
            val keywords = getKeywords(extension)
            val types = getTypes(extension)
            val singleLineComment = getCommentPrefix(extension)

            val lines = code.split("\n")
            lines.forEachIndexed { i, line ->
                if (!highlightLine(line, keywords, types, singleLineComment)) {
                    append(line)
                }
                if (i < lines.size - 1) append("\n")
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(
        line: String,
        keywords: Set<String>,
        types: Set<String>,
        commentPrefix: String?
    ): Boolean {
        val trimmed = line.trimStart()

        // 行注释
        if (commentPrefix != null && trimmed.startsWith(commentPrefix)) {
            withStyle(SpanStyle(color = commentColor)) { append(line) }
            return true
        }
        // # 注释 (Python, shell)
        if (trimmed.startsWith("#") || trimmed.startsWith("--")) {
            withStyle(SpanStyle(color = commentColor)) { append(line) }
            return true
        }

        if (keywords.isEmpty() && types.isEmpty()) return false

        var pos = 0
        while (pos < line.length) {
            when {
                // 字符串
                line[pos] == '"' || line[pos] == '\'' -> {
                    val quote = line[pos]
                    val end = findStringEnd(line, pos, quote)
                    withStyle(SpanStyle(color = stringColor)) { append(line.substring(pos, end)) }
                    pos = end
                }
                // 数字
                line[pos].isDigit() -> {
                    val start = pos
                    while (pos < line.length && (line[pos].isDigit() || line[pos] == '.' || line[pos] == 'x' || line[pos] == 'X' || (line[pos] in 'a'..'f') || (line[pos] in 'A'..'F'))) pos++
                    withStyle(SpanStyle(color = numberColor)) { append(line.substring(start, pos)) }
                }
                // 注释起始 (行内)
                commentPrefix != null && line.startsWith(commentPrefix, pos) -> {
                    withStyle(SpanStyle(color = commentColor)) { append(line.substring(pos)) }
                    return true
                }
                // 标识符
                line[pos].isLetter() || line[pos] == '_' -> {
                    val start = pos
                    while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
                    val word = line.substring(start, pos)
                    when {
                        word in keywords -> withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) { append(word) }
                        word in types -> withStyle(SpanStyle(color = typeColor)) { append(word) }
                        // 检测函数调用
                        pos < line.length && line[pos] == '(' -> withStyle(SpanStyle(color = functionColor)) { append(word) }
                        else -> append(word)
                    }
                }
                else -> {
                    append(line[pos])
                    pos++
                }
            }
        }
        return true
    }

    private fun findStringEnd(line: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < line.length) {
            if (line[i] == '\\' && i + 1 < line.length) { i += 2; continue }
            if (line[i] == quote) return i + 1
            i++
        }
        return line.length
    }

    private fun getCommentPrefix(ext: String): String? = when (ext) {
        "java", "kt", "cpp", "c", "js", "ts" -> "//"
        "py" -> null // Python 使用 #
        "xml", "html" -> null // 使用 <!--
        else -> null
    }

    private fun getKeywords(ext: String): Set<String> = when (ext) {
        "py" -> setOf("def", "class", "if", "else", "elif", "for", "while", "return", "import", "from", "as", "try", "except", "with", "pass", "in", "is", "not", "and", "or", "break", "continue", "yield", "raise", "finally", "lambda", "global", "nonlocal", "assert", "del", "True", "False", "None")
        "java" -> setOf("package", "import", "class", "interface", "extends", "implements", "if", "else", "for", "while", "return", "try", "catch", "finally", "throw", "throws", "public", "private", "protected", "static", "final", "abstract", "void", "new", "this", "super", "null", "true", "false", "switch", "case", "default", "break", "continue", "do", "enum", "instanceof", "synchronized", "volatile", "transient", "native")
        "kt" -> setOf("package", "import", "class", "interface", "object", "fun", "val", "var", "if", "else", "for", "while", "return", "try", "catch", "finally", "throw", "public", "private", "protected", "internal", "override", "open", "abstract", "data", "sealed", "enum", "companion", "suspend", "inline", "operator", "infix", "tailrec", "const", "lateinit", "when", "is", "as", "in", "this", "super", "null", "true", "false", "do", "break", "continue")
        "cpp", "c", "h" -> setOf("include", "define", "if", "else", "for", "while", "return", "try", "catch", "throw", "class", "struct", "void", "static", "const", "using", "namespace", "template", "typename", "virtual", "public", "private", "protected", "new", "delete", "this", "nullptr", "true", "false", "switch", "case", "default", "break", "continue", "do", "enum", "typedef", "extern", "sizeof", "auto", "register", "volatile", "int", "char", "long", "float", "double", "bool", "unsigned", "signed")
        "js", "ts" -> setOf("function", "class", "const", "let", "var", "if", "else", "for", "while", "return", "export", "import", "from", "async", "await", "try", "catch", "throw", "new", "this", "super", "null", "undefined", "true", "false", "switch", "case", "default", "break", "continue", "do", "typeof", "instanceof", "extends", "implements", "interface", "type", "enum", "static", "private", "public", "protected", "of", "in")
        "html", "xml" -> setOf()
        "json" -> setOf()
        "md" -> emptySet()
        else -> emptySet()
    }

    private fun getTypes(ext: String): Set<String> = when (ext) {
        "java" -> setOf("String", "Integer", "int", "Boolean", "boolean", "Long", "long", "Float", "float", "Double", "double", "char", "byte", "Byte", "short", "Short", "Void", "void", "Object", "Class", "List", "Map", "Set", "ArrayList", "HashMap")
        "kt" -> setOf("String", "Int", "Boolean", "Long", "Float", "Double", "Char", "Byte", "Short", "Unit", "Nothing", "Any", "List", "Map", "Set", "MutableList", "MutableMap", "MutableSet", "Array")
        "cpp", "c", "h" -> setOf("int", "char", "bool", "long", "float", "double", "void", "unsigned", "signed", "short", "size_t", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t")
        "ts" -> setOf("string", "number", "boolean", "void", "any", "never", "unknown", "null", "undefined")
        else -> emptySet()
    }
}
