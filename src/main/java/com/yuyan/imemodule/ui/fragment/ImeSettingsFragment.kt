package com.yuyan.imemodule.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.ui.utils.addCategory
import com.yuyan.imemodule.ui.utils.addPreference
import com.yuyan.imemodule.ui.auth.LoginActivity
import com.yuyan.imemodule.ui.auth.UserProfileActivity
import com.yuyan.imemodule.ui.knowledge.KnowledgeManagementActivity

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

            // 使用一致的方式添加账号设置分类
            addCategory(R.string.account_settings) {
                isIconSpaceReserved = false
                addPreference(
                    R.string.login_status,
                    "",
                    R.drawable.ic_menu_user
                ) {
                    try {
                        if (UserManager.isLoggedIn()) {
                            // 如果已登录，跳转到个人信息页面
                            startActivity(Intent(requireContext(), UserProfileActivity::class.java))
                        } else {
                            // 如果未登录，跳转到登录页面
                            startActivity(Intent(requireContext(), LoginActivity::class.java))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "操作失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }

                // 添加个人知识库管理入口
                addPreference(
                    R.string.knowledge_management,
                    "",
                    R.drawable.ic_menu_knowledge
                ) {
                    try {
                        if (UserManager.isLoggedIn()) {
                            startActivity(Intent(requireContext(), KnowledgeManagementActivity::class.java))
                        } else {
                            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "操作失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }

                // 初始化登录状态显示
                try {
                    findPreference<Preference>(getString(R.string.login_status))?.let {
                        updateLoginStatusSummary(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 页面恢复时更新登录状态
        try {
            findPreference<Preference>(getString(R.string.login_status))?.let {
                updateLoginStatusSummary(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLoginStatusSummary(preference: Preference) {
        try {
            preference.summary = if (UserManager.isLoggedIn()) {
                UserManager.getCurrentUser()?.username ?: getString(R.string.login)
            } else {
                getString(R.string.not_logged_in)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            preference.summary = getString(R.string.not_logged_in)
        }
    }
}