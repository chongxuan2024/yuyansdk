package com.yuyan.imemodule.ui.knowledge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yuyan.imemodule.R
import com.yuyan.imemodule.databinding.ItemDocumentBinding
import java.text.SimpleDateFormat
import java.util.*

data class Document(
    val id: Long,
    val uid: Long,
    val createTime: Date,
    val fileId: String,
    val fileParseStatus: String,
    val jobId: String,
    val jobStatus: String,
    val status: String,
    val description: String,
    var isDownloaded: Boolean = false,
    var isDownloading: Boolean = false,
    var downloadProgress: Int = 0
) {
    companion object {
        const val FILE_PARSE_STATUS_PARSING = "PARSING"
        const val FILE_PARSE_STATUS_SUCCESS = "PARSE_SUCCESS"
        
        const val JOB_STATUS_COMPLETED = "COMPLETED"
        const val JOB_STATUS_FAILED = "FAILED"
        const val JOB_STATUS_RUNNING = "RUNNING"
        const val JOB_STATUS_PENDING = "PENDING"
        
        const val STATUS_OK = "OK"
        const val STATUS_DISABLE = "DISABLE"
    }
}

class DocumentAdapter(private val isAdmin: Boolean = false) : ListAdapter<Document, DocumentAdapter.ViewHolder>(DocumentDiffCallback()) {

    private var onDeleteClickListener: ((Document) -> Unit)? = null
    private var onDownloadClickListener: ((Document, ViewHolder) -> Unit)? = null

    fun setOnDeleteClickListener(listener: (Document) -> Unit) {
        onDeleteClickListener = listener
    }
    
    fun setOnDownloadClickListener(listener: (Document, ViewHolder) -> Unit) {
        onDownloadClickListener = listener
    }
    
    fun updateDownloadProgress(fileId: String, progress: Int) {
        val position = currentList.indexOfFirst { it.fileId == fileId }
        if (position != -1) {
            val document = getItem(position)
            document.downloadProgress = progress
            document.isDownloading = progress < 100
            document.isDownloaded = progress == 100
            notifyItemChanged(position)
        }
    }
    
    fun setDownloaded(fileId: String, isDownloaded: Boolean) {
        val position = currentList.indexOfFirst { it.fileId == fileId }
        if (position != -1) {
            val document = getItem(position)
            document.isDownloaded = isDownloaded
            document.isDownloading = false
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onDeleteClickListener, onDownloadClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), isAdmin)
    }

    class ViewHolder(
        private val binding: ItemDocumentBinding,
        private val onDeleteClickListener: ((Document) -> Unit)?,
        private val onDownloadClickListener: ((Document, ViewHolder) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun bind(document: Document, isAdmin: Boolean) {
            binding.apply {
                tvDescription.text = document.description
                
                // 设置删除按钮的可见性和点击事件
                btnDelete.visibility = if (isAdmin) View.VISIBLE else View.GONE
                btnDelete.setOnClickListener {
                    onDeleteClickListener?.invoke(document)
                }
                
                // 设置下载按钮的点击事件
                btnDownload.setOnClickListener {
                    onDownloadClickListener?.invoke(document, this@ViewHolder)
                }
                
                // 更新下载状态UI
                updateDownloadState(document)
                
                // 设置文档状态
                chipStatus.apply {
                    text = document.status
                    setChipBackgroundColorResource(
                        when (document.status) {
                            Document.STATUS_OK -> R.color.status_ok
                            else -> R.color.status_disable
                        }
                    )
                }

                // 设置解析状态
                tvParseStatus.apply {
                    text = "解析状态：${
                        when (document.fileParseStatus) {
                            Document.FILE_PARSE_STATUS_PARSING -> "解析中"
                            Document.FILE_PARSE_STATUS_SUCCESS -> "解析完成"
                            else -> document.fileParseStatus
                        }
                    }"
                    setTextColor(
                        context.getColor(
                            when (document.fileParseStatus) {
                                Document.FILE_PARSE_STATUS_SUCCESS -> R.color.status_ok
                                Document.FILE_PARSE_STATUS_PARSING -> R.color.status_pending
                                else -> R.color.status_error
                            }
                        )
                    )
                }

                // 设置创建时间
                tvCreateTime.text = "创建时间：${dateFormat.format(document.createTime)}"
            }
        }
        
        fun updateDownloadState(document: Document) {
            binding.apply {
                if (document.isDownloading) {
                    progressDownload.visibility = View.VISIBLE
                    progressDownload.progress = document.downloadProgress
                    btnDownload.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    progressDownload.visibility = if (document.isDownloaded) View.GONE else View.GONE
                    btnDownload.setImageResource(
                        if (document.isDownloaded) android.R.drawable.ic_menu_view 
                        else android.R.drawable.ic_menu_save
                    )
                }
            }
        }
        
        fun updateProgress(progress: Int) {
            binding.progressDownload.progress = progress
        }
        
        fun showProgress() {
            binding.progressDownload.visibility = View.VISIBLE
        }
        
        fun hideProgress() {
            binding.progressDownload.visibility = View.GONE
        }
    }
}

class DocumentDiffCallback : DiffUtil.ItemCallback<Document>() {
    override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem == newItem
    }
} 