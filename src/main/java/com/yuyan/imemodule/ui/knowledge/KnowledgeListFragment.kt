package com.yuyan.imemodule.ui.knowledge

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.data.model.KnowledgeBase
import com.yuyan.imemodule.data.model.Member
import com.yuyan.imemodule.data.model.PaymentType
import com.yuyan.imemodule.data.model.Role
import com.yuyan.imemodule.data.model.TemplateType
import com.yuyan.imemodule.databinding.FragmentKnowledgeListBinding
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
    private val client = OkHttpClient()
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
                // 处理知识库点击事件，打开知识库详情页面
                startActivity(
                    KnowledgeDetailActivity.createIntent(
                        requireContext(),
                        knowledgeBase.id,
                        isAdmin
                    )
                )
            },
            onMemberClick = { knowledgeBase ->
                Log.d(TAG, "点击成员管理: ${knowledgeBase.name}")
                // 处理成员管理点击事件
                if (isAdmin) {
                    showMemberManagementDialog(knowledgeBase)
                }
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

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: hashCode=${this.hashCode()}")
        _binding = null
    }
}