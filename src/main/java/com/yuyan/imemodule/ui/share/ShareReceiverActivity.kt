package com.yuyan.imemodule.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yuyan.imemodule.data.model.KnowledgeBase
import com.yuyan.imemodule.data.model.PaymentType
import com.yuyan.imemodule.data.model.TemplateType
import com.yuyan.imemodule.ui.knowledge.KnowledgeDetailActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId

class ShareReceiverActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private var sharedUris: ArrayList<Uri>? = null
    private var sharedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 处理意图
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                handleSendFile(intent)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendMultipleFiles(intent)
            }
            Intent.ACTION_VIEW -> {
                handleViewFile(intent)
            }
            else -> {
                finish()
                return
            }
        }

        // 加载知识库列表并显示选择对话框
        loadKnowledgeBases()
    }

    private fun handleSendFile(intent: Intent) {
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let {
            sharedUris = arrayListOf(it)
        }
    }

    private fun handleSendText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            sharedText = it
        }
    }

    private fun handleSendMultipleFiles(intent: Intent) {
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
            if (uris.isNotEmpty()) {
                sharedUris = uris
            }
        }
    }

    private fun handleViewFile(intent: Intent) {
        intent.data?.let { uri ->
            sharedUris = arrayListOf(uri)
        }
    }

    private fun loadKnowledgeBases() {
        val user = UserManager.getCurrentUser() ?: run {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val jsonBody = JSONObject().apply {
            put("isAdmin", "true")
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/list")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ShareReceiverActivity,
                        "加载知识库失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            val knowledgeBases = mutableListOf<KnowledgeBase>()
                            val dataArray = jsonResponse.getJSONArray("data")
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.getJSONObject(i)
                                val localDateTime = LocalDateTime.parse(item.getString("createTime"))
                                val zoneId = ZoneId.of("UTC")
                                val zonedDateTime = localDateTime.atZone(zoneId)

                                knowledgeBases.add(
                                    KnowledgeBase(
                                        id = item.getString("id"),
                                        name = item.getString("name"),
                                        paymentType = PaymentType.valueOf(item.getString("paymentType")),
                                        templateType = TemplateType.valueOf(item.getString("aiTemplate")),
                                        owner = item.getString("creatorId"),
                                        createdAt = zonedDateTime.toInstant().toEpochMilli(),
                                        creatorUser = item.optString("creatorUser"),
                                        members = emptyList()
                                    )
                                )
                            }

                            showKnowledgeBaseDialog(knowledgeBases)
                        } else {
                            Toast.makeText(this@ShareReceiverActivity,
                                "加载知识库失败", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ShareReceiverActivity,
                            "数据解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        })
    }

    private fun showKnowledgeBaseDialog(knowledgeBases: List<KnowledgeBase>) {
        if (knowledgeBases.isEmpty()) {
            Toast.makeText(this, "没有可用的知识库", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val items = knowledgeBases.map { 
            buildString {
                append(it.name)
//                it.creatorUser?.let { creator ->
//                    append(" (创建者: $creator)")
//                }
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择知识库")
            .setItems(items) { _, which ->
                val selectedKnowledgeBase = knowledgeBases[which]
                openKnowledgeDetail(selectedKnowledgeBase)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun openKnowledgeDetail(knowledgeBase: KnowledgeBase) {
        val intent = KnowledgeDetailActivity.createIntent(
            this,
            knowledgeBase.id.toString(),
            knowledgeBase.name,
            true
        ).apply {
            // 传递分享的文件 URI 列表或文本
            sharedUris?.let { putParcelableArrayListExtra(KnowledgeDetailActivity.EXTRA_SHARED_URIS, it) }
            sharedText?.let { putExtra(KnowledgeDetailActivity.EXTRA_SHARED_TEXT, it) }
        }
        startActivity(intent)
        finish()
    }
}