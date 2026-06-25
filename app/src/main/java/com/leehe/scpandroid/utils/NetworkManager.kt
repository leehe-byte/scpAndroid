package com.leehe.scpandroid.utils

import android.content.Context
import android.util.Log
import com.leehe.scpandroid.models.NetworkStorage
import com.leehe.scpandroid.models.RemoteFile
import com.leehe.scpandroid.models.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.TransferListener
import net.schmizz.sshj.common.StreamCopier
import org.apache.commons.net.ftp.FTPClient
import com.github.sardine.SardineFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object NetworkManager {
    private const val TAG = "NetworkManager"

    suspend fun listFiles(storage: NetworkStorage, path: String, context: Context? = null): List<RemoteFile> = withContext(Dispatchers.IO) {
        try {
            when (storage.type) {
                StorageType.SFTP, StorageType.SCP -> listSFTP(storage, path)
                StorageType.FTP -> listFTP(storage, path)
                StorageType.SMB -> SmbManager.listFiles(storage, path)
                StorageType.WEBDAV -> listWebDAV(storage, path)
                StorageType.ADB -> if (context != null) listRemoteADB(context, storage, path) else emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files for ${storage.type}", e)
            emptyList()
        }
    }

    suspend fun downloadFile(
        storage: NetworkStorage,
        remoteFile: RemoteFile,
        localDest: File,
        context: Context? = null,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            localDest.parentFile?.mkdirs()
            when (storage.type) {
                StorageType.SFTP, StorageType.SCP -> downloadSFTP(storage, remoteFile, localDest, onProgress)
                StorageType.FTP -> downloadFTP(storage, remoteFile, localDest, onProgress)
                StorageType.SMB -> SmbManager.downloadFile(storage, remoteFile, localDest, onProgress)
                StorageType.ADB -> if (context != null) downloadRemoteADB(context, storage, remoteFile, localDest, onProgress) else false
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            false
        }
    }

    suspend fun uploadFile(
        storage: NetworkStorage,
        localFile: File,
        remotePath: String,
        context: Context? = null,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when (storage.type) {
                StorageType.SFTP, StorageType.SCP -> uploadSFTP(storage, localFile, remotePath, onProgress)
                StorageType.FTP -> uploadFTP(storage, localFile, remotePath, onProgress)
                StorageType.SMB -> SmbManager.uploadFile(storage, localFile, remotePath, onProgress)
                StorageType.ADB -> if (context != null) uploadRemoteADB(context, storage, localFile, remotePath, onProgress) else false
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            false
        }
    }

    private fun newSSHClient(): SSHClient {
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connectTimeout = 10000
        ssh.timeout = 30000
        return ssh
    }

    private fun listSFTP(storage: NetworkStorage, path: String): List<RemoteFile> {
        val ssh = newSSHClient()
        return try {
            ssh.connect(storage.host, storage.port)
            ssh.authPassword(storage.username, storage.password)
            val sftp = ssh.newSFTPClient()
            sftp.ls(path).map {
                RemoteFile(it.name, it.path, it.isDirectory, it.attributes.size, it.attributes.mtime * 1000L)
            }
        } finally {
            ssh.disconnect()
        }
    }

    private fun downloadSFTP(storage: NetworkStorage, remoteFile: RemoteFile, localDest: File, onProgress: (Float) -> Unit): Boolean {
        val ssh = newSSHClient()
        return try {
            ssh.connect(storage.host, storage.port)
            ssh.authPassword(storage.username, storage.password)
            val sftp = ssh.newSFTPClient()
            sftp.getFileTransfer().setTransferListener(object : TransferListener {
                override fun directory(name: String?): TransferListener = this
                override fun file(name: String?, size: Long): StreamCopier.Listener {
                    return StreamCopier.Listener { transferred ->
                        onProgress(transferred.toFloat() / remoteFile.size.coerceAtLeast(1))
                    }
                }
            })
            sftp.get(remoteFile.path, localDest.absolutePath)
            true
        } finally {
            ssh.disconnect()
        }
    }

    private fun uploadSFTP(storage: NetworkStorage, localFile: File, remotePath: String, onProgress: (Float) -> Unit): Boolean {
        val ssh = newSSHClient()
        return try {
            ssh.connect(storage.host, storage.port)
            ssh.authPassword(storage.username, storage.password)
            val sftp = ssh.newSFTPClient()
            val fileSize = localFile.length()
            sftp.getFileTransfer().setTransferListener(object : TransferListener {
                override fun directory(name: String?): TransferListener = this
                override fun file(name: String?, size: Long): StreamCopier.Listener {
                    return StreamCopier.Listener { transferred ->
                        onProgress(transferred.toFloat() / fileSize.coerceAtLeast(1))
                    }
                }
            })
            val destPath = if (remotePath.endsWith("/")) "$remotePath${localFile.name}" else "$remotePath/${localFile.name}"
            sftp.put(localFile.absolutePath, destPath)
            true
        } finally {
            ssh.disconnect()
        }
    }

    private fun listFTP(storage: NetworkStorage, path: String): List<RemoteFile> {
        val ftp = FTPClient()
        ftp.defaultTimeout = 10000
        ftp.connectTimeout = 10000
        return try {
            ftp.connect(storage.host, storage.port)
            val loginOk = if (storage.username.isEmpty()) ftp.login("anonymous", "anonymous@") else ftp.login(storage.username, storage.password)
            if (!loginOk) throw Exception("FTP 登录失败: 用户名或密码错误")
            ftp.enterLocalPassiveMode()
            ftp.listFiles(path).map {
                RemoteFile(it.name, if (path.endsWith("/")) "$path${it.name}" else "$path/${it.name}", it.isDirectory, it.size, it.timestamp.timeInMillis)
            }
        } finally {
            try { ftp.logout() } catch (_: Exception) {}
            ftp.disconnect()
        }
    }

    private fun downloadFTP(storage: NetworkStorage, remoteFile: RemoteFile, localDest: File, onProgress: (Float) -> Unit): Boolean {
        val ftp = FTPClient()
        ftp.defaultTimeout = 10000
        ftp.connectTimeout = 10000
        return try {
            ftp.connect(storage.host, storage.port)
            val loginOk = ftp.login(storage.username, storage.password)
            if (!loginOk) return false
            ftp.enterLocalPassiveMode()
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            localDest.outputStream().use { out ->
                val buf = ByteArray(8192)
                var transferred = 0L
                ftp.retrieveFile(remoteFile.path, out)
                onProgress(1.0f)
            }
            true
        } finally {
            try { ftp.logout() } catch (_: Exception) {}
            ftp.disconnect()
        }
    }

    private fun uploadFTP(storage: NetworkStorage, localFile: File, remotePath: String, onProgress: (Float) -> Unit): Boolean {
        val ftp = FTPClient()
        ftp.defaultTimeout = 10000
        ftp.connectTimeout = 10000
        return try {
            ftp.connect(storage.host, storage.port)
            val loginOk = ftp.login(storage.username, storage.password)
            if (!loginOk) return false
            ftp.enterLocalPassiveMode()
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            val remoteFilePath = if (remotePath.endsWith("/")) "$remotePath${localFile.name}" else "$remotePath/${localFile.name}"
            localFile.inputStream().use { inp -> ftp.storeFile(remoteFilePath, inp) }
            onProgress(1.0f)
            true
        } finally {
            try { ftp.logout() } catch (_: Exception) {}
            ftp.disconnect()
        }
    }

    // --- ADB Implementation ---

    private suspend fun listRemoteADB(context: Context, storage: NetworkStorage, path: String): List<RemoteFile> {
        if (storage.host == "localhost" || storage.host == "127.0.0.1" || storage.host.isEmpty()) {
            return listLocalShizuku(path)
        }
        val res = AdbManager.runShellCommand(context, storage.host, storage.port, "ls -alF \"$path\"")
        if (res.startsWith("Error")) {
            return listOf(RemoteFile(res, "", false, 0, 0))
        }
        return parseAdbLs(res, path)
    }

    private fun listLocalShizuku(path: String): List<RemoteFile> {
        if (!ShizukuManager.isShizukuAvailable()) return emptyList()
        val res = ShizukuManager.runCommand("ls -alF \"$path\"")
        if (res.startsWith("Error") || res.startsWith("Exception")) return emptyList()
        return parseAdbLs(res, path)
    }

    private fun parseAdbLs(res: String, path: String): List<RemoteFile> {
        val lines = res.split("\n")
        return lines.filter { it.isNotBlank() && !it.startsWith("total") && !it.contains("Permission denied") }.mapNotNull { line ->
            try {
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 8) return@mapNotNull null
                val name = parts.drop(7).joinToString(" ")
                val isDir = line.startsWith("d") || name.endsWith("/")
                val cleanName = name.removeSuffix("*").removeSuffix("/")
                val size = parts[4].toLongOrNull() ?: 0L
                RemoteFile(cleanName, if (path.endsWith("/")) "$path$cleanName" else "$path/$cleanName", isDir, size, 0)
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun downloadRemoteADB(context: Context, storage: NetworkStorage, remoteFile: RemoteFile, localDest: File, onProgress: (Float) -> Unit): Boolean {
        localDest.parentFile?.mkdirs()
        val content = AdbManager.pullFileContent(context, storage.host, storage.port, remoteFile.path)
        return if (!content.startsWith("Error")) {
            localDest.writeText(content)
            onProgress(1.0f)
            true
        } else false
    }

    private suspend fun uploadRemoteADB(context: Context, storage: NetworkStorage, localFile: File, remotePath: String, onProgress: (Float) -> Unit): Boolean {
        val destPath = if (remotePath.endsWith("/")) "$remotePath${localFile.name}" else "$remotePath/${localFile.name}"
        // 大文件保护：超过 2MB 拒绝通过 ADB shell 上传
        if (localFile.length() > 2 * 1024 * 1024) {
            Log.w(TAG, "File too large for ADB upload: ${localFile.length()} bytes")
            return false
        }
        val content = localFile.readText(Charsets.UTF_8)
        val success = AdbManager.pushFileContent(context, storage.host, storage.port, content, destPath)
        if (success) onProgress(1.0f)
        return success
    }

    private fun listWebDAV(storage: NetworkStorage, path: String): List<RemoteFile> {
        val sardine = if (storage.username.isEmpty()) SardineFactory.begin() else SardineFactory.begin(storage.username, storage.password)
        val base = if (storage.host.startsWith("http")) storage.host.trimEnd('/') else "http://${storage.host}"
        val portSuffix = if (storage.port != 80 && storage.port != 443) ":${storage.port}" else ""
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val url = "$base$portSuffix$normalizedPath"
        return try {
            sardine.list(url).filter { it.name != null && it.name != "" }.map {
                RemoteFile(it.name ?: "", it.path ?: "", it.isDirectory, it.contentLength ?: 0, it.modified?.time ?: 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV list failed for $url", e)
            emptyList()
        }
    }
}
