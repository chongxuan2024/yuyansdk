package com.yuyan.imemodule.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuyan.imemodule.databinding.ActivityRegisterBinding

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            when {
                username.isBlank() -> {
                    Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                }
                email.isBlank() -> {
                    Toast.makeText(this, "请输入邮箱", Toast.LENGTH_SHORT).show()
                }
                password.isBlank() -> {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                }
                password != confirmPassword -> {
                    Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    register(username, email, password)
                }
            }
        }
    }

    private fun register(username: String, email: String, password: String) {
        val jsonBody = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/register")
            .post(RequestBody.create("application/json".toMediaType(), jsonBody.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (response.isSuccessful) {
                            Toast.makeText(this@RegisterActivity, "注册成功", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@RegisterActivity,
                                jsonResponse.getString("error"),
                                Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@RegisterActivity, "解析响应失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
} 