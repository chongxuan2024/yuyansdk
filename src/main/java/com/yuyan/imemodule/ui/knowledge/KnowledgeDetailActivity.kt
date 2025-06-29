package com.yuyan.imemodule.ui.knowledge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.model.Role
import com.yuyan.imemodule.databinding.ActivityKnowledgeDetailBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.view.animation.AnimationUtils
import android.view.animation.Animation

class KnowledgeDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKnowledgeDetailBinding
    private lateinit var adapter: DocumentAdapter
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private lateinit var knowledgeId: String
    private var isAdmin: Boolean = false
    private var documentCount = 0

    companion object {
        private const val EXTRA_KNOWLEDGE_ID = "knowledge_id"
        private const val EXTRA_IS_ADMIN = "is_admin"
        private const val REQUEST_PICK_FILE = 1
        private const val EXTRA_KNOWLEDGE_BASE_ID = "extra_knowledge_base_id"
        private const val MENU_MEMBER = Menu.FIRST + 1

        fun createIntent(context: Context, knowledgeId: String, isAdmin: Boolean): Intent {
            return Intent(context, KnowledgeDetailActivity::class.java).apply {
                putExtra(EXTRA_KNOWLEDGE_ID, knowledgeId)
                putExtra(EXTRA_IS_ADMIN, isAdmin)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKnowledgeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        knowledgeId = intent.getStringExtra(EXTRA_KNOWLEDGE_ID) ?: return finish()
        isAdmin = intent.getBooleanExtra(EXTRA_IS_ADMIN, false)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadDocuments()
        setupTreeAnimation()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showUploadDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isAdmin) {
            menu.add(Menu.NONE, MENU_MEMBER, Menu.NONE, "成员管理")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            MENU_MEMBER -> {
                showMemberManagement()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(isAdmin)
        binding.rvDocuments.apply {
            layoutManager = LinearLayoutManager(this@KnowledgeDetailActivity)
            adapter = this@KnowledgeDetailActivity.adapter
        }

        // 设置删除点击事件
        adapter.setOnDeleteClickListener { document ->
            showDeleteConfirmDialog(document)
        }

        // 设置下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            loadDocuments()
        }
    }

    private fun loadDocuments() {
        val user = UserManager.getCurrentUser() ?: return
        val knowledgeBaseId = intent.getStringExtra(EXTRA_KNOWLEDGE_ID) ?: return

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/detail/$knowledgeBaseId")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this@KnowledgeDetailActivity,
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
                                val documents = mutableListOf<Document>()
                                val dataArray = jsonResponse.getJSONArray("data")
                                
                                for (i in 0 until dataArray.length()) {
                                    val item = dataArray.getJSONObject(i)
                                    documents.add(
                                        Document(
                                            id = item.getLong("id"),
                                            uid = item.getLong("uid"),
                                            createTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                                .parse(item.getString("createTime")) ?: Date(),
                                            fileId = item.getString("fileId"),
                                            fileParseStatus = item.getString("fileParseStatus"),
                                            jobId = item.getString("jobId"),
                                            jobStatus = item.getString("jobStatus"),
                                            status = item.getString("status"),
                                            description = item.optString("description", "")
                                        )
                                    )
                                }
                                
                                adapter.submitList(documents)
                                
                                // 如果列表为空，显示空状态
                                binding.emptyView.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE
                                documentCount = documents.size
                                updateTreeLevel(documentCount)
                            } else {
                                Toast.makeText(this@KnowledgeDetailActivity,
                                    jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                "数据解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeDetailActivity,
                            "加载失败: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showMemberManagement() {
        val intent = KnowledgeMemberActivity.createIntent(this, knowledgeId, isAdmin)
        startActivity(intent)
    }

    private fun showUploadDialog() {
        val items = arrayOf(
            "添加知识装饰 🎁",
            "上传知识彩球 🎄",
            "分享知识星星 ⭐",
            "导入知识礼物 🎀"
        )
        AlertDialog.Builder(this)
            .setTitle("为圣诞树添加装饰")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showTextInputDialog()
                    1 -> pickFile()
                    2 -> pickImage()
                    3 -> showUrlInputDialog()
                }
            }
            .show()
    }

    private fun showTextInputDialog() {
        val input = EditText(this).apply {
            hint = "请输入文本内容"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }

        AlertDialog.Builder(this)
            .setTitle("上传文本")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    uploadText(text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showUrlInputDialog() {
        val input = EditText(this).apply {
            hint = "请输入HTML地址"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        AlertDialog.Builder(this)
            .setTitle("上传HTML地址")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val url = input.text.toString()
                if (url.isNotBlank()) {
                    uploadUrl(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_PICK_FILE)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    private fun uploadText(text: String) {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("text", text)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/uploadText/$knowledgeId")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeDetailActivity,
                        "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                "上传成功", Toast.LENGTH_SHORT).show()
                            uploadSuccess()
                        } else {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeDetailActivity,
                            "上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun uploadUrl(url: String) {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("url", url)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/uploadUrl/$knowledgeId")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeDetailActivity,
                        "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                "上传成功", Toast.LENGTH_SHORT).show()
                            uploadSuccess()
                        } else {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeDetailActivity,
                            "上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun uploadFile(uri: Uri) {
        val user = UserManager.getCurrentUser() ?: return

        // 创建临时文件
        val fileName = getFileName(uri)
        val tempFile = File(cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName,
                tempFile.asRequestBody("application/octet-stream".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/uploadFile/$knowledgeId")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                tempFile.delete()
                runOnUiThread {
                    Toast.makeText(this@KnowledgeDetailActivity,
                        "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                tempFile.delete()
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                "上传成功", Toast.LENGTH_SHORT).show()
                            uploadSuccess()
                        } else {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeDetailActivity,
                            "上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    fileName = cursor.getString(index)
                }
            }
        }
        return fileName
    }

    private fun showDeleteConfirmDialog(document: Document) {
        AlertDialog.Builder(this)
            .setTitle("修剪圣诞树")
            .setMessage("确定要移除这个知识装饰吗？这可能会影响圣诞树的生长。")
            .setPositiveButton("确定") { _, _ ->
                deleteDocument(document)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteDocument(document: Document) {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("id", document.id)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/document/delete")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeDetailActivity,
                        "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                "删除成功", Toast.LENGTH_SHORT).show()
                            documentCount--
                            updateTreeLevel(documentCount)
                            loadDocuments() // 重新加载文档列表
                        } else {
                            Toast.makeText(this@KnowledgeDetailActivity,
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@KnowledgeDetailActivity,
                            "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun setupTreeAnimation() {
        val growAnimation = AnimationUtils.loadAnimation(this, R.anim.tree_grow)
        binding.ivTree.startAnimation(growAnimation)
    }

    private fun updateTreeLevel(documentCount: Int) {
        val (level, description, drawable) = when {
            documentCount >= 17 -> Triple(
                "圣诞树 Level 3",
                "你的知识树已经长成一棵巨大的圣诞树，散发着智慧的光芒！🌟",
                R.drawable.knowledge_tree_level3
            )
            documentCount >= 8 -> Triple(
                "圣诞树 Level 2",
                "你的知识树正在茁壮成长，装饰也越来越丰富了！🎄",
                R.drawable.knowledge_tree_level2
            )
            else -> Triple(
                "圣诞树 Level 1",
                "开始装饰你的知识圣诞树吧！🎁",
                R.drawable.knowledge_tree_level1
            )
        }

        binding.apply {
            tvTreeLevel.text = level
            tvTreeDescription.text = description
            if (ivTree.tag != drawable) {
                ivTree.tag = drawable
                ivTree.setImageResource(drawable)
                ivTree.startAnimation(AnimationUtils.loadAnimation(this@KnowledgeDetailActivity, R.anim.tree_grow))
            }
        }
    }

    private fun uploadSuccess() {
        documentCount++
        updateTreeLevel(documentCount)
        val message = when {
            documentCount >= 17 -> "知识树已经长成参天大树！🌟"
            documentCount >= 8 -> "知识树又长高了一层！🎄"
            else -> "知识树获得了新的装饰！🎁"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        loadDocuments()
    }
} 