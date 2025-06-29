package com.yuyan.imemodule.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yuyan.imemodule.R
import com.yuyan.imemodule.databinding.ActivityUserProfileBinding

class UserProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.user_profile)
        }

        // 设置Toolbar的返回按钮点击事件
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // 显示用户信息
        UserManager.getCurrentUser()?.let { user ->
            binding.tvUsername.text = getString(R.string.username_format, user.username)
            binding.tvEmail.text = getString(R.string.email_format, user.email ?: getString(R.string.email_not_set))
        }

        // 退出登录按钮
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    UserManager.logout(this)
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
} 