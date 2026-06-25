package com.leehe.scpandroid.utils

import android.util.Base64
import com.cgutman.adblib.AdbBase64

class AdbBase64Impl : AdbBase64 {
    override fun encodeToString(data: ByteArray?): String {
        if (data == null) return ""
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }
}
