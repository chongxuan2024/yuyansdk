package com.yuyan.imemodule.ui.knowledge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.yuyan.imemodule.data.model.KnowledgeBase
import com.yuyan.imemodule.data.model.PaymentType
import com.yuyan.imemodule.data.model.Role
import com.yuyan.imemodule.data.model.TemplateType
import com.yuyan.imemodule.databinding.ItemKnowledgeBaseBinding
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class KnowledgeBaseAdapter(
    private val isAdmin: Boolean,
    private val onItemClick: (KnowledgeBase) -> Unit,
    private val onMemberClick: (KnowledgeBase) -> Unit,
    private val onDeleteClick: (KnowledgeBase) -> Unit,
    private val onRenameClick: (KnowledgeBase) -> Unit
) : ListAdapter<KnowledgeBase, KnowledgeBaseAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKnowledgeBaseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemKnowledgeBaseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                onItemClick(getItem(adapterPosition))
            }
        }

        fun bind(item: KnowledgeBase) {
            binding.tvName.text = item.name
            
            binding.chipPaymentType.text = when (item.paymentType) {
                PaymentType.USER_PAID -> "用户付费"
                PaymentType.ENTERPRISE_PAID -> "企业付费"
            }

            binding.tvTemplate.text = when (item.templateType) {
                TemplateType.PERSONAL_CHAT -> "个人聊天"
                TemplateType.PRODUCT_SUPPORT -> "产品答疑"
                TemplateType.CUSTOMER_SERVICE -> "售后服务"
                TemplateType.CUSTOM -> "自定义"
            }
            val creator = item.creatorUser
            if (creator != "null") {
                binding.tvCreator.text = "创建者：$creator"
            }

            binding.tvCreateTime.text = "创建时间：${formatDate(item.createdAt)}"

            binding.chipGroupMembers.removeAllViews()
            if (isAdmin) {
                // 管理员可以看到所有成员
                item.members.forEach { member ->
                    Chip(binding.root.context).apply {
                        text = "${member.username}(${
                            when (member.role) {
                                Role.ADMIN -> "管理员"
                                Role.USER -> "普通用户"
                            }
                        })"
                        isClickable = true
                        setOnClickListener { onMemberClick(item) }
                        binding.chipGroupMembers.addView(this)
                    }
                }
                binding.btnDelete.visibility = View.VISIBLE
                binding.btnRename.visibility = View.VISIBLE
                binding.chipPaymentType.visibility = View.GONE;
                binding.btnDelete.setOnClickListener { onDeleteClick(item) }
                binding.btnRename.setOnClickListener { onRenameClick(item) }
            } else {
                // 普通用户只能看到自己的角色
                val currentUser = UserManager.getCurrentUser()
                item.members.find { it.username == currentUser?.username }?.let { member ->
                    Chip(binding.root.context).apply {
                        text = when (member.role) {
                            Role.ADMIN -> "管理员"
                            Role.USER -> "普通用户"
                        }
                        isClickable = false
                        binding.chipGroupMembers.addView(this)
                    }
                }
                binding.btnDelete.visibility = View.GONE
                binding.btnRename.visibility = View.GONE
                binding.chipPaymentType.visibility = View.VISIBLE   ;
            }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<KnowledgeBase>() {
            override fun areItemsTheSame(oldItem: KnowledgeBase, newItem: KnowledgeBase): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: KnowledgeBase, newItem: KnowledgeBase): Boolean {
                return oldItem == newItem
            }
        }
    }
}