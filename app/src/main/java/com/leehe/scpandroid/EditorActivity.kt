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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.leehe.scpandroid.utils.CodeHighlighter
import com.leehe.scpandroid.utils.HexUtils
import com.leehe.scpandroid.utils.MarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra("file_path")?.takeIf { it.isNotBlank() } ?: run {
            Toast.makeText(this, "文件路径无效", Toast.LENGTH_SHORT).show()
            finish(); return
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
            var lastHighlightedText by remember { mutableStateOf("") }
            var mdHtml by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()

            LaunchedEffect(file) {
                withContext(Dispatchers.IO) {
                    val raw = try {
                        when {
                            isHex -> {
                                val maxBytes = 1024 * 512
                                val bytes = if (file.length() > maxBytes) {
                                    isTruncated = true; file.inputStream().use { it.readNBytes(maxBytes) }
                                } else file.readBytes()
                                buildString {
                                    appendLine(HexUtils.formatHexView(bytes))
                                    if (isTruncated) appendLine("\n\u26a0 文件过大，仅显示前 512KB")
                                }
                            }
                            file.length() > 2 * 1024 * 1024 -> {
                                isTruncated = true
                                file.inputStream().bufferedReader().use { r ->
                                    val sb = StringBuilder(); var lines = 0
                                    while (lines < 10000) { val l = r.readLine() ?: break; sb.appendLine(l); lines++ }
                                    sb.append("\n\u26a0 文件过大，仅显示前 10,000 行"); sb.toString()
                                }
                            }
                            else -> file.readText()
                        }
                    } catch (e: Exception) { "加载失败: ${e.message}" }

                    val annotated = if (!isHex) CodeHighlighter.highlight(raw, extension) else CodeHighlighter.highlight(raw, "hex")
                    textValue = TextFieldValue(annotated)
                    lastHighlightedText = raw
                    isLoading = false
                }
            }

            // 高亮防抖：文本变化后 300ms 无新输入才重新高亮
            LaunchedEffect(textValue.text) {
                if (!isHex && textValue.text == lastHighlightedText) return@LaunchedEffect
                if (isHex) return@LaunchedEffect
                delay(300)
                val target = textValue.text
                if (target != textValue.text) return@LaunchedEffect
                val newAnnotated = withContext(Dispatchers.Default) {
                    CodeHighlighter.highlight(target, extension)
                }
                if (target != textValue.text) return@LaunchedEffect
                lastHighlightedText = target
                textValue = textValue.copy(annotatedString = newAnnotated)
            }

            LaunchedEffect(isPreviewMode, textValue.text) {
                if (isPreviewMode && extension == "md") {
                    mdHtml = withContext(Dispatchers.IO) { MarkdownRenderer.toHtml(textValue.text, isDark) }
                }
            }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Scaffold(topBar = {
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
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, null) } },
                        actions = {
                            if (extension == "md") {
                                IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                                    Icon(if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility, null)
                                }
                            }
                            if (!isHex && !isLoading && !isPreviewMode) {
                                IconButton(onClick = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            try { file.writeText(textValue.text); true } catch (e: Exception) { false }
                                        }
                                        if (ok) { Toast.makeText(this@EditorActivity, "保存成功", Toast.LENGTH_SHORT).show(); finish() }
                                        else Toast.makeText(this@EditorActivity, "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                }) { Icon(Icons.Default.Save, null) }
                            }
                        }
                    )
                }) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
                        if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                        else if (isPreviewMode && extension == "md") MarkdownWebView(html = mdHtml, isDark = isDark, modifier = Modifier.fillMaxSize())
                        else {
                            CodeEditorWithLineNumbers(value = textValue, onValueChange = { nv ->
                                textValue = nv.copy(annotatedString = textValue.annotatedString)
                            })
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
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.apply { javaScriptEnabled = false; builtInZoomControls = true; displayZoomControls = false; loadWithOverviewMode = true; useWideViewPort = true }
                webViewClient = WebViewClient()
                setBackgroundColor(if (isDark) 0xFF0D1117.toInt() else 0xFFFFFFFF.toInt())
                webView = this; loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { wv -> if (wv.url == null || wv.url == "about:blank") wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) },
        modifier = modifier
    )
}

@Composable
fun CodeEditorWithLineNumbers(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    val lineCount = value.text.lines().size.coerceAtLeast(1)
    val numColor = MaterialTheme.colorScheme.outline
    val maxDigits = remember(lineCount) { lineCount.toString().length.coerceAtLeast(1) }
    val numStyle = remember(numColor) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp, color = numColor, textAlign = TextAlign.End)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(min = 32.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            items(lineCount, key = { it }) { i ->
                Text(
                    text = (i + 1).toString().padStart(maxDigits),
                    style = numStyle
                )
            }
        }
        BasicTextField(value = value, onValueChange = onValueChange,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp, vertical = 8.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { Box { it() } }
        )
    }
}
