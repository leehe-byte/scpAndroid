package com.leehe.scpandroid.utils

object HexUtils {
    /**
     * 将字节数组转为格式化的十六进制字符串
     */
    fun bytesToHex(bytes: ByteArray, bytesPerRow: Int = 16): String {
        val sb = StringBuilder()
        for (i in bytes.indices) {
            if (i % bytesPerRow == 0 && i != 0) sb.append("\n")
            sb.append("%02X ".format(bytes[i]))
        }
        return sb.toString()
    }

    /**
     * 带地址和 ASCII 预览的十六进制视图
     */
    fun formatHexView(bytes: ByteArray): String {
        val sb = StringBuilder()
        val bytesPerRow = 16
        
        for (i in bytes.indices step bytesPerRow) {
            // Offset 地址
            sb.append("%08X: ".format(i))
            
            // 十六进制部分
            for (j in 0 until bytesPerRow) {
                if (i + j < bytes.size) {
                    sb.append("%02X ".format(bytes[i + j]))
                } else {
                    sb.append("   ")
                }
            }
            
            sb.append(" ")
            
            // ASCII 部分
            for (j in 0 until bytesPerRow) {
                if (i + j < bytes.size) {
                    val c = bytes[i + j].toInt().toChar()
                    if (c in ' '..'~') {
                        sb.append(c)
                    } else {
                        sb.append(".")
                    }
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
