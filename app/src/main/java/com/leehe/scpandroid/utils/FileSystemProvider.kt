package com.leehe.scpandroid.utils

import android.content.Context
import java.io.File

/**
 * 统一文件系统抽象 — 参考 MT 管理器架构
 * 三种实现：JavaFile → Shizuku → Dhizuku，自动降级
 */
interface FileSystemProvider {
    /** 列出目录内容，失败返回 null（触发降级） */
    fun listFiles(dir: File): List<FileInfo>?
}

data class FileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long = 0
)

object FileSystemManager {
    private var shizukuProvider: FileSystemProvider? = null
    private var dhizukuProvider: FileSystemProvider? = null
    private var javaProvider: FileSystemProvider? = null

    /**
     * 级联降级文件系统：JavaFile → Shizuku → Dhizuku → Root
     * Root 放最后，避免启动时 Magisk 授权弹窗阻塞
     */
    fun listFiles(dir: File): List<FileInfo> {
        // 1. JavaFile + Runtime.exec + /proc/mounts（无任何外部依赖，最快）
        JavaFileProvider.listFiles(dir)?.let { return it }
        // 2. Shizuku（ADB 可开启）
        if (ShizukuManager.isShizukuAvailable()) {
            ShizukuFileProvider.listFiles(dir)?.let { return it }
        }
        // 3. Dhizuku（Device Owner 方式）
        if (DhizukuManager.isAvailable()) {
            DhizukuFileProvider.listFiles(dir)?.let { return it }
        }
        // 4. Root（最后才尝试，会触发 Magisk 弹窗）
        if (RootFileProvider.isAvailable()) {
            RootFileProvider.listFiles(dir)?.let { return it }
        }
        return emptyList()
    }

    /**
     * 把 root 保护目录的文件拷到可读位置
     * 用于在 root 权限下打开 /data/data/xxx 里的文件
     */
    fun copyToReadableCache(source: File, cacheDir: File): File? {
        if (!RootFileProvider.isAvailable()) return null
        val dest = File(cacheDir, source.name)
        return if (RootFileProvider.copyToReadable(source.absolutePath, dest.absolutePath)) dest else null
    }

    fun init(context: Context) {
        javaProvider = JavaFileProvider
        shizukuProvider = ShizukuFileProvider
        DhizukuManager.init(context)
        dhizukuProvider = DhizukuFileProvider
    }
}

/** 标准 Java File API + /proc/mounts 绕过 SELinux 限制 */
object JavaFileProvider : FileSystemProvider {
    override fun listFiles(dir: File): List<FileInfo>? {
        // 1. 先试 Java File API
        val javaList = try { dir.listFiles() } catch (e: Exception) { null }
        if (javaList != null) {
            return javaList.filter { it.name != "." && it.name != ".." }.map {
                FileInfo(it.name, it.isDirectory, if(it.isDirectory) 0 else it.length(), it.lastModified())
            }
        }
        // 2. SELinux 可能阻止了直接访问(如 /)，从 /proc/mounts 发现目录
        if (dir.absolutePath == "/") return listRootViaMounts()
        return null
    }

    /**
     * 列出根目录内容
     * 1. 优先尝试 Runtime.exec("ls -alF /") — 无 root 也能列出所有文件和目录
     * 2. 失败时回退到 /proc/mounts + known 列表（只有目录，不含根目录下的普通文件）
     */
    private fun listRootViaMounts(): List<FileInfo> {
        // 尝试用 ls 命令列出根目录（不需要 root）
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/ls", "-alF", "/"))
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (out.isNotBlank() && !out.contains("Permission denied")) {
                val result = parseLsOutput(out)
                if (result.isNotEmpty()) return result
            }
        } catch (_: Exception) {}

        // 回退：/proc/mounts + 已知目录（只能列出目录，不含根目录下的普通文件）
        val dirs = linkedSetOf<String>()
        try {
            val mounts = java.io.File("/proc/mounts").readText()
            for (line in mounts.split("\n")) {
                val parts = line.split(" ")
                if (parts.size >= 2) {
                    val topDir = parts[1].removePrefix("/").substringBefore("/")
                    if (topDir.isNotEmpty()) dirs.add(topDir)
                }
            }
        } catch (_: Exception) {}

        val known = listOf("acct", "apex", "cache", "config", "d", "data", "debug_ramdisk",
            "dev", "etc", "init", "linkerconfig", "mnt", "odm", "oem", "proc", "product",
            "res", "sdcard", "storage", "sys", "system", "system_ext", "vendor")
        dirs.addAll(known)

        return dirs.map { name ->
            FileInfo(name, isDirectory = true, size = 0)
        }.sortedBy { it.name }.distinctBy { it.name }
    }
}

