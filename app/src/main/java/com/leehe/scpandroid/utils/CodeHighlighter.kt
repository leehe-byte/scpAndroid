package com.leehe.scpandroid.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object CodeHighlighter {
    private val keywordColor = Color(0xFFCF8E6D) // Orange-ish
    private val stringColor = Color(0xFF6A8759)  // Green
    private val commentColor = Color(0xFF808080) // Grey
    private val numberColor = Color(0xFF6897BB)  // Light Blue
    private val functionColor = Color(0xFFFFC66D) // Yellow-ish
    private val typeColor = Color(0xFF287BDE)    // Blue

    fun highlight(code: String, extension: String): AnnotatedString {
        return buildAnnotatedString {
            val keywords = getKeywords(extension)
            val types = getTypes(extension)
            
            var index = 0
            val lines = code.split("\n")
            lines.forEachIndexed { i, line ->
                highlightLine(line, keywords, types)
                if (i < lines.size - 1) append("\n")
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(line: String, keywords: Set<String>, types: Set<String>) {
        val trimmed = line.trimStart()
        
        // Comments (simple single line)
        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
            withStyle(SpanStyle(color = commentColor)) { append(line) }
            return
        }

        val tokens = line.split(Regex("""(?<=[ \t,();.{}\[\]])|(?=[ \t,();.{}\[\]])"""))
        for (token in tokens) {
            when {
                token.trim() in keywords -> withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) { append(token) }
                token.trim() in types -> withStyle(SpanStyle(color = typeColor)) { append(token) }
                token.startsWith("\"") && token.endsWith("\"") -> withStyle(SpanStyle(color = stringColor)) { append(token) }
                token.matches(Regex("""\d+""")) -> withStyle(SpanStyle(color = numberColor)) { append(token) }
                // Basic function detection
                token.contains("(") -> withStyle(SpanStyle(color = functionColor)) { append(token) }
                else -> append(token)
            }
        }
    }

    private fun getKeywords(ext: String): Set<String> = when (ext) {
        "py" -> setOf("def", "class", "if", "else", "elif", "for", "while", "return", "import", "from", "as", "try", "except", "with", "pass", "in", "is", "not", "and", "or")
        "java", "kt" -> setOf("package", "import", "class", "interface", "fun", "val", "var", "if", "else", "for", "while", "return", "try", "catch", "finally", "public", "private", "protected", "override", "internal", "object", "companion")
        "cpp", "c" -> setOf("include", "define", "if", "else", "for", "while", "return", "try", "catch", "throw", "class", "struct", "void", "static", "const", "using", "namespace")
        "js", "ts" -> setOf("function", "class", "const", "let", "var", "if", "else", "for", "while", "return", "export", "import", "from", "async", "await")
        else -> emptySet()
    }

    private fun getTypes(ext: String): Set<String> = when (ext) {
        "java", "kt", "cpp", "c" -> setOf("String", "Int", "Boolean", "Long", "Float", "Double", "char", "int", "bool", "long", "float", "double", "Void", "void")
        else -> emptySet()
    }
}
