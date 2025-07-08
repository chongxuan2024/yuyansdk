package com.yuyan.imemodule.service

import android.content.ClipboardManager.OnPrimaryClipChangedListener
import com.yuyan.imemodule.application.ImeSdkApplication
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.database.entry.Clipboard
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.LogUtils
import com.yuyan.imemodule.utils.clipboardManager

/**
 * 剪切板监听
 * 移除使用广播监听方式，解决部分手机后台无法启动监听服务异常(API level 31)。
 */
object ClipboardHelper : OnPrimaryClipChangedListener {

    private var isInitialized = false

    fun init() {
        try {
            if (!isInitialized) {
                LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "初始化剪贴板监听器")
                ImeSdkApplication.context.clipboardManager.addPrimaryClipChangedListener(this)
                isInitialized = true
            }
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "初始化剪贴板监听器失败", e)
        }
    }

    override fun onPrimaryClipChanged() {
        try {
            val isClipboardListening = AppPrefs.getInstance().clipboard.clipboardListening.getValue()
            LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "剪贴板内容变化，监听状态: $isClipboardListening")
            
            if(isClipboardListening) {
                val clipboardManager = ImeSdkApplication.context.clipboardManager
                val primaryClip = clipboardManager.primaryClip
                
                if(primaryClip != null && primaryClip.itemCount > 0) {
                    val item = primaryClip.getItemAt(0)
                    val text = item.text
                    
                    if (text != null && text.isNotBlank()) {
                        val data = text.toString()
                        LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "添加新的剪贴板内容: ${if(data.length > 20) data.substring(0, 20) + "..." else data}")
                        
                        // 保存到数据库
                        val clipboard = Clipboard(content = data)
                        DataBaseKT.instance.clipboardDao().insert(clipboard)
                        
                        // 更新建议设置
                        if (AppPrefs.getInstance().clipboard.clipboardSuggestion.getValue()) {
                            AppPrefs.getInstance().internal.clipboardUpdateTime.setValue(System.currentTimeMillis())
                            AppPrefs.getInstance().internal.clipboardUpdateContent.setValue(data)
                        }
                    } else {
                        LogUtils.Companion.w(LogUtils.LogType.CLIPBOARD, "剪贴板内容为空或不是文本")
                    }
                } else {
                    LogUtils.Companion.w(LogUtils.LogType.CLIPBOARD, "剪贴板为空或无法访问")
                }
            }
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "处理剪贴板变化失败", e)
        }
    }
    
    /**
     * 手动添加剪贴板内容，用于测试或特殊场景
     */
    fun addClipboardItem(content: String) {
        try {
            if (content.isNotBlank()) {
                LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "手动添加剪贴板内容: ${if(content.length > 20) content.substring(0, 20) + "..." else content}")
                
                // 保存到数据库
                val clipboard = Clipboard(content = content)
                DataBaseKT.instance.clipboardDao().insert(clipboard)
                
                // 更新建议设置
                if (AppPrefs.getInstance().clipboard.clipboardSuggestion.getValue()) {
                    AppPrefs.getInstance().internal.clipboardUpdateTime.setValue(System.currentTimeMillis())
                    AppPrefs.getInstance().internal.clipboardUpdateContent.setValue(content)
                }
            }
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "手动添加剪贴板内容失败", e)
        }
    }
}
