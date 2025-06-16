package com.yuyan.imemodule.ui.knowledge

import android.view.LayoutInflater
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
    val description: String
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

class DocumentAdapter : ListAdapter<Document, DocumentAdapter.ViewHolder>(DocumentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun bind(document: Document) {
            binding.apply {
                tvFileId.text = document.fileId
                tvDescription.text = document.description
                
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

                // 设置任务状态
                tvJobStatus.apply {
                    text = "任务状态：${
                        when (document.jobStatus) {
                            Document.JOB_STATUS_COMPLETED -> "已完成"
                            Document.JOB_STATUS_FAILED -> "失败"
                            Document.JOB_STATUS_RUNNING -> "运行中"
                            Document.JOB_STATUS_PENDING -> "等待中"
                            else -> document.jobStatus
                        }
                    }"
                    setTextColor(
                        context.getColor(
                            when (document.jobStatus) {
                                Document.JOB_STATUS_COMPLETED -> R.color.status_ok
                                Document.JOB_STATUS_FAILED -> R.color.status_error
                                Document.JOB_STATUS_RUNNING -> R.color.status_running
                                Document.JOB_STATUS_PENDING -> R.color.status_pending
                                else -> R.color.status_error
                            }
                        )
                    )
                }

                // 设置创建时间
                tvCreateTime.text = "创建时间：${dateFormat.format(document.createTime)}"
            }
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