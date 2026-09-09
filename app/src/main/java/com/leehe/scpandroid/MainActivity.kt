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
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.leehe.scpandroid.models.*
import com.leehe.scpandroid.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var pathInput: EditText
    private lateinit var leftAdapter: FileListAdapter
    private lateinit var rightAdapter: FileListAdapter

    private var showHidden = false
    private var shizukuActive = false
    private var isLeftActive = true

    private var leftDir = Environment.getExternalStorageDirectory()
    private var rightDir = File("/")

    private var leftRemotePath = "/"; private var rightRemotePath = "/"
    private var leftStorage: NetworkStorage? = null; private var rightStorage: NetworkStorage? = null
    private var isLeftRemote = false; private var isRightRemote = false
    private var savedStorages = mutableListOf<NetworkStorage>()

    private var isTransferring = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) refreshAll()
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleFileUpload(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定视图
        drawer = findViewById(R.id.drawer_layout)
        toolbar = findViewById(R.id.toolbar)
        pathInput = findViewById(R.id.path_input)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        val leftPanel = findViewById<FrameLayout>(R.id.left_panel)
        val rightPanel = findViewById<FrameLayout>(R.id.right_panel)

        // Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_menu)
        }
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> { refreshAll(); true }
                R.id.action_hidden -> { showHidden = !showHidden; item.setIcon(if (showHidden) R.drawable.ic_eye_on else R.drawable.ic_eye_off); refreshAll(); true }
                R.id.action_sort -> { showSortDialog(); true }
                R.id.action_upload -> { handleUpload(); true }
                R.id.action_shizuku -> { requestShizuku(); true }
                else -> false
            }
        }
        toolbar.inflateMenu(R.menu.toolbar_menu)

        // 路径输入
        pathInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                handlePathNavigation(pathInput.text.toString())
                true
            } else false
        }

        // RecyclerView 面板
        leftAdapter = FileListAdapter()
        rightAdapter = FileListAdapter()
        listOf(leftPanel to leftAdapter, rightPanel to rightAdapter).forEach { (panel, adapter) ->
            val rv = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                setAdapter(adapter)
                isVerticalScrollBarEnabled = true
                itemAnimator = null
            }
            panel.addView(rv)
        }
        leftPanel.setOnClickListener { isLeftActive = true; updatePanelSelection() }
        rightPanel.setOnClickListener { isLeftActive = false; updatePanelSelection() }

        leftAdapter.onFileClick = { fileClick(it, true) }
        leftAdapter.onFileLongClick = { fileLongClick(it, true) }
        rightAdapter.onFileClick = { fileClick(it, false) }
        rightAdapter.onFileLongClick = { fileLongClick(it, false) }

        // 抽屉
        setupDrawer(navView)

        // 初始化
        ShizukuManager.init()
        lifecycleScope.launch {
            val storages = withContext(Dispatchers.IO) {
                FileSystemManager.init(this@MainActivity)
                StoragePrefs.loadStorages(this@MainActivity)
            }
            savedStorages = storages.toMutableList()
            shizukuActive = ShizukuManager.isShizukuAvailable()
            updateDrawerItems()
        }
        // 先检查权限，通过后再加载文件
        checkPermissions()
    }

    private fun setupDrawer(nav: NavigationView) {
        nav.menu.clear()
        updateDrawerItems()
        nav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawers()
            lifecycleScope.launch {
                val title = item.title.toString()
                when {
                    title == "内部存储" -> {
                        val d = Environment.getExternalStorageDirectory()
                        if (isLeftActive) { isLeftRemote = false; leftDir = d } else { isRightRemote = false; rightDir = d }
                        refreshAll()
                    }
                    title == "系统根目录" -> {
                        if (isLeftActive) { isLeftRemote = false; leftDir = File("/") } else { isRightRemote = false; rightDir = File("/") }
                        refreshAll()
                    }
                    title == "添加网络存储" -> showAddStorageDialog(null)
                    title == "授权本地目录" -> launchSaf()
                    title.startsWith("删除 ") -> {
                        savedStorages.removeAll { it.name == title.removePrefix("删除 ") }
                        StoragePrefs.saveStorages(this@MainActivity, savedStorages)
                        updateDrawerItems()
                    }
                    title == "编辑" -> { /* handled in click */ }
                    title.contains("色模式") -> { toggleTheme(); updateDrawerItems() }
                    else -> {
                        savedStorages.find { it.name == title }?.let { s ->
                            if (isLeftActive) { isLeftRemote = true; leftStorage = s; leftRemotePath = s.rootPath }
                            else { isRightRemote = true; rightStorage = s; rightRemotePath = s.rootPath }
                            refreshAll()
                        }
                    }
                }
            }
            true
        }
    }

    private fun updateDrawerItems() {
        val nav = findViewById<NavigationView>(R.id.nav_view)
        val menu = nav.menu; menu.clear()
        menu.add(0, 1, 0, "内部存储").setIcon(R.drawable.ic_folder)
        menu.add(0, 2, 0, "系统根目录").setIcon(R.drawable.ic_folder)
        menu.add(0, 0, 0, "———").isEnabled = false
        savedStorages.forEachIndexed { i, s ->
            val item = menu.add(1, 100 + i, 0, s.name)
            item.setIcon(when (s.type) { StorageType.SMB -> R.drawable.ic_archive; StorageType.WEBDAV -> R.drawable.ic_archive; StorageType.ADB -> R.drawable.ic_code; else -> R.drawable.ic_file })
            val sub = item.subMenu
            sub?.add(2, 200 + i, 0, "编辑")
            sub?.add(2, 300 + i, 0, "删除 ${s.name}")
        }
        menu.add(0, 3, 0, "添加网络存储").setIcon(R.drawable.ic_code)
        menu.add(0, 4, 0, when(themeMode){"system"->"深色模式";"dark"->"浅色模式";else->"跟随系统"}).setIcon(R.drawable.ic_file)
    }

    private fun updatePanelSelection() {
        val activeColor = getColor(com.leehe.scpandroid.R.color.panel_active)
        val leftBg = findViewById<FrameLayout>(R.id.left_panel)
        val rightBg = findViewById<FrameLayout>(R.id.right_panel)
        leftBg.setBackgroundColor(if (isLeftActive) activeColor else 0x00000000.toInt())
        rightBg.setBackgroundColor(if (!isLeftActive) activeColor else 0x00000000.toInt())
    }

    // ==================== Permissions ====================

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

    // ==================== File Operations ====================

    private fun refreshAll() { loadFiles(true); loadFiles(false) }

    private fun loadFiles(isLeft: Boolean) {
        val dir = if (isLeft) leftDir else rightDir
        val adapter = if (isLeft) leftAdapter else rightAdapter
        val isRemote = if (isLeft) isLeftRemote else isRightRemote
        val storage = if (isLeft) leftStorage else rightStorage
        val remotePath = if (isLeft) leftRemotePath else rightRemotePath

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                if (isRemote && storage != null) {
                    val list = NetworkManager.listFiles(storage, remotePath, this@MainActivity)
                    val result = mutableListOf<Any>()
                    if (remotePath != "/" && remotePath != storage.rootPath) result.add(RemoteFile("..", "..", true, 0, 0))
                    result.addAll(list); result
                } else {
                    val result = mutableListOf<Any>()
                    if (dir.absolutePath != "/") result.add(FileItem(dir.parentFile ?: File("/"), "..", true, 0, 0))
                    val files = FileSystemManager.listFiles(dir)
                    result.addAll(files.filter { showHidden || !it.name.startsWith(".") }.map {
                        FileItem(File(dir, it.name), it.name, it.isDirectory, it.size, it.lastModified)
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
                    result
                }
            }
            adapter.items = items
            if (isLeft == isLeftActive) {
                val storageName = if (isLeftRemote || isRightRemote) {
                    (if (isLeft) leftStorage else rightStorage)?.name
                } else null
                val path = if (isRemote) remotePath else dir.absolutePath
                updatePath(path, storageName)
            }
        }
    }

    private fun handlePathNavigation(path: String) {
        if (isLeftActive) {
            if (isLeftRemote) leftRemotePath = path else leftDir = File(path)
        } else {
            if (isRightRemote) rightRemotePath = path else rightDir = File(path)
        }
        loadFiles(isLeftActive)
    }

    private fun fileClick(item: Any, isLeft: Boolean) {
        isLeftActive = isLeft; updatePanelSelection()
        when {
            item is FileItem && item.name == ".." -> {
                if (isLeft) leftDir = leftDir.parentFile ?: File("/") else rightDir = rightDir.parentFile ?: File("/")
                loadFiles(isLeft)
            }
            item is RemoteFile && item.name == ".." -> {
                val p = if (isLeft) leftRemotePath else rightRemotePath
                val newPath = if (p.count { it == '/' } <= 1) "/" else p.substringBeforeLast("/")
                if (isLeft) leftRemotePath = newPath else rightRemotePath = newPath
                loadFiles(isLeft)
            }
            item is FileItem && item.isDirectory -> {
                if (isLeft) leftDir = item.file else rightDir = item.file
                loadFiles(isLeft)
            }
            item is RemoteFile && item.isDirectory -> {
                val cur = if (isLeft) leftRemotePath else rightRemotePath
                val new = if (cur == "/") "/${item.name}" else "$cur/${item.name}"
                if (isLeft) leftRemotePath = new else rightRemotePath = new
                loadFiles(isLeft)
            }
            item is FileItem -> handleOpenFile(item)
            item is RemoteFile -> {
                val s = if (isLeft) leftStorage else rightStorage
                if (s != null) downloadRemote(item, s)
            }
        }
    }

    private fun fileLongClick(item: Any, isLeft: Boolean) {
        isLeftActive = isLeft; updatePanelSelection()
        val name = when (item) { is FileItem -> item.name; is RemoteFile -> item.name; else -> "" }
        if (name == "..") return

        val items = mutableListOf<String>().apply {
            if (item is FileItem) {
                add("打开方式...")
                add("编辑")
                add("十六进制查看")
                add("压缩为ZIP")
                if (name.endsWith(".zip") || name.endsWith(".apk")) add("解压")
            } else add("下载")
            add("删除")
        }
        AlertDialog.Builder(this).setTitle(name).setItems(items.toTypedArray()) { _, which ->
            when (items[which]) {
                "打开方式..." -> if (item is FileItem) handleOpenFile(item)
                "编辑" -> if (item is FileItem) openEditor(item, false)
                "十六进制查看" -> if (item is FileItem) openEditor(item, true)
                "压缩为ZIP" -> compressItem(item)
                "解压" -> extractItem(item)
                "下载" -> if (item is RemoteFile) downloadRemote(item, (if (isLeft) leftStorage else rightStorage) ?: return@setItems)
                "删除" -> confirmDelete(item)
            }
        }.show()
    }

    private fun handleOpenFile(item: FileItem) {
        val ext = item.file.extension.lowercase()
        if (ext == "apk") { showApkInfo(item.file); return }
        if (ext in setOf("zip", "rar", "7z", "jar")) { showArchiveBrowser(item.file); return }
        if (ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) { showImageViewer(item.file); return }
        lifecycleScope.launch {
            val isText = withContext(Dispatchers.IO) { FileUtils.isTextFile(item.file) }
            if (isText && item.file.length() < 5 * 1024 * 1024) openEditor(item, false)
            else {
                val options = mutableListOf<String>()
                if (isText) options.add("文本编辑")
                if (item.file.length() < 512 * 1024) options.add("十六进制查看")
                options.add("第三方应用打开")
                AlertDialog.Builder(this@MainActivity).setTitle(item.name)
                    .setItems(options.toTypedArray()) { _, i ->
                        when (options[i]) {
                            "文本编辑" -> openEditor(item, false)
                            "十六进制查看" -> openEditor(item, true)
                            "第三方应用打开" -> FileUtils.openWithSystem(this@MainActivity, item.file)
                        }
                    }.show()
            }
        }
    }

    private fun openEditor(item: FileItem, hex: Boolean = false) {
        startActivity(Intent(this, EditorActivity::class.java).apply {
            putExtra("file_path", item.file.absolutePath); putExtra("is_hex", hex)
        })
    }

    // ==================== Dialogs ====================

    private fun confirmDelete(item: Any) {
        val name = when (item) { is FileItem -> item.name; is RemoteFile -> item.name; else -> "" }
        AlertDialog.Builder(this).setTitle("确认删除").setMessage("确定要删除 \"$name\" 吗？此操作不可恢复！")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { if (item is FileItem) FileUtils.deleteFile(item.file) }
                    refreshAll()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun compressItem(item: Any) {
        if (item !is FileItem) return
        lifecycleScope.launch {
            val dest = File(item.file.parentFile, "${item.name}.zip")
            withContext(Dispatchers.IO) { ArchiveUtils.compress(listOf(item.file), dest) }
            refreshAll()
        }
    }

    private fun extractItem(item: Any) {
        if (item !is FileItem) return
        lifecycleScope.launch {
            val dest = File(item.file.parentFile, item.name.substringBeforeLast(".")).also { it.mkdirs() }
            withContext(Dispatchers.IO) { ArchiveUtils.extractAll(item.file, dest) }
            refreshAll()
        }
    }

    private fun downloadRemote(file: RemoteFile, storage: NetworkStorage) {
        val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file.name)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { NetworkManager.downloadFile(storage, file, dest, this@MainActivity) {} }
            refreshAll()
        }
    }

    // ==================== Compose Dialog Overlay ====================

    private fun showComposeDialog(content: @androidx.compose.runtime.Composable () -> Unit) {
        val cv = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent { content() }
        }
        AlertDialog.Builder(this).setView(cv).setPositiveButton("关闭") { _, _ -> }.show()
    }

    private fun showApkInfo(file: File) {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                try {
                    val pm = packageManager
                    val ai = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return@withContext null
                    val app = ai.applicationInfo ?: return@withContext null
                    app.sourceDir = file.absolutePath; app.publicSourceDir = file.absolutePath
                    AppInfo(app.loadLabel(pm).toString(), ai.packageName, app.loadIcon(pm), file.absolutePath, 0, file.length(), ai.versionName ?: "?", false)
                } catch (e: Exception) { null }
            }
            val view = layoutInflater.inflate(R.layout.dialog_apk, null)
            if (info != null) {
                view.findViewById<TextView>(R.id.apk_name).text = info.appName
                view.findViewById<TextView>(R.id.apk_pkg).text = info.packageName
                view.findViewById<TextView>(R.id.apk_ver).text = "v${info.versionName}"
                view.findViewById<TextView>(R.id.apk_size).text = FileUtils.formatFileSize(info.fileSize)
                if (info.appIcon != null) {
                    val d = info.appIcon; val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, 96, 96); d.draw(android.graphics.Canvas(bmp))
                    view.findViewById<ImageView>(R.id.apk_icon).setImageBitmap(bmp)
                }
            } else {
                view.findViewById<TextView>(R.id.apk_name).text = file.name
                view.findViewById<TextView>(R.id.apk_pkg).text = "无法读取应用信息"
            }
            AlertDialog.Builder(this@MainActivity).setTitle("APK 信息").setView(view)
                .setPositiveButton("安装") { _, _ ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    startActivity(intent)
                }
                .setNeutralButton("浏览内容") { _, _ -> showArchiveBrowser(file) }
                .setNegativeButton("关闭", null).show()
        }
    }

    private fun showArchiveBrowser(file: File) {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { ArchiveUtils.getArchiveEntries(file) }
            val adapter = ArchiveListAdapter(file, entries) { entry ->
                val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file.nameWithoutExtension)
                dest.mkdirs()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { try { ZipFile(file).extractFile(entry.second, dest.absolutePath) } catch (_: Exception) {} }
                    refreshAll()
                    Toast.makeText(this@MainActivity, "已解压到: $dest", Toast.LENGTH_LONG).show()
                }
            }
            val rv = RecyclerView(this@MainActivity).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                this.adapter = adapter
            }
            AlertDialog.Builder(this@MainActivity).setTitle(file.name)
                .setView(rv)
                .setPositiveButton("全部解压") { _, _ ->
                    val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file.nameWithoutExtension)
                    dest.mkdirs()
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { ArchiveUtils.extractAll(file, dest) }
                        refreshAll()
                        Toast.makeText(this@MainActivity, "已解压到: $dest", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("关闭", null).show()
        }
    }

    private fun showImageViewer(file: File) {
        val iv = ImageView(this)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                val opt = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opt)
                opt.inSampleSize = calculateInSampleSize(opt.outWidth, opt.outHeight, 2048)
                opt.inJustDecodeBounds = false
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opt)
            }
            iv.setImageBitmap(bmp)
        }
        AlertDialog.Builder(this).setView(iv).setNegativeButton("关闭", null).show()
    }

    private fun calculateInSampleSize(w: Int, h: Int, maxSide: Int): Int {
        var scale = 1
        while (w / scale > maxSide || h / scale > maxSide) scale *= 2
        return scale
    }

    // ==================== Sort / Upload / Transfer ====================

    private var sortBy = "name"
    private var themeMode = "system"

    private fun showSortDialog() {
        val opts = arrayOf("按名称", "按大小", "按时间")
        AlertDialog.Builder(this).setTitle("排序方式").setSingleChoiceItems(opts, when(sortBy) { "size" -> 1; "time" -> 2; else -> 0 }) { d, i ->
            sortBy = when(i) { 1 -> "size"; 2 -> "time"; else -> "name" }; d.dismiss(); refreshAll()
        }.show()
    }

    private fun handleUpload() {
        try { pickFileLauncher.launch("*/*") } catch (_: Exception) {}
    }

    private fun handleFileUpload(uri: Uri) {
        val activeStorage = if (isLeftActive) leftStorage else rightStorage
        val isLeft = isLeftActive
        if (activeStorage == null) {
            // 本地复制：将选中文件拷贝到当前目录
            val targetDir = if (isLeft) leftDir else rightDir
            val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { input.copyTo(it) }
                }
                val dest = File(targetDir, uri.lastPathSegment ?: tempFile.name)
                tempFile.renameTo(dest)
                refreshAll()
            } catch (_: Exception) {
                tempFile.delete()
            }
        } else {
            // 远程上传
            val remotePath = if (isLeft) leftRemotePath else rightRemotePath
            val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { input.copyTo(it) }
                }
                isTransferring = true
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        NetworkManager.uploadFile(activeStorage, tempFile, remotePath, this@MainActivity) {}
                    }
                    isTransferring = false
                    tempFile.delete()
                    refreshAll()
                }
            } catch (_: Exception) {
                tempFile.delete()
            }
        }
    }

    private var transferJob: kotlinx.coroutines.Job? = null

    private fun showTransferDialog(msg: String) {
        isTransferring = true
        val dialog = AlertDialog.Builder(this).setTitle(msg).setView(ProgressBar(this)).setCancelable(false).show()
        transferJob = lifecycleScope.launch {
            while (isTransferring) { kotlinx.coroutines.delay(500) }
            runOnUiThread { if (dialog.isShowing) dialog.dismiss() }
        }
    }

    // ==================== Theme Toggle ====================

    private fun toggleTheme() {
        themeMode = when (themeMode) { "dark" -> "light"; "light" -> "system"; else -> "dark" }
        val mode = when (themeMode) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun updatePath(path: String, storageName: String?) {
        val title = storageName ?: "scpAndroid"
        supportActionBar?.title = title
        supportActionBar?.subtitle = path
        pathInput.setText(path)
    }

    // ==================== Utility ====================

    private fun showAddStorageDialog(storage: NetworkStorage?) {
        val view = layoutInflater.inflate(R.layout.dialog_add_storage, null)
        val nameEt = view.findViewById<EditText>(R.id.stg_name)
        val hostEt = view.findViewById<EditText>(R.id.stg_host)
        val portEt = view.findViewById<EditText>(R.id.stg_port)
        val userEt = view.findViewById<EditText>(R.id.stg_user)
        val passEt = view.findViewById<EditText>(R.id.stg_pass)
        val pathEt = view.findViewById<EditText>(R.id.stg_path)
        val typeSpinner = view.findViewById<Spinner>(R.id.stg_type)

        storage?.let {
            nameEt.setText(it.name); hostEt.setText(it.host); portEt.setText(it.port.toString())
            userEt.setText(it.username); passEt.setText(it.password); pathEt.setText(it.rootPath)
        }

        AlertDialog.Builder(this).setTitle(if (storage == null) "添加网络存储" else "编辑网络存储").setView(view)
            .setPositiveButton("保存") { _, _ ->
                val s = NetworkStorage(nameEt.text.toString(),
                    when (typeSpinner.selectedItemPosition) { 0 -> StorageType.SFTP; 1 -> StorageType.FTP; 2 -> StorageType.SMB; 3 -> StorageType.WEBDAV; else -> StorageType.ADB },
                    hostEt.text.toString(), portEt.text.toString().toIntOrNull() ?: 22,
                    userEt.text.toString(), passEt.text.toString(), pathEt.text.toString().ifEmpty { "/" })
                if (storage != null) { val i = savedStorages.indexOf(storage); if (i >= 0) savedStorages[i] = s }
                else savedStorages.add(s)
                StoragePrefs.saveStorages(this, savedStorages); updateDrawerItems()
            }
            .setNegativeButton("取消", null).show()
    }

    private fun requestShizuku() {
        ShizukuManager.checkPermission { granted ->
            shizukuActive = granted
            if (granted) refreshAll()
            else Toast.makeText(this, "需要 Shizuku 权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSaf() {
        try { startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) } catch (_: Exception) {}
    }

}

// Archive RecyclerView adapter
class ArchiveListAdapter(
    private val file: File,
    entries: List<ArchiveEntry>,
    private val onExtract: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<ArchiveListAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.item_icon)
        val name: TextView = view.findViewById(R.id.item_name)
        val size: TextView = view.findViewById(R.id.item_size)
    }

    var items = entries
        set(v) { field = v; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.file_list_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.name.substringAfterLast("/")
        h.icon.setImageResource(if (e.isDirectory) R.drawable.ic_folder else R.drawable.ic_archive)
        h.size.text = if (!e.isDirectory) FileUtils.formatFileSize(e.uncompressedSize) else ""
        h.itemView.setOnClickListener {
            if (e.isDirectory) {
                val subEntries = try {
                    val zf = ZipFile(file)
                    val prefix = "${e.name}/"
                    zf.fileHeaders.mapNotNull { hdr ->
                        val fn = hdr.fileName.removeSuffix("/")
                        if (fn.startsWith(prefix) && fn != prefix) {
                            val rel = fn.removePrefix(prefix)
                            val isD = hdr.isDirectory || rel.contains("/")
                            ArchiveEntry(fn, isD, hdr.uncompressedSize, hdr.lastModifiedTime)
                        } else null
                    }.distinctBy { it.name }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                } catch (_: Exception) { emptyList() }
                items = subEntries
            } else {
                onExtract(e.name to e.name)
            }
        }
    }

    override fun getItemCount() = items.size
}
