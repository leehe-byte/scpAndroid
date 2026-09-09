package com.leehe.scpandroid.utils

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface

/** 原生 Spannable 语法高亮，零 Compose 开销，直接用于 EditText */
object SpanHighlighter {
    private val kwColor = 0xFFCF8E6D.toInt()
    private val strColor = 0xFF6A8759.toInt()
    private val cmtColor = 0xFF808080.toInt()
    private val numColor = 0xFF6897BB.toInt()
    private val funcColor = 0xFFFFC66D.toInt()
    private val typeColor = 0xFF287BDE.toInt()

    fun highlight(sp: Spannable, ext: String) {
        val text = sp.toString()
        val kw = keywords(ext)
        val tp = types(ext)
        val comment = commentPrefix(ext)

        // 清除旧 spans
        for (s in sp.getSpans(0, sp.length, Any::class.java)) sp.removeSpan(s)

        var i = 0
        while (i < sp.length) {
            // 行注释
            if (comment != null && i + comment.length <= sp.length && sp.substring(i, i + comment.length) == comment) {
                sp.setSpan(ForegroundColorSpan(cmtColor), i, sp.length, 0)
                break // 行注释到行尾
            }
            // 字符串
            if (i < sp.length && (sp[i] == '"' || sp[i] == '\'')) {
                val q = sp[i]; var e = i + 1
                while (e < sp.length && sp[e] != q) { if (sp[e] == '\\') e++; e++ }
                if (e < sp.length) e++
                sp.setSpan(ForegroundColorSpan(strColor), i, e, 0); i = e; continue
            }
            // 数字
            if (i < sp.length && sp[i].isDigit()) {
                val s = i
                while (i < sp.length && (sp[i].isDigit() || sp[i] == '.' || sp[i] == 'x' || sp[i] in 'a'..'f' || sp[i] in 'A'..'F')) i++
                sp.setSpan(ForegroundColorSpan(numColor), s, i, 0); continue
            }
            // 标识符
            if (i < sp.length && (sp[i].isLetter() || sp[i] == '_')) {
                val s = i
                while (i < sp.length && (sp[i].isLetterOrDigit() || sp[i] == '_')) i++
                val w = sp.substring(s, i)
                when {
                    w in kw -> sp.setSpan(StyleSpan(Typeface.BOLD), s, i, 0)
                    w in tp -> sp.setSpan(ForegroundColorSpan(typeColor), s, i, 0)
                }
                // 函数调用检测
                val next = if (i < sp.length) sp[i] else ' '
                if (next == '(') sp.setSpan(ForegroundColorSpan(funcColor), s, i, 0)
                continue
            }
            i++
        }
    }

    private fun commentPrefix(ext: String): String? = when (ext) {
        "py", "rb", "sh", "pl", "r" -> "#"
        "java", "kt", "cpp", "c", "cs", "js", "ts", "go", "rs", "swift", "dart" -> "//"
        "lua" -> "--"
        "sql" -> "--"
        else -> null
    }

    private fun keywords(ext: String): Set<String> = when (ext) {
        "py" -> setOf("def","class","if","else","elif","for","while","return","import","from","as","try","except","with","pass","in","is","not","and","or","break","continue","yield","raise","finally","lambda","True","False","None","async","await")
        "java" -> setOf("package","import","class","interface","extends","implements","if","else","for","while","return","try","catch","finally","throw","throws","public","private","protected","static","final","abstract","void","new","this","super","null","true","false","switch","case","default","break","continue","do","enum")
        "kt" -> setOf("package","import","class","interface","object","fun","val","var","if","else","for","while","return","try","catch","finally","throw","public","private","protected","internal","override","open","abstract","data","sealed","enum","companion","suspend","inline","when","is","as","in","this","super","null","true","false","do","break","continue","by")
        "cpp","c" -> setOf("if","else","for","while","return","class","struct","void","include","define","int","char","long","float","double","bool","static","const","using","namespace","template","virtual","public","private","protected","new","delete","this","nullptr","true","false","switch","case","break","continue","do","enum","typedef","sizeof","auto")
        "js","ts" -> setOf("function","class","const","let","var","if","else","for","while","return","export","import","from","async","await","try","catch","throw","new","this","super","null","undefined","true","false","switch","case","break","continue","do")
        "go" -> setOf("func","if","else","for","return","import","package","type","struct","interface","map","chan","var","const","go","defer","select","case","default","break","continue","range","switch","true","false","nil")
        "rs" -> setOf("fn","let","mut","if","else","for","while","return","impl","struct","enum","trait","pub","use","mod","as","in","where","match","self","super","true","false","unsafe","ref","move")
        else -> emptySet()
    }

    private fun types(ext: String): Set<String> = when (ext) {
        "java" -> setOf("String","Integer","int","Boolean","boolean","Long","long","Float","float","Double","double","char","void","Object","List","Map","Set")
        "kt" -> setOf("String","Int","Boolean","Long","Float","Double","Char","Unit","Nothing","Any","List","Map","Set","Array")
        "cpp","c" -> setOf("int","char","long","float","double","void","bool","unsigned","signed","size_t")
        "ts" -> setOf("string","number","boolean","void","any","never")
        else -> emptySet()
    }
}
