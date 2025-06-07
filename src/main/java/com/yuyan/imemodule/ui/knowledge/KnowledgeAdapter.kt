package com.yuyan.imemodule.ui.knowledge

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yuyan.imemodule.databinding.ItemKnowledgeBinding

class KnowledgeAdapter(
    private val onDeleteClick: (String) -> Unit,
    private val onRefreshStatus: (String) -> Unit
) : RecyclerView.Adapter<KnowledgeAdapter.ViewHolder>() {

    private val items = mutableListOf<KnowledgeItem>()

    fun updateItems(newItems: List<KnowledgeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateItemStatus(fileId: String, status: String) {
        val index = items.indexOfFirst { it.fileId == fileId }
        if (index != -1) {
            items[index] = items[index].copy(status = status)
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKnowledgeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemKnowledgeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: KnowledgeItem) {
            binding.tvFileName.text = item.fileName
            binding.tvStatus.text = when(item.status) {
                "PARSING" -> "解析中"
                "PARSE_SUCCESS" -> "解析完成"
                else -> item.status
            }
            binding.tvDescription.text = item.description
            
            binding.btnDelete.setOnClickListener {
                onDeleteClick(item.fileId)
            }

            // 当状态为解析中时，定期刷新状态
            if (item.status == "PARSING") {
                onRefreshStatus(item.fileId)
            }
        }
    }
}

data class KnowledgeItem(
    val fileId: String,
    val fileName: String,
    val status: String,
    val description: String
) 