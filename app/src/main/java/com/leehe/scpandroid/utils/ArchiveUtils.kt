package com.leehe.scpandroid.utils

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File

object ArchiveUtils {
    /**
     * 获取压缩包内的文件列表
     */
    fun getArchiveEntries(archiveFile: File): List<ArchiveEntry> {
        return try {
            val zipFile = ZipFile(archiveFile)
            zipFile.fileHeaders.map { header ->
                ArchiveEntry(
                    name = header.fileName,
                    isDirectory = header.isDirectory,
                    uncompressedSize = header.uncompressedSize,
                    lastModified = header.lastModifiedTime
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 解压压缩包到指定目录
     */
    fun extractAll(archiveFile: File, destDir: File, password: String? = null): Boolean {
        return try {
            val zipFile = ZipFile(archiveFile)
            if (zipFile.isEncrypted && password != null) {
                zipFile.setPassword(password.toCharArray())
            }
            zipFile.extractAll(destDir.absolutePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 压缩文件或目录
     */
    fun compress(sourceFiles: List<File>, destZipFile: File, password: String? = null): Boolean {
        return try {
            val zipFile = ZipFile(destZipFile)
            if (password != null) {
                // 这里可以配置加密参数，暂简化实现
                zipFile.setPassword(password.toCharArray())
            }
            sourceFiles.forEach { file ->
                if (file.isDirectory) {
                    zipFile.addFolder(file)
                } else {
                    zipFile.addFile(file)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

data class ArchiveEntry(
    val name: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val lastModified: Long
)
