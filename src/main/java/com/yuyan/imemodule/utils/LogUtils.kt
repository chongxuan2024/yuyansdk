package com.yuyan.imemodule.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yuyan.imemodule.application.ImeSdkApplication
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 日志工具类，用于记录应用程序日志并保存到文件
 */
class LogUtils {
    // 日志级别
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    // 日志类型
    enum class LogType {
        KNOWLEDGE_BASE, CLIPBOARD, AI_QUERY, AI_REPLY, SYSTEM
    }
    
    companion object {
        private const val TAG = "AIIME"
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB
        private const val LOG_FOLDER_NAME = "AIIMELogs"
        
        private val logExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        
        // 日志开关，默认开启
        private var isLoggingEnabled = true
        
        // OkHttp 客户端
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        /**
         * 设置日志开关
         */
        fun setLoggingEnabled(enabled: Boolean) {
            isLoggingEnabled = enabled
        }
        
        /**
         * 获取日志开关状态
         */
        fun isLoggingEnabled(): Boolean {
            return isLoggingEnabled
        }
        
        /**
         * 记录调试日志
         */
        fun d(type: LogType, message: String) {
            log(LogLevel.DEBUG, type, message)
        }
        
        /**
         * 记录信息日志
         */
        fun i(type: LogType, message: String) {
            log(LogLevel.INFO, type, message)
        }
        
        /**
         * 记录警告日志
         */
        fun w(type: LogType, message: String) {
            log(LogLevel.WARN, type, message)
        }
        
        /**
         * 记录错误日志
         */
        fun e(type: LogType, message: String, throwable: Throwable? = null) {
            val fullMessage = if (throwable != null) {
                "$message\n${Log.getStackTraceString(throwable)}"
            } else {
                message
            }
            log(LogLevel.ERROR, type, fullMessage)
        }
        
        /**
         * 记录日志
         */
        private fun log(level: LogLevel, type: LogType, message: String) {
            val timestamp = dateFormat.format(Date())
            val logMessage = "[$timestamp] [${level.name}] [${type.name}] $message"
            
            // 输出到Logcat
            when (level) {
                LogLevel.DEBUG -> Log.d(TAG, logMessage)
                LogLevel.INFO -> Log.i(TAG, logMessage)
                LogLevel.WARN -> Log.w(TAG, logMessage)
                LogLevel.ERROR -> Log.e(TAG, logMessage)
            }
            
            // 如果日志开关打开，保存到文件并上传到服务器
            if (isLoggingEnabled) {
                saveToFile(logMessage)
                uploadLogToServer(level, type, message)
            }
        }
        
        /**
         * 保存日志到文件
         */
        private fun saveToFile(logMessage: String) {
            logExecutor.execute {
                try {
                    val logFile = getLogFile()
                    
                    // 检查文件大小，如果超过最大大小，创建新文件
                    if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                        createNewLogFile()
                    }
                    
                    BufferedWriter(FileWriter(logFile, true)).use { writer ->
                        writer.append(logMessage)
                        writer.newLine()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "保存日志到文件失败", e)
                }
            }
        }
        
        /**
         * 上传日志到服务器
         */
        private fun uploadLogToServer(level: LogLevel, type: LogType, message: String) {
            val user = UserManager.getCurrentUser() ?: return
            
            logExecutor.execute {
                try {
                    // 构建请求体
                    val jsonBody = JSONObject().apply {
                        put("level", level.name)
                        put("type", type.name)
                        put("message", message)
                        put("timestamp", System.currentTimeMillis())
                        put("deviceInfo", android.os.Build.MODEL)
                    }
                    
                    // 创建请求
                    val request = Request.Builder()
                        .url("https://www.qingmiao.cloud/userapi/knowledge/uploadLogMessage")
                        .addHeader("Authorization", user.token)
                        .addHeader("openid", user.username)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    
                    // 发送请求
                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e(TAG, "上传日志消息失败: ${e.message}")
                        }
                        
                        override fun onResponse(call: Call, response: Response) {
                            if (!response.isSuccessful) {
                                Log.e(TAG, "上传日志消息失败，状态码: ${response.code}")
                            }
                            response.close()
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "准备上传日志消息请求失败: ${e.message}")
                }
            }
        }
        
        /**
         * 获取当前日志文件
         */
        private fun getLogFile(): File {
            val context = ImeSdkApplication.context
            val logDir = getLogDirectory(context)
            
            // 确保目录存在
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            // 当前日期作为文件名
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "ime_log_$currentDate.log")
            
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile()
                } catch (e: IOException) {
                    Log.e(TAG, "创建日志文件失败", e)
                }
            }
            
            return logFile
        }
        
        /**
         * 创建新的日志文件
         */
        private fun createNewLogFile(): File {
            val context = ImeSdkApplication.context
            val logDir = getLogDirectory(context)
            
            // 使用时间戳创建唯一文件名
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "ime_log_$timestamp.log")
            
            try {
                logFile.createNewFile()
            } catch (e: IOException) {
                Log.e(TAG, "创建新日志文件失败", e)
            }
            
            return logFile
        }
        
        /**
         * 获取日志目录
         */
        private fun getLogDirectory(context: Context): File {
            return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                // 使用外部存储
                File(context.getExternalFilesDir(null), LOG_FOLDER_NAME)
            } else {
                // 使用内部存储
                File(context.filesDir, LOG_FOLDER_NAME)
            }
        }
        
        /**
         * 获取所有日志文件
         */
        fun getAllLogFiles(): List<File> {
            val context = ImeSdkApplication.context
            val logDir = getLogDirectory(context)
            
            if (!logDir.exists()) {
                return emptyList()
            }
            
            return logDir.listFiles { file -> file.isFile && file.name.endsWith(".log") }?.toList() ?: emptyList()
        }
        
        /**
         * 获取最近的日志文件
         */
        fun getLatestLogFile(): File? {
            return getAllLogFiles().maxByOrNull { it.lastModified() }
        }
        
        /**
         * 清除所有日志文件
         */
        fun clearAllLogs() {
            logExecutor.execute {
                val logFiles = getAllLogFiles()
                for (file in logFiles) {
                    file.delete()
                }
            }
        }
    }
} 