package com.yuyan.imemodule.ui.knowledge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.data.model.Role
import com.yuyan.imemodule.databinding.ActivityKnowledgeMemberBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class KnowledgeMemberActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKnowledgeMemberBinding
    private lateinit var adapter: MemberAdapter
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private lateinit var knowledgeId: String
    private var isAdmin: Boolean = false

    companion object {
        private const val EXTRA_KNOWLEDGE_ID = "knowledge_id"
        private const val EXTRA_IS_ADMIN = "is_admin"

        fun createIntent(context: Context, knowledgeId: String, isAdmin: Boolean): Intent {
            return Intent(context, KnowledgeMemberActivity::class.java).apply {
                putExtra(EXTRA_KNOWLEDGE_ID, knowledgeId)
                putExtra(EXTRA_IS_ADMIN, isAdmin)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKnowledgeMemberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        knowledgeId = intent.getStringExtra(EXTRA_KNOWLEDGE_ID) ?: return finish()
        isAdmin = intent.getBooleanExtra(EXTRA_IS_ADMIN, false)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadMembers()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "成员管理"
    }

    private fun setupRecyclerView() {
        adapter = MemberAdapter()
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(this@KnowledgeMemberActivity)
            adapter = this@KnowledgeMemberActivity.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadMembers()
        }
    }

    private fun setupFab() {
        binding.fabAddMember.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.fabAddMember.setOnClickListener {
            showAddMemberDialog()
        }
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

    private fun showAddMemberDialog() {
        val input = EditText(this).apply {
            hint = "请输入用户名"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("添加成员")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val username = input.text.toString()
                if (username.isNotBlank()) {
                    addMember(username)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addMember(username: String) {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("knowledgeId", knowledgeId)
            put("username", username)
            put("role", Role.USER.name)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/addMember")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeMemberActivity,
                        "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeMemberActivity,
                                "添加成功", Toast.LENGTH_SHORT).show()
                            loadMembers()
                        } else {
                            Toast.makeText(this@KnowledgeMemberActivity,
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeMemberActivity,
                            "添加失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun loadMembers() {
        val user = UserManager.getCurrentUser() ?: return

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/members/$knowledgeId")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this@KnowledgeMemberActivity,
                        "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    binding.swipeRefresh.isRefreshing = false
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            if (jsonResponse.getBoolean("success")) {
                                val members = mutableListOf<Member>()
                                val dataArray = jsonResponse.getJSONArray("data")
                                
                                for (i in 0 until dataArray.length()) {
                                    val item = dataArray.getJSONObject(i)
                                    members.add(
                                        Member(
                                            username = item.getString("username"),
                                            role = Role.valueOf(item.getString("role"))
                                        )
                                    )
                                }
                                
                                adapter.submitList(members)
                                binding.emptyView.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
                            } else {
                                Toast.makeText(this@KnowledgeMemberActivity,
                                    jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@KnowledgeMemberActivity,
                                "数据解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeMemberActivity,
                            "加载失败: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
} 