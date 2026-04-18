package com.leehe.scpandroid

import android.os.Bundle
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
        val filePath = intent.getStringExtra("file_path") ?: return finish()
        val isHex = intent.getBooleanExtra("is_hex", false)
        val file = File(filePath)
        val extension = file.extension.lowercase()

        setContent {
            val isDark = isSystemInDarkTheme()
            var textValue by remember { mutableStateOf(TextFieldValue("加载中...")) }
            var isLoading by remember { mutableStateOf(true) }
            var isPreviewMode by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // 异步加载与大文件保护
            LaunchedEffect(file) {
                withContext(Dispatchers.IO) {
                    val content = try {
                        when {
                            isHex -> {
                                val bytes = if (file.length() > 1024 * 512) {
                                    file.inputStream().use { it.readNBytes(1024 * 512) } // 十六进制仅加载前 512KB
                                } else file.readBytes()
                                HexUtils.formatHexView(bytes)
                            }
                            file.length() > 2 * 1024 * 1024 -> {
                                // 大文件保护：读取前 2MB
                                file.inputStream().bufferedReader().use { reader ->
                                    val sb = StringBuilder()
                                    var lines = 0
                                    while (lines < 10000) { // 最多读取1万行
                                        val line = reader.readLine() ?: break
                                        sb.append(line).append("\n")
                                        lines++
                                    }
                                    sb.toString() + "\n--- 文件过大，仅显示部分内容 ---"
                                }
                            }
                            else -> file.readText()
                        }
                    } catch (e: Exception) {
                        "加载失败: ${e.message}"
                    }
                    
                    // 应用初始语法高亮
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
                                    Text(if(isHex) "十六进制模式" else "代码编辑", style = MaterialTheme.typography.labelSmall)
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
                                                try { file.writeText(textValue.text); true } catch (e: Exception) { false }
                                            }
                                            if (success) {
                                                Toast.makeText(this@EditorActivity, "保存成功", Toast.LENGTH_SHORT).show()
                                                finish()
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
                            if (isPreviewMode) {
                                // Markdown 预览区域 (基础展示)
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    Text(text = textValue.annotatedString, style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                // 专业代码编辑区域
                                CodeEditor(
                                    value = textValue,
                                    onValueChange = { 
                                        // 实时高亮逻辑（仅在文本改变时，此处可优化性能）
                                        textValue = it.copy(annotatedString = CodeHighlighter.highlight(it.text, extension))
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
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isDark: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(modifier = Modifier.fillMaxSize()) {
                    // 这里可以扩展行号列
                    Box(modifier = Modifier.weight(1f)) {
                        innerTextField()
                    }
                }
            }
        )
    }
}
