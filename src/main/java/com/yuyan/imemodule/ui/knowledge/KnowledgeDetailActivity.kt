package com.yuyan.imemodule.ui.knowledge

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.model.Role
import com.yuyan.imemodule.databinding.ActivityKnowledgeDetailBinding
import com.yuyan.imemodule.utils.LogUtils
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
import androidx.preference.PreferenceManager

class KnowledgeDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKnowledgeDetailBinding
    private lateinit var adapter: DocumentAdapter
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private lateinit var knowledgeId: String
    private lateinit var knowledgeName: String
    private var isAdmin: Boolean = false
    private var documentCount = 0
    
    // 已下载的文件ID集合
    private val downloadedFiles = mutableSetOf<String>()
    
    companion object {
        private const val EXTRA_KNOWLEDGE_ID = "knowledge_id"
        private const val EXTRA_KNOWLEDGE_NAME = "knowledge_name"
        private const val EXTRA_IS_ADMIN = "is_admin"
        private const val REQUEST_PICK_FILE = 1
        const val EXTRA_SHARED_URIS = "extra_shared_uris"
        const val EXTRA_SHARED_TEXT = "extra_shared_text"
        private const val EXTRA_KNOWLEDGE_BASE_ID = "extra_knowledge_base_id"
        private const val MENU_MEMBER = Menu.FIRST + 1
        private const val REQUEST_WRITE_STORAGE = 112
        private const val PREF_DOWNLOADED_FILES = "downloaded_files"

        fun createIntent(context: Context, knowledgeId: String, knowledgeName: String, isAdmin: Boolean): Intent {
            return Intent(context, KnowledgeDetailActivity::class.java).apply {
                putExtra(EXTRA_KNOWLEDGE_ID, knowledgeId)
                putExtra(EXTRA_KNOWLEDGE_NAME, knowledgeName)
                putExtra(EXTRA_IS_ADMIN, isAdmin)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKnowledgeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        knowledgeId = intent.getStringExtra(EXTRA_KNOWLEDGE_ID) ?: return finish()
        knowledgeName = intent.getStringExtra(EXTRA_KNOWLEDGE_NAME) ?: return finish()
        isAdmin = intent.getBooleanExtra(EXTRA_IS_ADMIN, false)
        
        // 加载已下载文件列表
        loadDownloadedFiles()

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadDocuments()
        setupTreeAnimation()

        // 处理分享的内容
        handleSharedContent()
        
        // 检查存储权限
        checkStoragePermission()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = knowledgeName
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
        
        // 设置下载点击事件
        adapter.setOnDownloadClickListener { document, viewHolder ->
            if (document.isDownloading) {
                // 如果正在下载，取消下载
                cancelDownload(document.fileId)
            } else if (document.isDownloaded) {
                // 如果已下载，打开文件
                openDownloadedFile(document.fileId, document.description)
            } else {
                // 否则开始下载
                downloadFile(document, viewHolder)
            }
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
                                    val fileId = item.getString("fileId")
                                    documents.add(
                                        Document(
                                            id = item.getLong("id"),
                                            uid = item.getLong("uid"),
                                            createTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                                .parse(item.getString("createTime")) ?: Date(),
                                            fileId = fileId,
                                            fileParseStatus = item.getString("fileParseStatus"),
                                            jobId = item.getString("jobId"),
                                            jobStatus = item.getString("jobStatus"),
                                            status = item.getString("status"),
                                            description = item.optString("description", ""),
                                            isDownloaded = isFileDownloaded(fileId)
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
            "添加知识文本 🌱",
            "上传知识文件 🌿",
            "分享知识图片 🍃",
            "导入网页内容 🌸"
        )
        AlertDialog.Builder(this)
            .setTitle("为知识树添加养分")
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
            .setTitle("修剪知识树")
            .setMessage("确定要移除这个知识吗？这可能会影响知识树的生长。")
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
        val actualCount = minOf(documentCount, 15)  // 限制最大层级为20
        val level = "知识树 Level $actualCount"
        val description = when (actualCount) {
            15 -> "恭喜！你的知识树已经长到最高啦！🌟"
            in 10..15 -> "你的知识树已经非常高大了，继续加油！🎄"
            in 4..9 -> "知识树茁壮成长中，装饰也越来越漂亮了！🎁"
            in 1..3 -> "知识树正在慢慢长高，继续添加知识吧！🎀"
            else -> "开始养育你的小知识树吧！⭐"
        }
        val drawable = resources.getIdentifier(
            "knowledge_tree_level$actualCount",
            "drawable",
            packageName
        )

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
        val actualCount = minOf(documentCount, 15)
        updateTreeLevel(documentCount)
        val message = when {
            actualCount >= 15 -> "知识树已经长到最高啦！🌟"
            actualCount >= 6 -> "知识树又长高了，真是棒极了！🎄"
            else -> "知识树长高了一层！⭐"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        loadDocuments()
    }

    private fun handleSharedContent() {
        // 处理分享的文件列表
        intent.getParcelableArrayListExtra<Uri>(EXTRA_SHARED_URIS)?.let { uris ->
            for (uri in uris) {
                uploadFile(uri)
            }
        }

        // 处理分享的文本
        intent.getStringExtra(EXTRA_SHARED_TEXT)?.let { text ->
            uploadText(text)
        }
    }
    
    // 下载文件
    private fun downloadFile(document: Document, viewHolder: DocumentAdapter.ViewHolder) {
        val user = UserManager.getCurrentUser() ?: return
        
        // 检查存储权限
        if (!hasStoragePermission()) {
            requestStoragePermission()
            return
        }
        
        // 获取文件名
        var fileName = document.description
        if (fileName.startsWith("文件：")) {
            fileName = fileName.substring(3)
            if (fileName.contains("...")) {
                fileName = fileName.substring(0, fileName.indexOf("..."))
            }
        } else {
            fileName = "${document.fileId}.txt"
        }
        
        // 构建下载URL
        val fileId = document.fileId
        val url = "https://www.qingmiao.cloud/userapi/knowledge/downloadFile/$fileId"
        
        // 创建进度对话框
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在下载")
            .setMessage("正在下载文件: $fileName")
            .setCancelable(false)
            .setView(layoutInflater.inflate(R.layout.dialog_progress, null))
            .setNegativeButton("取消") { dialog, _ ->
                // 取消下载
                dialog.dismiss()
            }
            .create()
        
        progressDialog.show()
        
        // 在后台线程中下载文件
        Thread {
            try {
                // 构建请求
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", user.token)
                    .addHeader("openid", user.username)
                    .build()
                
                // 创建下载目录
                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "知识库")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                
                // 创建目标文件
                val downloadFile = File(downloadDir, fileName)
                
                // 执行请求
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("下载失败: ${response.code}")
                    }
                    
                    // 获取文件总大小
                    val contentLength = response.body?.contentLength() ?: -1L
                    
                    // 打开输出流
                    val outputStream = FileOutputStream(downloadFile)
                    
                    // 获取输入流
                    val inputStream = response.body?.byteStream() ?: throw IOException("无法获取响应内容")
                    
                    // 缓冲区
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead: Long = 0
                    
                    // 读取数据并更新进度
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // 计算进度
                        val progress = if (contentLength > 0) {
                            (totalBytesRead * 100 / contentLength).toInt()
                        } else {
                            -1
                        }
                        
                        // 更新UI
                        runOnUiThread {
                            // 更新进度对话框
                            val progressBar = progressDialog.findViewById<android.widget.ProgressBar>(R.id.progressBar)
                            val tvProgress = progressDialog.findViewById<android.widget.TextView>(R.id.tvProgress)
                            
                            if (progressBar != null && tvProgress != null) {
                                if (progress >= 0) {
                                    progressBar.progress = progress
                                    tvProgress.text = "$progress%"
                                }
                            }
                        }
                    }
                    
                    // 关闭流
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    
                    // 下载完成，更新UI
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        // 保存下载记录
                        saveDownloadedFileId(fileId)
                        adapter.setDownloaded(fileId, true)
                        
                        // 显示成功消息
                        Toast.makeText(this, "文件下载成功", Toast.LENGTH_SHORT).show()
                        
                        // 打开文件
                        openFile(downloadFile)
                    }
                }
            } catch (e: Exception) {
                // 下载失败，更新UI
                LogUtils.Companion.e(LogUtils.LogType.KNOWLEDGE_BASE, "下载文件失败", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    // 取消下载
    private fun cancelDownload(fileId: String) {
        // 由于我们使用同步下载，取消下载的功能在进度对话框的取消按钮中实现
        // 这里只需要更新UI状态
        adapter.setDownloaded(fileId, false)
        Toast.makeText(this, "已取消下载", Toast.LENGTH_SHORT).show()
    }
    
    // 打开已下载的文件
    private fun openDownloadedFile(fileId: String, description: String) {
        try {
            // 获取文件名
            var fileName = description
            if (fileName.startsWith("文件：")) {
                fileName = fileName.substring(3)
                if (fileName.contains("...")) {
                    fileName = fileName.substring(0, fileName.indexOf("..."))
                }
            } else {
                fileName = "$fileId.txt"
            }
            
            // 构建文件路径
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "知识库/$fileName")
            
            if (!file.exists()) {
                Toast.makeText(this, "文件不存在，请重新下载", Toast.LENGTH_SHORT).show()
                removeDownloadedFileId(fileId)
                adapter.setDownloaded(fileId, false)
                return
            }
            
            // 创建打开文件的Intent
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    applicationContext.packageName + ".provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            // 设置MIME类型
            val mimeType = getMimeType(fileName)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // 启动活动
            startActivity(Intent.createChooser(intent, "打开文件"))
            
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.KNOWLEDGE_BASE, "打开文件失败", e)
            Toast.makeText(this, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 打开文件
    private fun openFile(file: File) {
        try {
            // 创建打开文件的Intent
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    applicationContext.packageName + ".provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            // 设置MIME类型
            val mimeType = getMimeType(file.name)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // 启动活动
            startActivity(Intent.createChooser(intent, "打开文件"))
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.KNOWLEDGE_BASE, "打开文件失败", e)
            Toast.makeText(this, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 获取MIME类型
    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", true) -> "application/pdf"
            fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) -> "application/msword"
            fileName.endsWith(".xls", true) || fileName.endsWith(".xlsx", true) -> "application/vnd.ms-excel"
            fileName.endsWith(".ppt", true) || fileName.endsWith(".pptx", true) -> "application/vnd.ms-powerpoint"
            fileName.endsWith(".txt", true) -> "text/plain"
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            else -> "*/*"
        }
    }
    
    // 保存已下载文件ID
    private fun saveDownloadedFileId(fileId: String) {
        downloadedFiles.add(fileId)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        editor.putStringSet(PREF_DOWNLOADED_FILES, downloadedFiles)
        editor.apply()
    }
    
    // 移除已下载文件ID
    private fun removeDownloadedFileId(fileId: String) {
        downloadedFiles.remove(fileId)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        editor.putStringSet(PREF_DOWNLOADED_FILES, downloadedFiles)
        editor.apply()
    }
    
    // 加载已下载文件列表
    private fun loadDownloadedFiles() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val savedFiles = prefs.getStringSet(PREF_DOWNLOADED_FILES, emptySet()) ?: emptySet()
        downloadedFiles.addAll(savedFiles)
    }
    
    // 检查文件是否已下载
    private fun isFileDownloaded(fileId: String): Boolean {
        return downloadedFiles.contains(fileId)
    }
    
    // 检查存储权限
    private fun checkStoragePermission() {
        if (!hasStoragePermission()) {
            requestStoragePermission()
        }
    }
    
    // 是否有存储权限
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // Android 10及以上使用分区存储，不需要特殊权限
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // 请求存储权限
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
        }
    }
    
    // 权限请求结果处理
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储权限才能下载文件", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 