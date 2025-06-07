package com.yuyan.imemodule.ui.knowledge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.databinding.ActivityKnowledgeManagementBinding
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
    private val client = OkHttpClient()
    private val PICK_FILE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKnowledgeManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        loadKnowledgeList()
    }

    private fun setupRecyclerView() {
        adapter = KnowledgeAdapter(
            onDeleteClick = { fileId -> deleteKnowledge(fileId) },
            onRefreshStatus = { fileId -> checkFileStatus(fileId) }
        )
        binding.rvKnowledgeList.apply {
            layoutManager = LinearLayoutManager(this@KnowledgeManagementActivity)
            adapter = this@KnowledgeManagementActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            loadKnowledgeList()
        }

        binding.btnUploadText.setOnClickListener {
            showTextInputDialog()
        }

        binding.btnUploadFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, PICK_FILE_REQUEST)
        }
    }

    private fun showTextInputDialog() {
        val dialog = TextInputDialog(this)
        dialog.setOnTextSubmitted { text ->
            uploadText(text)
        }
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val fileName = getFileName(uri)
                if (isValidFileType(fileName)) {
                    uploadFile(uri, fileName)
                } else {
                    Toast.makeText(this, "不支持的文件类型", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isValidFileType(fileName: String): Boolean {
        val validExtensions = listOf(".txt", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx")
        return validExtensions.any { fileName.lowercase().endsWith(it) }
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

    private fun uploadText(text: String) {
        val user = UserManager.getCurrentUser() ?: return

        val requestBody = FormBody.Builder()
            .add("text", text)
            .build()

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/text")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeManagementActivity, "上传失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeManagementActivity, "上传成功", Toast.LENGTH_SHORT).show()
                            loadKnowledgeList()
                        } else {
                            Toast.makeText(this@KnowledgeManagementActivity, 
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeManagementActivity, "上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun uploadFile(uri: Uri, fileName: String) {
        val user = UserManager.getCurrentUser() ?: return

        // 创建临时文件
        val tempFile = File(cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, tempFile.asRequestBody("application/octet-stream".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/file")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeManagementActivity, "上传失败", Toast.LENGTH_SHORT).show()
                }
                tempFile.delete()
            }

            override fun onResponse(call: Call, response: Response) {
                tempFile.delete()
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@KnowledgeManagementActivity, "上传成功", Toast.LENGTH_SHORT).show()
                            loadKnowledgeList()
                        } else {
                            Toast.makeText(this@KnowledgeManagementActivity, 
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeManagementActivity, "上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun loadKnowledgeList() {
        val user = UserManager.getCurrentUser() ?: return

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/listFile")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KnowledgeManagementActivity, "获取列表失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            val dataArray = jsonResponse.getJSONArray("data")
                            val knowledgeItems = mutableListOf<KnowledgeItem>()
                            
                            for (i in 0 until dataArray.length()) {
                                val taskObject = dataArray.getJSONObject(i)
                                knowledgeItems.add(
                                    KnowledgeItem(
                                        fileId = taskObject.getString("fileId"),
                                        fileName = taskObject.optString("description", "未命名文件"),
                                        status = taskObject.getString("fileParseStatus"),
                                        description = taskObject.optString("description", "")
                                    )
                                )
                            }
                            adapter.updateItems(knowledgeItems)
                        } else {
                            Toast.makeText(this@KnowledgeManagementActivity, 
                                jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KnowledgeManagementActivity, "获取列表失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
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