/** Shizuku Shell 实现（ADB/Root 启动后可用） */
object ShizukuFileProvider : FileSystemProvider {
    override fun listFiles(dir: File): List<FileInfo>? {
        if (!ShizukuManager.isShizukuAvailable()) return null
        val result = ShizukuManager.runCommand("ls -alF \"${dir.absolutePath}\"")
        if (result.startsWith("Error") || result.startsWith("Exception")) return null
        return parseLsOutput(result)
    }
}

/** Dhizuku Shell 实现（Device Owner 方式，无需 ADB/Root） */
object DhizukuFileProvider : FileSystemProvider {
    override fun listFiles(dir: File): List<FileInfo>? {
        if (!DhizukuManager.isAvailable()) return null
        val result = DhizukuManager.runCommand("ls -alF \"${dir.absolutePath}\"")
        if (result.startsWith("Error") || result.startsWith("Exception")) return null
        return parseLsOutput(result)
    }
}

/** Root Shell 实现 — 持久 su 会话，只授权一次 */
object RootFileProvider : FileSystemProvider {
    @Volatile private var shell: Process? = null
    @Volatile private var available: Boolean? = null

    @Synchronized
    fun isAvailable(): Boolean {
        available?.let { return it }
        return try {
            val p = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            p.outputStream.write("echo ROOT_OK\n".toByteArray())
            p.outputStream.flush()
            val reader = p.inputStream.bufferedReader()
            var ok = false
            var line: String?; var safety = 0
            while (reader.readLine().also { line = it } != null && safety < 100) {
                safety++
                if (line!!.contains("ROOT_OK")) { ok = true; break }
            }
            if (ok) { shell = p; available = true }
            else { p.destroy(); available = false }
            ok
        } catch (e: Exception) {
            available = false; false
        }
    }

    override fun listFiles(dir: File): List<FileInfo>? {
        return execAndRead("ls -alF \"${dir.absolutePath}\"")?.let { parseLsOutput(it) }
    }

    /** 用 root 权限拷贝文件到可读位置 */
    fun copyToReadable(srcPath: String, destPath: String): Boolean {
        val result = execAndRead("cp \"$srcPath\" \"$destPath\" && echo OK || echo FAIL")
        return result?.contains("OK") == true && !result.contains("FAIL")
    }

    @Synchronized
    fun execAndRead(cmd: String): String? {
        val p = shell ?: return null
        try {
            val marker = "___END_${System.nanoTime()}___"
            p.outputStream.write("$cmd; echo $marker\n".toByteArray())
            p.outputStream.flush()
            val sb = StringBuilder()
            val reader = p.inputStream.bufferedReader()
            var line: String?; var safety = 0
            while (reader.readLine().also { line = it } != null && safety < 50000) {
                safety++
                val l = line!!
                if (l.contains(marker)) break
                sb.appendLine(l)
            }
            return sb.toString().takeUnless { it.isBlank() || it.contains("Permission denied") }
        } catch (e: Exception) {
            android.util.Log.w("RootFS", "Shell 断开: ${e.message}")
            p.destroy(); shell = null; available = null; return null
        }
    }
}

private val LS_SPLIT_RE = Regex("\\s+")
private val LS_MONTH = setOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

internal fun parseLsOutput(output: String): List<FileInfo> {
    return output.split("\n")
        .filter { it.isNotBlank() && !it.startsWith("total") && !it.contains("Permission denied") }
        .mapNotNull { line ->
            try {
                val parts = line.split(LS_SPLIT_RE)
                if (parts.size < 7) return@mapNotNull null
                // 固定列：0=权限 1=链接数 2=所有者 3=组 4=大小
                // 日期列：parts[5] 可能是 "Jan" 或 "2024-01-15"
                // 如果 parts[5] 是月份缩写，日期是 3 列（月/日/时间），否则是 2 列
                val isMonth = parts[5].length == 3 && parts[5] in LS_MONTH
                val nameStart = if (isMonth) 8 else 7
                if (parts.size <= nameStart) return@mapNotNull null
                var name = parts.drop(nameStart).joinToString(" ")
                if (name == "." || name == "..") return@mapNotNull null
                val isSymlink = line.startsWith("l")
                val arrowIdx = name.indexOf(" -> ")
                val target = if (arrowIdx > 0) name.substring(arrowIdx + 4) else null
                if (arrowIdx > 0) name = name.substring(0, arrowIdx)
                name = name.removeSuffix("@").removeSuffix("*").removeSuffix("/")
                if (name == "." || name == "..") return@mapNotNull null
                val isDir = line.startsWith("d") || (isSymlink && target != null && target.endsWith("/"))
                val size = parts[4].toLongOrNull() ?: 0L
                FileInfo(name, isDir, size)
            } catch (e: Exception) { null }
        }
}
