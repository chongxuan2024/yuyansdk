package com.yuyan.imemodule.ui.knowledge

import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.model.KnowledgeBase
import com.yuyan.imemodule.data.model.Member
import com.yuyan.imemodule.data.model.PaymentType
import com.yuyan.imemodule.data.model.Role
import com.yuyan.imemodule.data.model.TemplateType
import com.yuyan.imemodule.databinding.FragmentKnowledgeListBinding
import com.yuyan.imemodule.prefs.restore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId

class KnowledgeListFragment : Fragment() {
    private var _binding: FragmentKnowledgeListBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private lateinit var adapter: KnowledgeBaseAdapter
    private var isAdmin: Boolean = false
    private val TAG = "KnowledgeListFragment"

    companion object {
        private const val ARG_IS_ADMIN = "is_admin"

        fun newInstance(isAdmin: Boolean) = KnowledgeListFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_IS_ADMIN, isAdmin)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAdmin = arguments?.getBoolean(ARG_IS_ADMIN) ?: false
        Log.d(TAG, "onCreate: isAdmin=$isAdmin, hashCode=${this.hashCode()}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView: hashCode=${this.hashCode()}")
        _binding = FragmentKnowledgeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: hashCode=${this.hashCode()}")
        setupRecyclerView()
        setupSwipeRefresh()
        loadKnowledgeBases()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: hashCode=${this.hashCode()}")
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView")
        adapter = KnowledgeBaseAdapter(
            isAdmin = isAdmin,
            onItemClick = { knowledgeBase ->
                Log.d(TAG, "点击知识库: ${knowledgeBase.name}")
                startActivity(
                    KnowledgeDetailActivity.createIntent(
                        requireContext(),
                        knowledgeBase.id,
                        knowledgeBase.name,
                        isAdmin
                    )
                )
            },
            onMemberClick = { knowledgeBase ->
                Log.d(TAG, "点击成员管理: ${knowledgeBase.name}")
                if (isAdmin) {
                    showMemberManagementDialog(knowledgeBase)
                }
            },
            onDeleteClick = { knowledgeBase ->
                showDeleteConfirmDialog(knowledgeBase)
            },
            onRenameClick = { knowledgeBase ->
                showRenameDialog(knowledgeBase)
            }
        )
        binding.rvKnowledgeList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@KnowledgeListFragment.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh?.setOnRefreshListener {
            loadKnowledgeBases()
        }
    }

    fun refreshData() {
        loadKnowledgeBases()
    }

    public fun loadKnowledgeBases() {
        Log.d(TAG, "开始加载知识库列表, isAdmin=$isAdmin")
        val user = UserManager.getCurrentUser()
        if (user == null) {
            Log.e(TAG, "用户未登录")
            return
        }

        val jsonBody = JSONObject().apply {
            put("isAdmin", isAdmin)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/list")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "发送请求: ${request.url}, headers: ${request.headers}, body: $jsonBody")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "请求失败", e)
                activity?.runOnUiThread {
                    binding.swipeRefresh?.isRefreshing = false
                    Toast.makeText(requireContext(),
                        "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "收到响应: $responseBody")
                
                activity?.runOnUiThread {
                    binding.swipeRefresh?.isRefreshing = false
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            if (jsonResponse.getBoolean("success")) {
                                val knowledgeBases = mutableListOf<KnowledgeBase>()
                                val dataArray = jsonResponse.getJSONArray("data")
                                Log.d(TAG, "解析数据数组，长度: ${dataArray.length()}")
                                for (i in 0 until dataArray.length()) {
                                    val item = dataArray.getJSONObject(i)
                                    val localDateTime = LocalDateTime.parse(item.getString("createTime"))
                                    // 指定时区（比如 UTC）
                                    val zoneId = ZoneId.of("UTC")
                                    val zonedDateTime = localDateTime.atZone(zoneId)

                                    knowledgeBases.add(
                                        KnowledgeBase(
                                            id = item.getString("id"),
                                            name = item.getString("name"),
                                            paymentType = PaymentType.valueOf(item.getString("paymentType")),
                                            templateType = TemplateType.valueOf(item.getString("aiTemplate")),
                                            owner = item.getString("creatorId"),
                                            createdAt = zonedDateTime.toInstant().toEpochMilli() ,
                                            creatorUser = item.getString("creatorUser"),
                                            members = emptyList() // 暂时使用空列表，因为响应中没有 members 字段
                                        )
                                    )
                                }
                                Log.d(TAG, "解析到 ${knowledgeBases.size} 个知识库")
                                adapter.submitList(knowledgeBases)
                            } else {
                                val message = jsonResponse.optString("message", "加载失败")
                                Log.e(TAG, "请求失败: $message")
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析响应失败", e)
                            Toast.makeText(requireContext(), "数据解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e(TAG, "请求失败: ${response.code}")
                        Toast.makeText(requireContext(), "加载失败: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showMemberManagementDialog(knowledgeBase: KnowledgeBase) {
        // TODO: 实现成员管理对话框
    }

    private fun showDeleteConfirmDialog(knowledgeBase: KnowledgeBase) {

        AlertDialog.Builder(requireContext())
            .setTitle("删除知识库")
            .setMessage("确定要删除知识库《${knowledgeBase.name}》吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                deleteKnowledgeBase(knowledgeBase)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteKnowledgeBase(knowledgeBase: KnowledgeBase) {
        val user = UserManager.getCurrentUser() ?: return

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/delete/${knowledgeBase.id}")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            if (jsonResponse.getBoolean("success")) {
                                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                                loadKnowledgeBases() // 重新加载列表
                            } else {
                                Toast.makeText(context, 
                                    jsonResponse.getString("message"), 
                                    Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, 
                                "删除失败: ${e.message}", 
                                Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, 
                            "删除失败: ${response.code}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showRenameDialog(knowledgeBase: KnowledgeBase) {
        val editText = EditText(requireContext()).apply {
            setText(knowledgeBase.name)
            filters = arrayOf(InputFilter.LengthFilter(50))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("修改知识库名称")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    renameKnowledgeBase(knowledgeBase.id, newName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renameKnowledgeBase(knowledgeId: String, newName: String) {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("knowledgeId", knowledgeId)
            put("name", newName)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/rename")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "修改失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            if (jsonResponse.getBoolean("success")) {
                                Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show()
                                loadKnowledgeBases() // 重新加载列表
                            } else {
                                Toast.makeText(context, 
                                    jsonResponse.getString("message"), 
                                    Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, 
                                "修改失败: ${e.message}", 
                                Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, 
                            "修改失败: ${response.code}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: hashCode=${this.hashCode()}")
        _binding = null
    }
}