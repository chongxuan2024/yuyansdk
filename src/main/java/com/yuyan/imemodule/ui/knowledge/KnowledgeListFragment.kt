package com.yuyan.imemodule.ui.knowledge

import android.os.Bundle
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

class KnowledgeListFragment : Fragment() {
    private var _binding: FragmentKnowledgeListBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()
    private lateinit var adapter: KnowledgeBaseAdapter
    private var isAdmin: Boolean = false

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
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentKnowledgeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        loadKnowledgeBases()
    }

    private fun setupRecyclerView() {
        adapter = KnowledgeBaseAdapter(
            isAdmin = isAdmin,
            onItemClick = { knowledgeBase ->
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

    private fun loadKnowledgeBases() {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("isAdmin", isAdmin)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/list")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    binding.swipeRefresh?.isRefreshing = false
                    Toast.makeText(requireContext(),
                        "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    binding.swipeRefresh?.isRefreshing = false
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            val knowledgeBases = mutableListOf<KnowledgeBase>()
                            val dataArray = jsonResponse.getJSONArray("data")
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.getJSONObject(i)
                                knowledgeBases.add(
                                    KnowledgeBase(
                                        id = item.getString("id"),
                                        name = item.getString("name"),
                                        paymentType = PaymentType.valueOf(item.getString("paymentType")),
                                        templateType = TemplateType.valueOf(item.getString("templateType")),
                                        owner = item.getString("owner"),
                                        createdAt = item.getLong("createdAt"),
                                        members = item.getJSONArray("members").let { membersArray ->
                                            List(membersArray.length()) { j ->
                                                val memberObj = membersArray.getJSONObject(j)
                                                Member(
                                                    username = memberObj.getString("username"),
                                                    role = Role.valueOf(memberObj.getString("role"))
                                                )
                                            }
                                        }
                                    )
                                )
                            }
                            adapter.submitList(knowledgeBases)
                        } else {
                            Toast.makeText(requireContext(),
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(),
                            "加载失败", Toast.LENGTH_SHORT).show()
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
        _binding = null
    }
}