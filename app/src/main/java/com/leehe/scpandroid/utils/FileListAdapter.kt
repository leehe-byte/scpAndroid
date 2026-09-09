package com.leehe.scpandroid.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.RecyclerView
import com.leehe.scpandroid.R
import com.leehe.scpandroid.models.FileItem
import com.leehe.scpandroid.models.RemoteFile
import java.io.File

class FileListAdapter : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

    var items: List<Any> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    var onFileClick: ((Any) -> Unit)? = null
    var onFileLongClick: ((Any) -> Unit)? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.item_icon)
        val name: TextView = view.findViewById(R.id.item_name)
        val size: TextView = view.findViewById(R.id.item_size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.file_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.setOnClickListener { onFileClick?.invoke(item) }
        holder.itemView.setOnLongClickListener { onFileLongClick?.invoke(item); true }

        when (item) {
            is FileItem -> {
                holder.name.text = item.name
                holder.size.text = if (!item.isDirectory && item.name != "..") item.getFormattedSize() else ""
                bindIcon(holder, item.name, item.isDirectory, item.file)
            }
            is RemoteFile -> {
                holder.name.text = item.name
                holder.size.text = if (!item.isDirectory && item.name != "..") FileUtils.formatFileSize(item.size) else ""
                setFileIcon(holder, item.name, item.isDirectory)
            }
        }
    }

    override fun getItemCount() = items.size

    private fun bindIcon(holder: ViewHolder, name: String, isDir: Boolean, file: File) {
        val ext = name.substringAfterLast(".", "").lowercase()
        // 先设置占位图标
        setFileIcon(holder, name, isDir)
        // 目录或不可缩略的类型直接返回
        if (isDir || ext !in thumbnailExts) return

        val targetPath = file.absolutePath
        val targetPos = holder.bindingAdapterPosition
        ThumbnailProvider.loadAsync(holder.itemView.context, file) { bmp ->
            if (bmp == null) return@loadAsync
            // 校验 view 未被回收或复用
            if (holder.bindingAdapterPosition != targetPos) return@loadAsync
            val item = items.getOrNull(targetPos)
            val currentPath = when (item) {
                is FileItem -> item.file.absolutePath
                else -> null
            }
            if (currentPath != targetPath) return@loadAsync

            val round = RoundedBitmapDrawableFactory.create(holder.itemView.context.resources, bmp)
            round.cornerRadius = bmp.width * 0.12f
            holder.icon.setImageDrawable(round)
        }
    }

    private fun setFileIcon(holder: ViewHolder, name: String, isDir: Boolean) {
        val resId = when {
            isDir -> R.drawable.ic_folder
            name.endsWith(".apk") -> R.drawable.ic_apk
            Companion.RE_ARCHIVE.matches(name) -> R.drawable.ic_archive
            Companion.RE_IMAGE.matches(name) -> R.drawable.ic_image
            Companion.RE_VIDEO.matches(name) -> R.drawable.ic_video
            Companion.RE_AUDIO.matches(name) -> R.drawable.ic_audio
            Companion.RE_CODE.matches(name) -> R.drawable.ic_code
            Companion.RE_PDF.matches(name) -> R.drawable.ic_pdf
            Companion.RE_TEXT.matches(name) -> R.drawable.ic_text
            else -> R.drawable.ic_file
        }
        holder.icon.setImageResource(resId)
    }

    companion object {
        val thumbnailExts = setOf("apk","jpg","jpeg","png","gif","webp","bmp","heic","mp4","mkv","avi","mov","webm","flv","3gp")

        private val RE_ARCHIVE = Regex("(?i).*\\.(zip|rar|7z|tar|gz)")
        private val RE_IMAGE = Regex("(?i).*\\.(jpg|jpeg|png|gif|webp|bmp)")
        private val RE_VIDEO = Regex("(?i).*\\.(mp4|mkv|avi|mov|webm|flv)")
        private val RE_AUDIO = Regex("(?i).*\\.(mp3|wav|flac|ogg)")
        private val RE_CODE = Regex("(?i).*\\.(py|java|kt|cpp|c|h|js|ts|html|css|json|xml|smali|sh|sql|rs|go|php|rb|swift|dart)")
        private val RE_PDF = Regex("(?i).*\\.(pdf)")
        private val RE_TEXT = Regex("(?i).*\\.(txt|md|log)")
    }
}
