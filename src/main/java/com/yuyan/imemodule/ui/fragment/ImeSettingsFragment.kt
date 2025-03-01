package com.yuyan.imemodule.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.yuyan.imemodule.BuildConfig
import com.yuyan.imemodule.R
import com.yuyan.imemodule.ui.utils.addCategory
import com.yuyan.imemodule.ui.utils.addPreference
import com.yuyan.imemodule.ui.auth.LoginActivity

class ImeSettingsFragment : PreferenceFragmentCompat() {

    private fun PreferenceCategory.addDestinationPreference(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        @IdRes destination: Int
    ) {
        addPreference(title, icon = icon) {
            findNavController().navigate(destination)
        }
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addCategory(R.string.input_methods) {
                isIconSpaceReserved = false
                addDestinationPreference(
                    R.string.setting_ime_input,
                    R.drawable.ic_menu_language,
                    R.id.action_settingsFragment_to_inputSettingsFragment
                )
                if(!BuildConfig.offline) {
                    addDestinationPreference(
                        R.string.ime_settings_handwriting,
                        R.drawable.ic_menu_handwriting,
                        R.id.action_settingsFragment_to_handwritingSettingsFragment
                    )
                }
            }
            addCategory(R.string.keyboard) {
                isIconSpaceReserved = false
                addDestinationPreference(
                    R.string.theme,
                    R.drawable.ic_menu_theme,
                    R.id.action_settingsFragment_to_themeSettingsFragment
                )
                addDestinationPreference(
                    R.string.keyboard_feedback,
                    R.drawable.ic_menu_touch,
                    R.id.action_settingsFragment_to_keyboardFeedbackFragment
                )
                addDestinationPreference(
                    R.string.setting_ime_keyboard,
                    R.drawable.ic_menu_keyboard,
                    R.id.action_settingsFragment_to_keyboardSettingFragment
                )
                addDestinationPreference(
                    R.string.aiclipboard,
                    R.drawable.ic_menu_clipboard_ai,
                    R.id.action_settingsFragment_to_clipboardSettingsFragment
                )

                addDestinationPreference(
                    R.string.full_display_keyboard,
                    R.drawable.ic_menu_keyboard_full,
                    R.id.action_settingsFragment_to_fullDisplayKeyboardFragment
                )
            }
//            addCategory(R.string.advanced) {
//                isIconSpaceReserved = false
//                addDestinationPreference(
//                    R.string.setting_ime_other,
//                    R.drawable.ic_menu_more_horiz,
//                    R.id.action_settingsFragment_to_otherSettingsFragment
//                )
//                addPreference(R.string.feedback,"",
//                    R.drawable.ic_menu_edit,) {
//                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CustomConstant.FEEDBACK_TXC_REPO)))
//                }
//                addDestinationPreference(
//                    R.string.about,
//                    R.drawable.ic_menu_feedback,
//                    R.id.action_settingsFragment_to_aboutFragment
//                )
//            }
        }

        // 添加账号设置分类
        addPreferencesFromResource(R.xml.ime_settings_account)
        
        // 获取账号状态首选项
        findPreference<Preference>("login_status")?.apply {
            setOnPreferenceClickListener {
                if (UserManager.isLoggedIn()) {
                    // 如果已登录，显示退出登录对话框
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.logout)
                        .setMessage("确定要退出登录吗？")
                        .setPositiveButton("确定") { _, _ ->
                            UserManager.logout(requireContext())
                            updateLoginStatus()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    // 如果未登录，跳转到登录页面
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
                true
            }
        }

        // 初始化登录状态显示
        updateLoginStatus()
    }

    override fun onResume() {
        super.onResume()
        // 页面恢复时更新登录状态
        updateLoginStatus()
    }

    private fun updateLoginStatus() {
        findPreference<Preference>("login_status")?.apply {
            if (UserManager.isLoggedIn()) {
                val user = UserManager.getCurrentUser()
                summary = user?.username ?: "已登录"  // 显示用户名
            } else {
                summary = "未登录"
            }
        }
    }
}