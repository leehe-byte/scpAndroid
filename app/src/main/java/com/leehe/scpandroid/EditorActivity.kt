package com.leehe.scpandroid

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leehe.scpandroid.utils.CodeHighlighter
import com.leehe.scpandroid.utils.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra("file_path")?.takeIf { it.isNotBlank() } ?: run {
            Toast.makeText(this, "文件路径无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val isHex = intent.getBooleanExtra("is_hex", false)
        val file = File(filePath)
        val extension = file.extension.lowercase()

        setContent {
            val isDark = isSystemInDarkTheme()
            var textValue by remember { mutableStateOf(TextFieldValue("加载中...")) }
            var isLoading by remember { mutableStateOf(true) }
            var isPreviewMode by remember { mutableStateOf(false) }
            var isTruncated by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(file) {
                withContext(Dispatchers.IO) {
                    val content = try {
                        when {
                            isHex -> {
                                val maxBytes = 1024 * 512
                                val bytes = if (file.length() > maxBytes) {
                                    isTruncated = true
                                    file.inputStream().use { it.readNBytes(maxBytes) }
                                } else file.readBytes()
                                buildString {
                                    appendLine(HexUtils.formatHexView(bytes))
                                    if (isTruncated) appendLine("\n⚠ 文件过大，仅显示前 512KB 的内容")
                                }
                            }
                            file.length() > 2 * 1024 * 1024 -> {
                                isTruncated = true
                                file.inputStream().bufferedReader().use { reader ->
                                    val sb = StringBuilder()
                                    var lines = 0
                                    while (lines < 10000) {
                                        val line = reader.readLine() ?: break
                                        sb.appendLine(line)
                                        lines++
                                    }
                                    sb.append("\n⚠ 文件过大，仅显示前 10,000 行的内容")
                                    sb.toString()
                                }
                            }
                            else -> file.readText()
                        }
                    } catch (e: Exception) {
                        "加载失败: ${e.message}"
                    }

                    val annotated = if (!isHex) CodeHighlighter.highlight(content, extension)
                        else CodeHighlighter.highlight(content, "hex")

                    textValue = TextFieldValue(annotated)
                    isLoading = false
                }
            }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Row {
                                        Text(if(isHex) "十六进制" else extension.ifEmpty { "文本" }, style = MaterialTheme.typography.labelSmall)
                                        if (isTruncated) Text(" · 部分加载", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, null) }
                            },
                            actions = {
                                if (extension == "md") {
                                    IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                                        Icon(if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility, null)
                                    }
                                }
                                if (!isHex && !isLoading && !isPreviewMode) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            val success = withContext(Dispatchers.IO) {
                                                try { file.writeText(textValue.text); true }
                                                catch (e: Exception) {
                                                    Log.e("Editor", "Save failed", e)
                                                    false
                                                }
                                            }
                                            if (success) {
                                                Toast.makeText(this@EditorActivity, "保存成功", Toast.LENGTH_SHORT).show()
                                                finish()
                                            } else {
                                                Toast.makeText(this@EditorActivity, "保存失败，请检查权限", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Save, null)
                                    }
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            if (isPreviewMode && extension == "md") {
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    MarkdownPreview(content = textValue.text)
                                }
                            } else {
                                var lastHighlightedText by remember { mutableStateOf("") }
                                CodeEditorWithLineNumbers(
                                    value = textValue,
                                    onValueChange = { newValue ->
                                        val text = newValue.text
                                        if (text != lastHighlightedText) {
                                            lastHighlightedText = text
                                            textValue = newValue.copy(annotatedString = CodeHighlighter.highlight(text, extension))
                                        } else {
                                            textValue = newValue
                                        }
                                    },
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodeEditorWithLineNumbers(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isDark: Boolean
) {
    val lineCount = value.text.lines().size.coerceAtLeast(1)

    Row(modifier = Modifier.fillMaxSize()) {
        // 行号列
        Column(
            modifier = Modifier
                .widthIn(min = 32.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            for (i in 1..lineCount) {
                Text(
                    text = i.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun MarkdownPreview(content: String) {
    val annotated = buildAnnotatedString {
        val lines = content.split("\n")
        var inCodeBlock = false
        lines.forEach { line ->
            when {
                line.startsWith("```") -> {
                    inCodeBlock = !inCodeBlock
                    if (!inCodeBlock) append("\n")
                }
                inCodeBlock -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = androidx.compose.ui.graphics.Color(0x22000000))) {
                        append(line)
                    }
                    append("\n")
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)) { append(line.removePrefix("# ")) }
                    append("\n")
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append(line.removePrefix("## ")) }
                    append("\n")
                }
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) { append(line.removePrefix("### ")) }
                    append("\n")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    withStyle(SpanStyle()) { append("  •  ${line.removePrefix("- ").removePrefix("* ")}") }
                    append("\n")
                }
                line.startsWith("> ") -> {
                    withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Gray, fontStyle = FontStyle.Italic)) { append(line) }
                    append("\n")
                }
                else -> {
                    append(renderInlineMarkdown(line))
                    append("\n")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text.startsWith("*", i) && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val end = text.indexOf("*", i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = androidx.compose.ui.graphics.Color(0x22000000))) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text.startsWith("[", i) -> {
                    val closeBracket = text.indexOf("](", i)
                    val closeParen = if (closeBracket > 0) text.indexOf(")", closeBracket) else -1
                    if (closeParen > 0) {
                        val linkText = text.substring(i + 1, closeBracket)
                        withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF0969DA), textDecoration = TextDecoration.Underline)) {
                            append(linkText)
                        }
                        i = closeParen + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
