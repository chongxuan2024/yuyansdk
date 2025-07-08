package com.yuyan.imemodule.utils

import android.content.Context
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 日志上传服务类，用于将日志文件上传到服务器
 */
class LogUploader {
    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        /**
         * 上传日志文件到服务器
         * @param context 上下文
         * @param logFile 日志文件
         * @param onSuccess 上传成功回调
         * @param onFailure 上传失败回调
         */
        fun uploadLogFile(
            context: Context,
            logFile: File,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val user = UserManager.getCurrentUser()
            if (user == null) {
                onFailure("用户未登录")
                return
            }

            if (!logFile.exists() || logFile.length() == 0L) {
                onFailure("日志文件不存在或为空")
                return
            }

            try {
                // 创建MultipartBody
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "logFile",
                        logFile.name,
                        logFile.asRequestBody("application/octet-stream".toMediaType())
                    )
                    .build()

                // 创建请求
                val request = Request.Builder()
                    .url("https://www.qingmiao.cloud/userapi/knowledge/uploadLog")
                    .addHeader("Authorization", user.token)
                    .addHeader("openid", user.username)
                    .post(requestBody)
                    .build()

                // 发送请求
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        LogUtils.Companion.e(LogUtils.LogType.SYSTEM, "上传日志文件失败: ${e.message}", e)
                        onFailure("网络错误: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                if (jsonResponse.getBoolean("success")) {
                                    LogUtils.Companion.i(LogUtils.LogType.SYSTEM, "日志上传成功: $responseBody")
                                    onSuccess()
                                } else {
                                    val message = jsonResponse.optString("message", "上传失败")
                                    LogUtils.Companion.e(LogUtils.LogType.SYSTEM, "日志上传失败: $message")
                                    onFailure(message)
                                }
                            } catch (e: Exception) {
                                LogUtils.Companion.e(LogUtils.LogType.SYSTEM, "解析响应失败: ${e.message}", e)
                                onFailure("解析响应失败: ${e.message}")
                            }
                        } else {
                            LogUtils.Companion.e(LogUtils.LogType.SYSTEM, "上传失败，状态码: ${response.code}")
                            onFailure("上传失败，状态码: ${response.code}")
                        }
                    }
                })
            } catch (e: Exception) {
                LogUtils.Companion.e(LogUtils.LogType.SYSTEM, "准备上传请求失败: ${e.message}", e)
                onFailure("准备上传请求失败: ${e.message}")
            }
        }
    }
} 