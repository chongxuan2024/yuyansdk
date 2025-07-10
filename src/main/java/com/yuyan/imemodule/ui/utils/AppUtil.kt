
package com.yuyan.imemodule.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavDeepLinkBuilder
import com.yuyan.imemodule.R
import com.yuyan.imemodule.ui.activity.SettingsActivity
import com.yuyan.imemodule.ui.knowledge.KnowledgeManagementActivity
import com.yuyan.imemodule.utils.LogUtils
import kotlin.system.exitProcess

object AppUtil {

    fun launchSettings(context: Context) {
        context.startActivity<SettingsActivity> {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }

    fun lauchknowledge(context: Context){
        LogUtils.i(LogUtils.LogType.KNOWLEDGE_BASE, "开始启动知识库页面startActivity")
        context.startActivity<KnowledgeManagementActivity> {
           addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }



    private fun launchMainToDest(context: Context, @IdRes dest: Int, arguments: Bundle? = null) {
        NavDeepLinkBuilder(context)
            .setComponentName(SettingsActivity::class.java)
            .setGraph(R.navigation.settings_nav)
            .setDestination(dest)
            .setArguments(arguments)
            .createTaskStackBuilder()
            /**
             * [androidx.core.app.TaskStackBuilder.getIntents] would add unwanted flags
             * [Intent.FLAG_ACTIVITY_CLEAR_TASK] and [Intent.FLAG_ACTIVITY_TASK_ON_HOME]
             * so we must launch the Intent by ourselves
             */
            .editIntentAt(0)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                context.startActivity(this)
            }
    }

    fun launchSettingsToHandwriting(context: Context) =
        launchMainToDest(context, R.id.handwritingSettingsFragment)


    fun launchSettingsToKeyboard(context: Context) =
        launchMainToDest(context, R.id.keyboardFeedbackFragment)

    fun launchSettingsToPrefix(context: Context, arguments: Bundle? = null) =
        launchMainToDest(context, R.id.sidebarSymbolFragment, arguments)

    fun launchMainToThemeList(context: Context) =
        launchMainToDest(context, R.id.themeFragment)



    fun exit() {
        exitProcess(0)
    }
}