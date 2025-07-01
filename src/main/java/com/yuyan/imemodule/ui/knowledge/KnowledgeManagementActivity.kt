package com.yuyan.imemodule.ui.knowledge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.model.*
import com.yuyan.imemodule.databinding.ActivityKnowledgeManagementBinding
import com.yuyan.imemodule.databinding.DialogAddKnowledgeBaseBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class KnowledgeManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKnowledgeManagementBinding
    private lateinit var adapter: KnowledgeAdapter
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val PICK_FILE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKnowledgeManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupViewPager()
        setupFab()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "知识库管理"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        adapter = KnowledgeAdapter(
            onDeleteClick = { fileId -> deleteKnowledge(fileId) },
            onRefreshStatus = { fileId -> checkFileStatus(fileId) }
        )
    }

    private fun setupViewPager() {
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2

            override fun createFragment(position: Int) = when (position) {
                0 -> KnowledgeListFragment.newInstance(isAdmin = true)
                else -> KnowledgeListFragment.newInstance(isAdmin = false)
            }
        }
        
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "我的知识库"
                else -> "已授权知识库"
            }
        }.attach()

        loadKnowledgeList()
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddKnowledgeBaseDialog()
        }
    }

    private fun showAddKnowledgeBaseDialog() {
        val dialogBinding = DialogAddKnowledgeBaseBinding.inflate(LayoutInflater.from(this))
        
        // 设置模板选项
        val templates = TemplateType.values().map { 
            when (it) {
                TemplateType.PERSONAL_CHAT -> "个人聊天"
                TemplateType.PRODUCT_SUPPORT -> "产品答疑"
                TemplateType.CUSTOMER_SERVICE -> "售后服务"
                TemplateType.CUSTOM -> "自定义"
            }
        }
        dialogBinding.spinnerTemplate.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            templates
        )

        AlertDialog.Builder(this)
            .setTitle("创建知识库")
            .setView(dialogBinding.root)
            .setPositiveButton("确定") { _, _ ->
                val name = dialogBinding.etName.text.toString()
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入知识库名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val paymentType = when (dialogBinding.rgPaymentType.checkedRadioButtonId) {
                    R.id.rbUserPaid -> PaymentType.USER_PAID
                    R.id.rbEnterprisePaid -> PaymentType.ENTERPRISE_PAID
                    else -> {
                        Toast.makeText(this, "请选择付费类型", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }

                val templateType = TemplateType.values()[dialogBinding.spinnerTemplate.selectedItemPosition]
                createKnowledgeBase(name, paymentType, templateType)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createKnowledgeBase(name: String, paymentType: PaymentType, templateType: TemplateType) {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("name", name)
            put("paymentType", paymentType.name)
            put("aiTemplate", templateType.name)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/create")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        println("createKnowledgeBase $jsonBody");
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeManagementActivity, 
                        "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeManagementActivity,
                                "创建成功", Toast.LENGTH_SHORT).show()
                            // 刷新列表
                            loadKnowledgeList()
                        } else {
                            Toast.makeText(this@KnowledgeManagementActivity,
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeManagementActivity,
                            "创建失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun loadKnowledgeList() {
        val currentItem = binding.viewPager.currentItem
        Log.d("KnowledgeManagement", "当前页面索引: $currentItem")
        
        // 使用 ViewPager2 的方式获取当前的 Fragment
        val fragment = supportFragmentManager.findFragmentByTag("f$currentItem")
            ?: supportFragmentManager.fragments.firstOrNull { it is KnowledgeListFragment }
        
        if (fragment is KnowledgeListFragment) {
            Log.d("KnowledgeManagement", "正在刷新知识库列表")
            fragment.loadKnowledgeBases()
        } else {
            Log.e("KnowledgeManagement", "当前Fragment不是KnowledgeListFragment: ${fragment?.javaClass?.simpleName}")
            // 如果找不到 Fragment，尝试延迟加载
            binding.viewPager.post {
                val delayedFragment = supportFragmentManager.findFragmentByTag("f$currentItem")
                    ?: supportFragmentManager.fragments.firstOrNull { it is KnowledgeListFragment }
                if (delayedFragment is KnowledgeListFragment) {
                    delayedFragment.loadKnowledgeBases()
                }
            }
        }
    }

    private fun deleteKnowledge(fileId: String) {
        // TODO: 实现删除知识的功能
    }

    private fun checkFileStatus(fileId: String) {
        val user = UserManager.getCurrentUser() ?: return

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/api/bailian/status/$fileId")
            .addHeader("Authorization", user.token)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeManagementActivity, "获取状态失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            val data = jsonResponse.getJSONObject("data")
                            adapter.updateItemStatus(fileId, data.getString("fileParseStatus"))
                        }
                    }
                }
            }
        })
    }
} 