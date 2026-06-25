package com.leehe.scpandroid

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.leehe.scpandroid.utils.CodeHighlighter
import com.leehe.scpandroid.utils.HexUtils
import com.leehe.scpandroid.utils.MarkdownRenderer
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
            var mdHtml by remember { mutableStateOf("") }
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
                                    if (isTruncated) appendLine("\n\u26a0 文件过大，仅显示前 512KB")
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
                                    sb.append("\n\u26a0 文件过大，仅显示前 10,000 行")
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

            // 预览模式下预渲染 Markdown HTML
            LaunchedEffect(isPreviewMode, textValue.text) {
                if (isPreviewMode && extension == "md") {
                    mdHtml = withContext(Dispatchers.IO) {
                        MarkdownRenderer.toHtml(textValue.text, isDark)
                    }
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
                        } else if (isPreviewMode && extension == "md") {
                            // WebView Markdown 预览
                            MarkdownWebView(
                                html = mdHtml,
                                isDark = isDark,
                                modifier = Modifier.fillMaxSize()
                            )
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
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownWebView(html: String, isDark: Boolean, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = false
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                webViewClient = WebViewClient()
                setBackgroundColor(if (isDark) 0xFF0D1117.toInt() else 0xFFFFFFFF.toInt())
                webView = this
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { wv ->
            val current = wv.url
            if (current == null || current == "about:blank") {
                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
    )
}

@Composable
fun CodeEditorWithLineNumbers(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val lineCount = value.text.lines().size.coerceAtLeast(1)

    Row(modifier = Modifier.fillMaxSize()) {
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
                Box { innerTextField() }
            }
        )
    }
}
