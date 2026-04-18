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
import java.io.InputStream
import java.io.OutputStream

object NetworkManager {
    private const val TAG = "NetworkManager"

    suspend fun listFiles(storage: NetworkStorage, path: String, context: Context? = null): List<RemoteFile> = withContext(Dispatchers.IO) {
        try {
            when (storage.type) {
                StorageType.SFTP -> listSFTP(storage, path)
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
            when (storage.type) {
                StorageType.SFTP -> downloadSFTP(storage, remoteFile, localDest, onProgress)
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
                StorageType.SFTP -> uploadSFTP(storage, localFile, remotePath, onProgress)
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

    // --- SFTP, FTP, WebDAV implementations (Remained unchanged but optimized) ---

    private fun listSFTP(storage: NetworkStorage, path: String): List<RemoteFile> {
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
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
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
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
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
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
            sftp.put(localFile.absolutePath, remotePath)
            true
        } finally {
            ssh.disconnect()
        }
    }

    private fun listFTP(storage: NetworkStorage, path: String): List<RemoteFile> {
        val ftp = FTPClient()
        return try {
            ftp.connect(storage.host, storage.port)
            if (storage.username.isEmpty()) ftp.login("anonymous", "") else ftp.login(storage.username, storage.password)
            ftp.enterLocalPassiveMode()
            ftp.listFiles(path).map {
                RemoteFile(it.name, if (path.endsWith("/")) "$path${it.name}" else "$path/${it.name}", it.isDirectory, it.size, it.timestamp.timeInMillis)
            }
        } finally {
            ftp.disconnect()
        }
    }

    private fun downloadFTP(storage: NetworkStorage, remoteFile: RemoteFile, localDest: File, onProgress: (Float) -> Unit): Boolean {
        val ftp = FTPClient()
        return try {
            ftp.connect(storage.host, storage.port)
            ftp.login(storage.username, storage.password)
            ftp.enterLocalPassiveMode()
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            val out = object : FileOutputStream(localDest) {
                var transferred = 0L
                override fun write(b: Int) { super.write(b); transferred++; onProgress(transferred.toFloat() / remoteFile.size.coerceAtLeast(1)) }
                override fun write(b: ByteArray, off: Int, len: Int) { super.write(b, off, len); transferred += len; onProgress(transferred.toFloat() / remoteFile.size.coerceAtLeast(1)) }
            }
            out.use { ftp.retrieveFile(remoteFile.path, it) }
        } finally {
            ftp.disconnect()
        }
    }

    private fun uploadFTP(storage: NetworkStorage, localFile: File, remotePath: String, onProgress: (Float) -> Unit): Boolean {
        val ftp = FTPClient()
        return try {
            ftp.connect(storage.host, storage.port)
            ftp.login(storage.username, storage.password)
            ftp.enterLocalPassiveMode()
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            val fileSize = localFile.length()
            val inp = object : FileInputStream(localFile) {
                var transferred = 0L
                override fun read(): Int { val r = super.read(); if (r != -1) { transferred++; onProgress(transferred.toFloat() / fileSize.coerceAtLeast(1)) }; return r }
                override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r != -1) { transferred += r; onProgress(transferred.toFloat() / fileSize.coerceAtLeast(1)) }; return r }
            }
            val remoteFilePath = if (remotePath.endsWith("/")) "$remotePath${localFile.name}" else "$remotePath/${localFile.name}"
            inp.use { ftp.storeFile(remoteFilePath, it) }
        } finally {
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
        return parseAdbLs(res, path)
    }

    private fun parseAdbLs(res: String, path: String): List<RemoteFile> {
        val lines = res.split("\n")
        return lines.filter { it.isNotBlank() && !it.startsWith("total") && !it.contains("Permission denied") }.mapNotNull { line ->
            try {
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 8) return@mapNotNull null
                var name = parts.last()
                val isDir = line.startsWith("d") || name.endsWith("/")
                name = name.removeSuffix("*").removeSuffix("/")
                val size = parts[4].toLongOrNull() ?: 0L
                RemoteFile(name, if (path.endsWith("/")) "$path$name" else "$path/$name", isDir, size, 0)
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun downloadRemoteADB(context: Context, storage: NetworkStorage, remoteFile: RemoteFile, localDest: File, onProgress: (Float) -> Unit): Boolean {
        val content = AdbManager.pullFileContent(context, storage.host, storage.port, remoteFile.path)
        return if (!content.startsWith("Error")) {
            localDest.writeText(content)
            onProgress(1.0f)
            true
        } else false
    }

    private suspend fun uploadRemoteADB(context: Context, storage: NetworkStorage, localFile: File, remotePath: String, onProgress: (Float) -> Unit): Boolean {
        val destPath = if (remotePath.endsWith("/")) "$remotePath${localFile.name}" else "$remotePath/${localFile.name}"
        val success = AdbManager.pushFileContent(context, storage.host, storage.port, localFile.readText(), destPath)
        if (success) onProgress(1.0f)
        return success
    }

    private fun listWebDAV(storage: NetworkStorage, path: String): List<RemoteFile> {
        val sardine = if (storage.username.isEmpty()) SardineFactory.begin() else SardineFactory.begin(storage.username, storage.password)
        val url = if (storage.host.startsWith("http")) "${storage.host}:${storage.port}$path" else "http://${storage.host}:${storage.port}$path"
        return try {
            sardine.list(url).filter { it.name != null }.map {
                RemoteFile(it.name ?: "", it.path, it.isDirectory, it.contentLength ?: 0, it.modified?.time ?: 0)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
