package com.yuyan.imemodule.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yuyan.imemodule.R
import com.yuyan.imemodule.utils.LogUploader
import com.yuyan.imemodule.utils.LogUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志管理界面
 */
class LogManagementActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var uploadButton: Button
    private lateinit var clearButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var adapter: LogFileAdapter
    private var logFiles: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_management)

        // 设置标题栏
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "日志管理"

        // 初始化视图
        recyclerView = findViewById(R.id.recycler_view_logs)
        uploadButton = findViewById(R.id.button_upload_log)
        clearButton = findViewById(R.id.button_clear_logs)
        progressBar = findViewById(R.id.progress_bar)
        emptyView = findViewById(R.id.text_empty_logs)

        // 设置RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LogFileAdapter(this, emptyList()) { file ->
            showLogFileOptions(file)
        }
        recyclerView.adapter = adapter

        // 设置按钮点击事件
        uploadButton.setOnClickListener {
            uploadLatestLog()
        }

        clearButton.setOnClickListener {
            showClearLogsConfirmation()
        }

        // 加载日志文件
        loadLogFiles()
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

    private fun loadLogFiles() {
        progressBar.visibility = View.VISIBLE
        
        // 在后台线程加载日志文件
        Thread {
            logFiles = LogUtils.Companion.getAllLogFiles().sortedByDescending { it.lastModified() }
            
            runOnUiThread {
                progressBar.visibility = View.GONE
                
                if (logFiles.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.updateData(logFiles)
                }
            }
        }.start()
    }

    private fun uploadLatestLog() {
        val latestLog = LogUtils.Companion.getLatestLogFile()
        if (latestLog == null) {
            Toast.makeText(this, "没有可上传的日志文件", Toast.LENGTH_SHORT).show()
            return
        }

        uploadLogFile(latestLog)
    }

    private fun uploadLogFile(logFile: File) {
        progressBar.visibility = View.VISIBLE
        uploadButton.isEnabled = false
        clearButton.isEnabled = false

        LogUploader.Companion.uploadLogFile(
            this,
            logFile,
            onSuccess = {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    uploadButton.isEnabled = true
                    clearButton.isEnabled = true
                    Toast.makeText(this, "日志上传成功", Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = { errorMessage ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    uploadButton.isEnabled = true
                    clearButton.isEnabled = true
                    Toast.makeText(this, "上传失败: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showClearLogsConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("清除日志")
            .setMessage("确定要清除所有日志文件吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                clearAllLogs()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearAllLogs() {
        progressBar.visibility = View.VISIBLE
        
        Thread {
            LogUtils.Companion.clearAllLogs()
            
            runOnUiThread {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "所有日志已清除", Toast.LENGTH_SHORT).show()
                loadLogFiles()
            }
        }.start()
    }

    private fun showLogFileOptions(file: File) {
        val options = arrayOf("上传此日志", "查看日志内容", "删除此日志")
        
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> uploadLogFile(file)
                    1 -> showLogContent(file)
                    2 -> deleteLogFile(file)
                }
            }
            .show()
    }

    private fun showLogContent(file: File) {
        try {
            val content = file.readText()
            
            AlertDialog.Builder(this)
                .setTitle(file.name)
                .setMessage(content)
                .setPositiveButton("关闭", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法读取日志内容: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteLogFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("删除日志")
            .setMessage("确定要删除此日志文件吗？")
            .setPositiveButton("确定") { _, _ ->
                if (file.delete()) {
                    Toast.makeText(this, "日志已删除", Toast.LENGTH_SHORT).show()
                    loadLogFiles()
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LogManagementActivity::class.java)
        }
    }
}

/**
 * 日志文件适配器
 */
class LogFileAdapter(
    private val context: Context,
    private var logFiles: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<LogFileAdapter.ViewHolder>() {

    fun updateData(newLogFiles: List<File>) {
        logFiles = newLogFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_log_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = logFiles[position]
        holder.bind(file)
    }

    override fun getItemCount(): Int = logFiles.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.text_log_name)
        private val dateTextView: TextView = itemView.findViewById(R.id.text_log_date)
        private val sizeTextView: TextView = itemView.findViewById(R.id.text_log_size)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(logFiles[position])
                }
            }
        }

        fun bind(file: File) {
            nameTextView.text = file.name
            
            // 格式化日期
            val date = Date(file.lastModified())
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            dateTextView.text = dateFormat.format(date)
            
            // 格式化文件大小
            val size = file.length()
            sizeTextView.text = when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }
    }
} 