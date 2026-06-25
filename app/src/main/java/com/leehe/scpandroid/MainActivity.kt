package com.leehe.scpandroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.leehe.scpandroid.models.*
import com.leehe.scpandroid.utils.*
import net.lingala.zip4j.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    // Archive browser state
    private var showArchiveBrowser by mutableStateOf(false)
    private var archiveEntries by mutableStateOf<List<ArchiveEntry>>(emptyList())
    private var archiveFile by mutableStateOf<File?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            refreshAll()
        } else {
            Toast.makeText(this, "需要存储权限才能浏览文件", Toast.LENGTH_LONG).show()
        }
    }

    // Settings States
    private var showHiddenFiles by mutableStateOf(false)
    private var sortBy by mutableStateOf("name")
    private var themeMode by mutableStateOf("system")

    // Panel States
    private var leftCurrentDir by mutableStateOf(Environment.getExternalStorageDirectory())
    private var rightCurrentDir by mutableStateOf(File("/"))
    private val leftFileList = mutableStateListOf<Any>()
    private val rightFileList = mutableStateListOf<Any>()
    
    // Remote states
    private var leftRemotePath by mutableStateOf("/")
    private var rightRemotePath by mutableStateOf("/")
    private var leftStorage by mutableStateOf<NetworkStorage?>(null)
    private var rightStorage by mutableStateOf<NetworkStorage?>(null)
    private var isLeftRemote by mutableStateOf(false)
    private var isRightRemote by mutableStateOf(false)

    private var isLeftActive by mutableStateOf(true)
    private var shizukuActive by mutableStateOf(false)

    private val savedStorages = mutableStateListOf<NetworkStorage>()

    // Transfer State
    private var isTransferring by mutableStateOf(false)
    private var transferMessage by mutableStateOf("")
    private var transferProgress by mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuManager.init()
        savedStorages.addAll(StoragePrefs.loadStorages(this))
        
        setContent {
            val isDark = when(themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var selectedFile by remember { mutableStateOf<Any?>(null) }
                var showMenu by remember { mutableStateOf(false) }
                var showSortDialog by remember { mutableStateOf(false) }
                var showAddStorageDialog by remember { mutableStateOf(false) }
                var storageToEdit by remember { mutableStateOf<NetworkStorage?>(null) }

                val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    uri?.let { handleFileUpload(it) }
                }

                val openDocumentTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    uri?.let {
                        contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        val folderName = DocumentFile.fromTreeUri(this, it)?.name ?: "SAF目录"
                        val newStorage = NetworkStorage(
                            name = folderName,
                            type = StorageType.LOCAL_SAF,
                            host = it.toString(),
                            port = 0,
                            username = "",
                            password = "",
                            rootPath = "/"
                        )
                        savedStorages.add(newStorage)
                        StoragePrefs.saveStorages(this, savedStorages)
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(Modifier.height(12.dp))
                            Text("scpAndroid", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                            
                            Text("本地存储", modifier = Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.labelMedium)
                            NavigationDrawerItem(
                                label = { Text("内部存储") },
                                selected = false,
                                onClick = {
                                    val storageDir = Environment.getExternalStorageDirectory()
                                    if (isLeftActive) { isLeftRemote = false; leftCurrentDir = storageDir }
                                    else { isRightRemote = false; rightCurrentDir = storageDir }
                                    refreshAll(); scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Smartphone, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("系统根目录 (/)") },
                                selected = false,
                                onClick = {
                                    if (isLeftActive) { isLeftRemote = false; leftCurrentDir = File("/") } 
                                    else { isRightRemote = false; rightCurrentDir = File("/") }
                                    refreshAll(); scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Folder, null) }
                            )
                            
                            Divider(Modifier.padding(vertical = 8.dp))
                            Text("其他存储", modifier = Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.labelMedium)
                            savedStorages.forEach { storage ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    NavigationDrawerItem(
                                        label = { Text(storage.name) },
                                        selected = (isLeftActive && leftStorage == storage && isLeftRemote) || (!isLeftActive && rightStorage == storage && isRightRemote),
                                        onClick = {
                                            connectToStorage(storage, isLeftActive)
                                            scope.launch { drawerState.close() }
                                        },
                                        icon = { Icon(getStorageIcon(storage.type), null) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (storage.type != StorageType.LOCAL_SAF) {
                                        IconButton(onClick = { storageToEdit = storage; showAddStorageDialog = true }) {
                                            Icon(Icons.Default.Edit, "编辑")
                                        }
                                    }
                                    IconButton(onClick = { 
                                        savedStorages.remove(storage)
                                        StoragePrefs.saveStorages(this@MainActivity, savedStorages)
                                    }) {
                                        Icon(Icons.Default.Delete, "删除", tint = Color.Red)
                                    }
                                }
                            }
                            NavigationDrawerItem(
                                label = { Text("添加网络存储") },
                                selected = false,
                                onClick = { storageToEdit = null; showAddStorageDialog = true },
                                icon = { Icon(Icons.Default.Add, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("授权本地目录 (SAF)") },
                                selected = false,
                                onClick = { openDocumentTreeLauncher.launch(null) },
                                icon = { Icon(Icons.Default.FolderSpecial, null) }
                            )
                            Divider(Modifier.padding(vertical = 8.dp))
                            Text("系统", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium)
                            NavigationDrawerItem(
                                label = { Text(if(isDark) "浅色模式" else "深色模式") },
                                selected = false,
                                onClick = { themeMode = if(isDark) "light" else "dark" },
                                icon = { Icon(if(isDark) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("退出") },
                                selected = false,
                                onClick = { finishAffinity() },
                                icon = { Icon(Icons.Default.ExitToApp, null) }
                            )
                        }
                    }
                ) {
                    val activeStorage = if (isLeftActive) (if (isLeftRemote) leftStorage else null) else (if (isRightRemote) rightStorage else null)
                    val activePath = if (isLeftActive) (if(isLeftRemote) leftRemotePath else leftCurrentDir.absolutePath) else (if(isRightRemote) rightRemotePath else rightCurrentDir.absolutePath)
                    
                    DualPaneExplorerScreen(
                        leftFiles = leftFileList.toList(),
                        rightFiles = rightFileList.toList(),
                        leftPath = if(isLeftRemote) leftRemotePath else leftCurrentDir.absolutePath,
                        rightPath = if(isRightRemote) rightRemotePath else rightCurrentDir.absolutePath,
                        activePath = activePath,
                        activeStorageName = activeStorage?.name ?: "本地存储",
                        activeStorageIcon = getStorageIcon(activeStorage?.type),
                        isLeftActive = isLeftActive,
                        shizukuActive = shizukuActive,
                        showHidden = showHiddenFiles,
                        onPanelClick = { isLeftActive = it },
                        onFileClick = { item, isLeft -> handleFileClick(item, isLeft) },
                        onFileLongClick = { item, isLeft ->
                            isLeftActive = isLeft
                            selectedFile = item
                            showMenu = true
                        },
                        onPathConfirm = { newPath -> handlePathNavigation(newPath) },
                        onRefresh = { refreshAll() },
                        onToggleHidden = { showHiddenFiles = !showHiddenFiles; refreshAll() },
                        onSortClick = { showSortDialog = true },
                        onRequestShizuku = { 
                            ShizukuManager.checkPermission { granted -> 
                                shizukuActive = granted 
                                if (granted) refreshAll()
                            } 
                        },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onUpload = { pickFileLauncher.launch("*/*") }
                    )
                }

                if (showMenu && selectedFile != null) {
                    FileActionMenu(
                        fileItem = selectedFile!!,
                        isRemote = (isLeftActive && isLeftRemote) || (!isLeftActive && isRightRemote),
                        onDismiss = { showMenu = false },
                        onAction = { action ->
                            handleFileAction(action, selectedFile!!)
                            showMenu = false
                        }
                    )
                }

                if (showSortDialog) {
                    SortDialog(currentSort = sortBy, onDismiss = { showSortDialog = false }, onSort = { sortBy = it; refreshAll(); showSortDialog = false })
                }

                if (showAddStorageDialog) {
                    AddStorageDialog(
                        existingStorage = storageToEdit,
                        onDismiss = { showAddStorageDialog = false; storageToEdit = null },
                        onAdd = { storage ->
                            if (storageToEdit != null) {
                                val idx = savedStorages.indexOf(storageToEdit)
                                if (idx != -1) savedStorages[idx] = storage
                            } else {
                                savedStorages.add(storage)
                            }
                            StoragePrefs.saveStorages(this@MainActivity, savedStorages)
                            showAddStorageDialog = false
                            storageToEdit = null
                        }
                    )
                }

                if (isTransferring) {
                    TransferProgressDialog(transferMessage, transferProgress)
                }

                if (showArchiveBrowser && archiveFile != null) {
                    ArchiveBrowserDialog(
                        archiveFile = archiveFile!!,
                        entries = archiveEntries,
                        onDismiss = { showArchiveBrowser = false; archiveFile = null },
                        onExtract = { entry ->
                            lifecycleScope.launch {
                                val dest = File(archiveFile!!.parentFile!!, archiveFile!!.nameWithoutExtension)
                                dest.mkdirs()
                                isTransferring = true
                                transferMessage = "正在解压: ${entry.name}"
                                val ok = withContext(Dispatchers.IO) {
                                    val zipFile = ZipFile(archiveFile)
                                    try {
                                        zipFile.extractFile(entry.name, dest.absolutePath)
                                        true
                                    } catch (e: Exception) { false }
                                }
                                isTransferring = false
                                if (ok) refreshAll()
                                showArchiveBrowser = false
                                archiveFile = null
                            }
                        },
                        onExtractAll = {
                            val dest = File(archiveFile!!.parentFile!!, archiveFile!!.nameWithoutExtension)
                            dest.mkdirs()
                            isTransferring = true
                            transferMessage = "正在解压全部..."
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) { ArchiveUtils.extractAll(archiveFile!!, dest) }
                                isTransferring = false
                                refreshAll()
                                showArchiveBrowser = false
                                archiveFile = null
                            }
                        }
                    )
                }
            }
        }
        checkPermissions()
        lifecycleScope.launch {
            shizukuActive = ShizukuManager.isShizukuAvailable()
        }
    }

    private fun getStorageIcon(type: StorageType?): ImageVector {
        return when(type) {
            StorageType.SFTP, StorageType.FTP, StorageType.SCP -> Icons.Default.Dns
            StorageType.SMB -> Icons.Default.Storage
            StorageType.WEBDAV -> Icons.Default.Cloud
            StorageType.ADB -> Icons.Default.Terminal
            StorageType.LOCAL_SAF -> Icons.Default.FolderSpecial
            null -> Icons.Default.Smartphone
        }
    }

    private fun handlePathNavigation(newPath: String) {
        if (isLeftActive) {
            if (isLeftRemote) leftRemotePath = newPath else leftCurrentDir = File(newPath)
        } else {
            if (isRightRemote) rightRemotePath = newPath else rightCurrentDir = File(newPath)
        }
        loadFiles(isLeftActive)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else { refreshAll() }
        } else {
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                refreshAll()
            } else { requestPermissionLauncher.launch(permissions) }
        }
    }

    private fun refreshAll() {
        loadFiles(true)
        loadFiles(false)
    }

    private fun loadFiles(isLeft: Boolean) {
        val isRemote = if(isLeft) isLeftRemote else isRightRemote
        val storage = if(isLeft) leftStorage else rightStorage
        val remotePath = if(isLeft) leftRemotePath else rightRemotePath
        val localDir = if(isLeft) leftCurrentDir else rightCurrentDir

        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                try {
                    if (isRemote && storage != null) {
                        if (storage.type == StorageType.LOCAL_SAF) {
                            listSAF(storage, remotePath)
                        } else {
                            Log.d("Network", "Listing remote files for ${storage.type} at $remotePath")
                            val list = NetworkManager.listFiles(storage, remotePath, this@MainActivity)
                            val result = mutableListOf<Any>()
                            if (remotePath != "/" && remotePath != (storage.rootPath.ifEmpty { "/" })) {
                                result.add(RemoteFile("..", "..", true, 0, 0))
                            }
                            result.addAll(list)
                            result
                        }
                    } else {
                        val result = mutableListOf<Any>()
                        if (localDir.absolutePath != "/") {
                            val parent = localDir.parentFile ?: File("/")
                            result.add(FileItem(parent, "..", true, 0, 0))
                        }

                        val list = try { localDir.listFiles() } catch (e: Exception) { null }
                        if (list != null) {
                            val fileItems = list.filter { showHiddenFiles || !it.name.startsWith(".") }.map {
                                FileItem(it, it.name, it.isDirectory, if(it.isDirectory) 0 else it.length(), it.lastModified())
                            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                            result.addAll(fileItems)
                        } else if (shizukuActive) {
                            Log.d("Shizuku", "Native fallback for ${localDir.absolutePath}")
                            val shizukuFiles = ShizukuManager.listFiles(localDir.absolutePath)
                            val shizukuItems = shizukuFiles.filter { showHiddenFiles || !it.name.startsWith(".") }.map {
                                FileItem(File(localDir, it.name), it.name, it.isDirectory, it.size, 0)
                            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                            result.addAll(shizukuItems)
                        }
                        result
                    }
                } catch (e: Exception) {
                    Log.e("Network", "Error listing files", e)
                    emptyList()
                }
            }
            
            if (isLeft) { 
                leftFileList.clear()
                leftFileList.addAll(files) 
            } else { 
                rightFileList.clear()
                rightFileList.addAll(files) 
            }
        }
    }

    private fun listSAF(storage: NetworkStorage, path: String): List<Any> {
        val treeUri = Uri.parse(storage.host)
        var targetDir: DocumentFile? = DocumentFile.fromTreeUri(this, treeUri)
        
        if (path != "/") {
            val subPaths = path.trim('/').split("/")
            for (p in subPaths) {
                targetDir = targetDir?.findFile(p)
            }
        }

        val result = mutableListOf<Any>()
        if (path != "/") result.add(RemoteFile("..", "..", true, 0, 0))

        targetDir?.listFiles()?.forEach { file: DocumentFile ->
            val name = file.name ?: "Unknown"
            if (showHiddenFiles || !name.startsWith(".")) {
                result.add(RemoteFile(
                    name = name,
                    path = if (path == "/") "/$name" else "$path/$name",
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified()
                ))
            }
        }
        return result
    }

    private fun connectToStorage(storage: NetworkStorage, isLeft: Boolean) {
        if (isLeft) {
            leftStorage = storage
            isLeftRemote = true
            leftRemotePath = storage.rootPath.ifEmpty { "/" }
        } else {
            rightStorage = storage
            isRightRemote = true
            rightRemotePath = storage.rootPath.ifEmpty { "/" }
        }
        loadFiles(isLeft)
    }

    private fun handleFileClick(item: Any, isLeft: Boolean) {
        isLeftActive = isLeft 
        if (item is FileItem && item.name == "..") {
            val current = if(isLeft) leftCurrentDir else rightCurrentDir
            val parent = current.parentFile ?: File("/")
            if (isLeft) leftCurrentDir = parent else rightCurrentDir = parent
            loadFiles(isLeft)
        } else if (item is RemoteFile && item.name == "..") {
            val currentPath = if (isLeft) leftRemotePath else rightRemotePath
            val newPath = if (currentPath.count { it == '/' } <= 1) "/" else currentPath.substringBeforeLast("/")
            if (isLeft) leftRemotePath = newPath else rightRemotePath = newPath
            loadFiles(isLeft)
        } else if (item is FileItem && item.isDirectory) {
            if (isLeft) leftCurrentDir = item.file else rightCurrentDir = item.file
            loadFiles(isLeft)
        } else if (item is RemoteFile && item.isDirectory) {
            val currentPath = if (isLeft) leftRemotePath else rightRemotePath
            val newPath = if (currentPath == "/") "/${item.name}" else "$currentPath/${item.name}"
            if (isLeft) leftRemotePath = newPath else rightRemotePath = newPath
            loadFiles(isLeft)
        } else if (item is FileItem) {
            handleLocalFileClick(item)
        } else if (item is RemoteFile) {
            val storage = if(isLeft) leftStorage else rightStorage
            if (storage != null) {
                if (storage.type == StorageType.LOCAL_SAF) {
                    Toast.makeText(this@MainActivity, "SAF 存储暂不支持直接编辑", Toast.LENGTH_SHORT).show()
                } else {
                    downloadRemoteFile(item, storage)
                }
            }
        }
    }

    private fun handleLocalFileClick(item: FileItem) {
        val file = item.file
        val ext = file.extension.lowercase()
        
        lifecycleScope.launch {
            val isText = withContext(Dispatchers.IO) { FileUtils.isTextFile(file) }
            val fileSize = file.length()
            
            if (isText && fileSize < 5 * 1024 * 1024) {
                openEditor(item, false)
            } else if (ext == "zip" || ext == "apk" || ext == "rar") {
                openArchiveBrowser(item)
            } else {
                // 大型文件或二进制文件，交给系统处理
                FileUtils.openWithSystem(this@MainActivity, file)
            }
        }
    }

    private fun downloadRemoteFile(remoteFile: RemoteFile, storage: NetworkStorage) {
        val localDest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), remoteFile.name)
        isTransferring = true
        transferMessage = "正在下载: ${remoteFile.name}"
        lifecycleScope.launch {
            val success = NetworkManager.downloadFile(storage, remoteFile, localDest, this@MainActivity) { transferProgress = it }
            isTransferring = false
            if (success) { 
                refreshAll()
                // 下载后尝试智能打开
                handleLocalFileClick(FileItem(localDest, localDest.name, false, localDest.length(), localDest.lastModified()))
            }
        }
    }

    private fun handleFileUpload(uri: Uri) {
        val storage = (if(isLeftActive) leftStorage else rightStorage) ?: return
        val path = if(isLeftActive) leftRemotePath else rightRemotePath
        val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}")
        contentResolver.openInputStream(uri)?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
        
        isTransferring = true
        transferMessage = "正在上传..."
        lifecycleScope.launch {
            val success = NetworkManager.uploadFile(storage, tempFile, path, this@MainActivity) { transferProgress = it }
            isTransferring = false
            tempFile.delete()
            if (success) refreshAll()
        }
    }

    private fun handleFileAction(action: String, item: Any) {
        lifecycleScope.launch {
            when (action) {
                "delete" -> {
                    if (item is FileItem) withContext(Dispatchers.IO) { FileUtils.deleteFile(item.file) }
                    refreshAll()
                }
                "copy", "download" -> {
                    if (item is FileItem) {
                        val targetDir = if (isLeftActive) rightCurrentDir else leftCurrentDir
                        isTransferring = true
                        transferMessage = "正在复制..."
                        withContext(Dispatchers.IO) { FileUtils.copyFile(item.file, targetDir) }
                        isTransferring = false
                    } else if (item is RemoteFile) {
                        val storage = if (isLeftActive) leftStorage else rightStorage
                        if (storage != null) downloadRemoteFile(item, storage)
                    }
                    refreshAll()
                }
                "extract" -> {
                    if (item is FileItem) {
                        val dest = File(item.file.parentFile, item.name.substringBeforeLast("."))
                        dest.mkdirs()
                        isTransferring = true
                        withContext(Dispatchers.IO) { ArchiveUtils.extractAll(item.file, dest) }
                        isTransferring = false
                    }
                    refreshAll()
                }
                "compress" -> {
                    if (item is FileItem) {
                        val dest = File(item.file.parentFile, "${item.name}.zip")
                        isTransferring = true
                        withContext(Dispatchers.IO) { ArchiveUtils.compress(listOf(item.file), dest) }
                        isTransferring = false
                    }
                    refreshAll()
                }
                "edit" -> if(item is FileItem) openEditor(item, false)
                "hex" -> if(item is FileItem) openEditor(item, true)
            }
        }
    }

    private fun openArchiveBrowser(item: FileItem) {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { ArchiveUtils.getArchiveEntries(item.file) }
            archiveFile = item.file
            archiveEntries = entries
            showArchiveBrowser = true
        }
    }

    private fun openEditor(item: FileItem, isHex: Boolean) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("file_path", item.file.absolutePath)
            putExtra("is_hex", isHex)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualPaneExplorerScreen(
    leftFiles: List<Any>,
    rightFiles: List<Any>,
    leftPath: String,
    rightPath: String,
    activePath: String,
    activeStorageName: String,
    activeStorageIcon: ImageVector,
    isLeftActive: Boolean,
    shizukuActive: Boolean,
    showHidden: Boolean,
    onPanelClick: (Boolean) -> Unit,
    onFileClick: (Any, Boolean) -> Unit,
    onFileLongClick: (Any, Boolean) -> Unit,
    onPathConfirm: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleHidden: () -> Unit,
    onSortClick: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenDrawer: () -> Unit,
    onUpload: () -> Unit
) {
    var pathInput by remember(activePath) { mutableStateOf(activePath) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(activeStorageIcon, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(activeStorageName, style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") } },
                    actions = {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
                        IconButton(onClick = onToggleHidden) { Icon(if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) }
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("搜索") }, onClick = { expanded = false }, leadingIcon = { Icon(Icons.Default.Search, null) })
                                DropdownMenuItem(text = { Text("排序") }, onClick = { onSortClick(); expanded = false }, leadingIcon = { Icon(Icons.Default.Sort, null) })
                                DropdownMenuItem(text = { Text("上传到此") }, onClick = { onUpload(); expanded = false }, leadingIcon = { Icon(Icons.Default.Upload, null) })
                                DropdownMenuItem(text = { Text("Shizuku") }, onClick = { onRequestShizuku(); expanded = false }, leadingIcon = { Icon(Icons.Default.Shield, null, tint = if(shizukuActive) Color.Green else Color.Gray) })
                            }
                        }
                    }
                )
                TextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onPathConfirm(pathInput)
                        focusManager.clearFocus()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isLeftActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                onPanelClick(true)
                            }
                        }
                    }
            ) {
                FilePanel(leftFiles, leftPath, true, onFileClick, onFileLongClick)
            }
            Divider(modifier = Modifier.width(1.dp).fillMaxHeight())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (!isLeftActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                onPanelClick(false)
                            }
                        }
                    }
            ) {
                FilePanel(rightFiles, rightPath, false, onFileClick, onFileLongClick)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FilePanel(
    files: List<Any>, 
    path: String, 
    isLeft: Boolean, 
    onFileClick: (Any, Boolean) -> Unit, 
    onFileLongClick: (Any, Boolean) -> Unit
) {
    val state = rememberLazyListState()
    LaunchedEffect(path) { state.scrollToItem(0) }

    AnimatedContent(
        targetState = files,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f) togetherWith
            fadeOut(animationSpec = tween(200))
        },
        modifier = Modifier.fillMaxSize()
    ) { currentFiles ->
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(items = currentFiles) { item ->
                when(item) {
                    is FileItem -> FileListItem(item, onClick = { onFileClick(item, isLeft) }, onLongClick = { onFileLongClick(item, isLeft) })
                    is RemoteFile -> RemoteFileListItem(item, onClick = { onFileClick(item, isLeft) }, onLongClick = { onFileLongClick(item, isLeft) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(item: FileItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val formattedSize = remember(item.size) { item.getFormattedSize() }
    val icon = getFileIcon(item.name, item.isDirectory)
    
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (!item.isDirectory && item.name != "..") Text(formattedSize, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteFileListItem(item: RemoteFile, onClick: () -> Unit, onLongClick: () -> Unit) {
    val icon = getFileIcon(item.name, item.isDirectory)
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (!item.isDirectory && item.name != "..") Text(FileUtils.formatFileSize(item.size), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

private fun getFileIcon(name: String, isDir: Boolean): ImageVector {
    if (isDir) return Icons.Default.Folder
    val ext = name.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Unarchive
        "apk" -> Icons.Default.Android
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov" -> Icons.Default.VideoLibrary
        "mp3", "wav", "flac", "ogg" -> Icons.Default.AudioFile
        "txt", "md", "log" -> Icons.Default.Description
        "py", "cpp", "c", "h", "java", "kt", "js", "ts", "html", "css", "json", "xml", "smali" -> Icons.Default.Code
        "pdf" -> Icons.Default.PictureAsPdf
        else -> Icons.Outlined.Description
    }
}

@Composable
fun SortDialog(currentSort: String, onDismiss: () -> Unit, onSort: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("排序方式") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSort("name") }) {
                RadioButton(selected = currentSort == "name", onClick = { onSort("name") })
                Text("按名称")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSort("size") }) {
                RadioButton(selected = currentSort == "size", onClick = { onSort("size") })
                Text("按大小")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSort("time") }) {
                RadioButton(selected = currentSort == "time", onClick = { onSort("time") })
                Text("按时间")
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun FileActionMenu(fileItem: Any, isRemote: Boolean, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    val name = if(fileItem is FileItem) fileItem.name else if(fileItem is RemoteFile) fileItem.name else ""
    if (name == "..") return
    
    AlertDialog(onDismissRequest = onDismiss, title = { Text(name) }, text = {
        Column {
            if (!isRemote) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { onAction("edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text("十六进制查看") }, onClick = { onAction("hex") }, leadingIcon = { Icon(Icons.Default.Code, null) })
                DropdownMenuItem(text = { Text("压缩为ZIP") }, onClick = { onAction("compress") }, leadingIcon = { Icon(Icons.Default.Compress, null) })
                if (name.endsWith(".zip") || name.endsWith(".apk")) DropdownMenuItem(text = { Text("解压") }, onClick = { onAction("extract") }, leadingIcon = { Icon(Icons.Default.Unarchive, null) })
            } else {
                DropdownMenuItem(text = { Text("下载并查看") }, onClick = { onAction("download") }, leadingIcon = { Icon(Icons.Default.Visibility, null) })
                DropdownMenuItem(text = { Text("下载到本地") }, onClick = { onAction("download") }, leadingIcon = { Icon(Icons.Default.Download, null) })
            }
            Divider()
            DropdownMenuItem(text = { Text("删除", color = Color.Red) }, onClick = { onAction("delete") }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) })
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
fun AddStorageDialog(existingStorage: NetworkStorage? = null, onDismiss: () -> Unit, onAdd: (NetworkStorage) -> Unit) {
    var name by remember { mutableStateOf(existingStorage?.name ?: "") }
    var host by remember { mutableStateOf(existingStorage?.host ?: "") }
    var port by remember { mutableStateOf(existingStorage?.port?.toString() ?: "22") }
    var user by remember { mutableStateOf(existingStorage?.username ?: "") }
    var pass by remember { mutableStateOf(existingStorage?.password ?: "") }
    var rootPath by remember { mutableStateOf(existingStorage?.rootPath ?: "/") }
    var type by remember { mutableStateOf(existingStorage?.type ?: StorageType.SFTP) }
    
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if(existingStorage==null)"添加网络存储" else "编辑网络存储") }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StorageType.values().filter { it != StorageType.LOCAL_SAF }.forEach {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(selected = type == it, onClick = { type = it; port = when(it){ StorageType.SFTP->"22"; StorageType.FTP->"21"; StorageType.SMB->"445"; else->"22" } })
                        Text(it.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            TextField(value = host, onValueChange = { host = it }, label = { Text("Host (IP)") })
            TextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
            TextField(value = rootPath, onValueChange = { rootPath = it }, label = { Text("Path (可选, 如 /Shared)") })
            if(type != StorageType.ADB) {
                TextField(value = user, onValueChange = { user = it }, label = { Text("User") })
                TextField(value = pass, onValueChange = { pass = it }, label = { Text("Pass") })
            }
        }
    }, confirmButton = { 
        Button(
            enabled = name.isNotBlank() && host.isNotBlank(),
            onClick = { onAdd(NetworkStorage(name, type, host, port.toIntOrNull()?:22, user, pass, rootPath)) }
        ) { Text("保存") } 
    })
}

@Composable
fun TransferProgressDialog(message: String, progress: Float) {
    Dialog(onDismissRequest = {}) {
        Card {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(message)
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ArchiveBrowserDialog(
    archiveFile: File,
    entries: List<ArchiveEntry>,
    onDismiss: () -> Unit,
    onExtract: (ArchiveEntry) -> Unit,
    onExtractAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(archiveFile.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onExtractAll) { Text("全部解压") }
                }
                Divider(Modifier.padding(vertical = 8.dp))
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无法读取压缩包内容", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(entries.size) { idx ->
                            val entry = entries[idx]
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onExtract(entry) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (entry.isDirectory) Icons.Default.Folder else getFileIcon(entry.name, false),
                                    null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    if (!entry.isDirectory) Text(FileUtils.formatFileSize(entry.uncompressedSize), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("关闭") }
            }
        }
    }
}
