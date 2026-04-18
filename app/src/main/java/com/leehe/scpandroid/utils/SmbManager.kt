package com.leehe.scpandroid.utils

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.leehe.scpandroid.models.NetworkStorage
import com.leehe.scpandroid.models.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

object SmbManager {
    private const val TAG = "SmbManager"
    
    private val client: SMBClient by lazy {
        val config = SmbConfig.builder()
            .withTimeout(15000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .withSoTimeout(60000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        SMBClient(config)
    }

    private fun getAuthContext(storage: NetworkStorage): AuthenticationContext {
        return if (storage.username.isBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(storage.username, storage.password.toCharArray(), null)
        }
    }

    private fun parsePath(path: String): Pair<String, String> {
        val cleanPath = path.trim().removePrefix("/").removeSuffix("/")
        if (cleanPath.isEmpty()) return "" to ""
        
        val parts = cleanPath.split("/", limit = 2)
        val shareName = parts[0]
        val subPath = if (parts.size > 1) parts[1] else ""
        return shareName to subPath
    }

    /**
     * 由于 SMBJ 无法像 jcifs 一样直接枚举共享名，
     * 我们参考 SambaLite 的实现，尝试连接一些常见的共享名。
     */
    private fun tryListShares(session: Session): List<String> {
        val commonShares = listOf(
            "Shared", "Public", "Downloads", "Movies", "Music", "Pictures", "Data", "Home", "Users",
            "share", "public", "downloads", "data", "external", "sda1", "sdb1", "storage", "nas"
        )
        val foundShares = mutableListOf<String>()
        for (name in commonShares) {
            try {
                session.connectShare(name).use { share ->
                    if (share.isConnected) foundShares.add(name)
                }
            } catch (e: Exception) {
                // 忽略连接失败的尝试
            }
        }
        return foundShares
    }

    suspend fun listFiles(storage: NetworkStorage, path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val (shareName, subPath) = parsePath(path)
        
        try {
            client.connect(storage.host, storage.port).use { connection ->
                val session = connection.authenticate(getAuthContext(storage))
                session.use { s ->
                    // 如果路径为空，尝试展示“常用”共享列表
                    if (shareName.isEmpty()) {
                        Log.d(TAG, "No share name, attempting to find common shares...")
                        val shares = tryListShares(s)
                        if (shares.isEmpty()) {
                            return@withContext listOf(RemoteFile("请手动在Path中填写共享文件夹名", "", true, 0, 0))
                        }
                        return@withContext shares.map { name ->
                            RemoteFile(name, "/$name", true, 0, 0)
                        }
                    }

                    // 如果有共享名，则正常列出内容
                    val share = s.connectShare(shareName) as DiskShare
                    share.use { ds ->
                        ds.list(subPath).filter { 
                            it.fileName != "." && it.fileName != ".." 
                        }.map { info ->
                            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0L
                            val currentPath = if (path.endsWith("/")) path else "$path/"
                            RemoteFile(
                                name = info.fileName,
                                path = "$currentPath${info.fileName}",
                                isDirectory = isDir,
                                size = info.endOfFile,
                                lastModified = info.lastWriteTime.toEpochMillis()
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMBJ operation failed: ${e.message}")
            throw e
        }
    }

    suspend fun downloadFile(
        storage: NetworkStorage,
        remoteFile: RemoteFile,
        localDest: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val (shareName, subPath) = parsePath(remoteFile.path)
        try {
            client.connect(storage.host, storage.port).use { connection ->
                val session = connection.authenticate(getAuthContext(storage))
                session.use { s ->
                    val share = s.connectShare(shareName) as DiskShare
                    share.use { ds ->
                        val smbFile = ds.openFile(
                            subPath,
                            EnumSet.of(AccessMask.GENERIC_READ),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            null
                        )
                        smbFile.use { sf ->
                            sf.inputStream.use { input ->
                                FileOutputStream(localDest).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var transferred = 0L
                                    val total = remoteFile.size
                                    var bytes = input.read(buffer)
                                    while (bytes >= 0) {
                                        output.write(buffer, 0, bytes)
                                        transferred += bytes
                                        if (total > 0) onProgress(transferred.toFloat() / total)
                                        bytes = input.read(buffer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMBJ download failed", e)
            false
        }
    }

    suspend fun uploadFile(
        storage: NetworkStorage,
        localFile: File,
        remotePath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val destRemotePath = if (remotePath.endsWith("/")) "$remotePath${localFile.name}" else "$remotePath/${localFile.name}"
        val (shareName, subPath) = parsePath(destRemotePath)
        
        try {
            client.connect(storage.host, storage.port).use { connection ->
                val session = connection.authenticate(getAuthContext(storage))
                session.use { s ->
                    val share = s.connectShare(shareName) as DiskShare
                    share.use { ds ->
                        val smbFile = ds.openFile(
                            subPath,
                            EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.FILE_WRITE_DATA),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OVERWRITE_IF,
                            null
                        )
                        smbFile.use { sf ->
                            localFile.inputStream().use { input ->
                                sf.outputStream.use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    val total = localFile.length()
                                    var transferred = 0L
                                    var bytes = input.read(buffer)
                                    while (bytes >= 0) {
                                        output.write(buffer, 0, bytes)
                                        transferred += bytes
                                        if (total > 0) onProgress(transferred.toFloat() / total)
                                        bytes = input.read(buffer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMBJ upload failed", e)
            false
        }
    }
}
