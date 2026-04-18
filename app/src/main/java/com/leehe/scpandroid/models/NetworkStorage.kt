package com.leehe.scpandroid.models

enum class StorageType {
    SFTP, SMB, FTP, SCP, WEBDAV, ADB, LOCAL_SAF
}

data class NetworkStorage(
    val name: String,
    val type: StorageType,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val rootPath: String = "/"
)
