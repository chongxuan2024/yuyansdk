package com.yuyan.imemodule.ui.auth

import User
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuyan.imemodule.databinding.ActivityLoginBinding

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "登录"
        }

        // 设置Toolbar的返回按钮点击事件
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "请填写用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            login(username, password)
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun login(username: String, password: String) {
        val jsonBody = JSONObject().apply {
            put("username", username)
            put("password", password)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/login")
            .post(RequestBody.create("application/json".toMediaType(), jsonBody.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (response.isSuccessful) {
                            val user = User(
                                username = jsonResponse.getString("username"),
                                email = jsonResponse.optString("email"),
                                token = jsonResponse.getString("token")
                            )
                            UserManager.saveUser(this@LoginActivity, user)
                            Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, 
                                jsonResponse.getString("error"), 
                                Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "解析响应失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
} 