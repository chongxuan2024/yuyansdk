package com.yuyan.imemodule.data.model

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yuyan.imemodule.databinding.ItemKnowledgeBaseBinding

class KnowledgeAdapter(
    private val onDeleteClick: (String) -> Unit,
    private val onRefreshStatus: (String) -> Unit
) : RecyclerView.Adapter<KnowledgeAdapter.ViewHolder>() {
    private var items = mutableListOf<KnowledgeItem>()

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
        return ViewHolder(
            ItemKnowledgeBaseBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemKnowledgeBaseBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: KnowledgeItem) {
            binding.tvName.text = item.fileName
            // TODO: 实现其他UI绑定
        }
    }
}

data class KnowledgeItem(
    val fileId: String,
    val fileName: String,
    val status: String,
    val description: String
) 