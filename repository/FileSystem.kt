package com.leehe.scpandroid.repository

import com.leehe.scpandroid.model.FileItem
import java.io.InputStream
import java.io.OutputStream

interface FileSystem {
    suspend fun listFiles(path: String): List<FileItem>
    suspend fun createDirectory(path: String, name: String): Boolean
    suspend fun createFile(path: String, name: String): Boolean
    suspend fun delete(path: String): Boolean
    suspend fun rename(oldPath: String, newPath: String): Boolean
    suspend fun getInputStream(path: String): InputStream
    suspend fun getOutputStream(path: String): OutputStream
    suspend fun exists(path: String): Boolean
}
