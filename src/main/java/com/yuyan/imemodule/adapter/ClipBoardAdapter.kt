package com.yuyan.imemodule.adapter

// 导入所需的Android和自定义组件
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.emoji2.widget.EmojiTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.data.theme.ThemeManager.activeTheme
import com.yuyan.imemodule.database.entry.Clipboard
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.prefs.behavior.ClipboardLayoutMode
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.utils.DevicesUtils.dip2px
import com.yuyan.imemodule.utils.DevicesUtils.px2dip
import splitties.views.dsl.core.margin
import android.app.AlertDialog
import android.widget.Button
import android.widget.Toast
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.yuyan.imemodule.service.ImeService
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

private const val AI_BUTTON_TEXT = "AI回复"

/**
 * 剪切板界面适配器
 */
class ClipBoardAdapter(
    context: Context, 
    datas: MutableList<Clipboard>,
    subMode: SkbMenuMode,
) : RecyclerView.Adapter<ClipBoardAdapter.SymbolHolder>() {
    // 数据源列表
    private var mDatas : MutableList<Clipboard>
    // 上下文对象
    private val mContext: Context

    private val subMode: SkbMenuMode
    // 文本颜色
    private var textColor: Int
    // 剪贴板布局模式
    private var clipboardLayoutCompact: ClipboardLayoutMode

    // 添加Handler用于主线程更新UI
    private val mainHandler = Handler(Looper.getMainLooper())
    // 添加协程作用域
    private val scope = CoroutineScope(Dispatchers.IO)

    // 初始化块
    init {
        mDatas = datas
        this.subMode = subMode
        // 获取当前主题
        val theme = activeTheme
        // 设置文本颜色
        textColor = theme.keyTextColor
        mContext = context
        // 获取剪贴板布局模式设置
//        clipboardLayoutCompact = AppPrefs.getInstance().clipboard.clipboardLayoutCompact.getValue()
        clipboardLayoutCompact = ClipboardLayoutMode.ListView
    }

    // 创建ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolHolder {
        // 创建容器布局
        val mContainer = RelativeLayout(mContext)
        // 设置垂直居中
        mContainer.gravity = Gravity.CENTER_VERTICAL
        // 设置边距
        val marginValue = dip2px(3)
        
        // 根据布局模式设置不同的布局参数
        when (clipboardLayoutCompact){
            // 列表视图模式
            ClipboardLayoutMode.ListView -> {
                mContainer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(marginValue*2, marginValue, marginValue*2, marginValue)
                }
            }
            // 其他模式(网格/紧凑)
            else -> {
                mContainer.layoutParams = GridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(marginValue, marginValue, marginValue, marginValue)
                }
            }
        }

        // 设置容器背景
        mContainer.background = GradientDrawable().apply {
            setColor(activeTheme.keyBackgroundColor)
            setShape(GradientDrawable.RECTANGLE)
            setCornerRadius(ThemeManager.prefs.keyRadius.getValue().toFloat()) // 设置圆角半径
        }

        // 创建内容文本视图
        val viewContext = EmojiTextView(mContext).apply {
            id = R.id.clipboard_adapter_content
            maxLines = 20
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                margin = marginValue
                addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
            }
        }

        // 创建按钮容器
        val buttonContainer = LinearLayout(mContext).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.BELOW, R.id.clipboard_adapter_content)
                setMargins(dip2px(5), dip2px(5), dip2px(5), dip2px(5))
            }
        }

        // 创建 AI 回复按钮
        val detailButton = Button(mContext).apply {
            text = AI_BUTTON_TEXT
            setTextColor(textColor)
            textSize = 12f  // 设置更小的文字大小
            minHeight = 0   // 移除最小高度限制
            minimumHeight = dip2px(32)  // 设置合适的按钮高度
            setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4))  // 设置内边距
            background = GradientDrawable().apply {
                setColor(activeTheme.functionKeyBackgroundColor)
                setShape(GradientDrawable.RECTANGLE)
                setCornerRadius(ThemeManager.prefs.keyRadius.getValue().toFloat())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dip2px(5)
            }
        }

        // 创建还原按钮
        val restoreButton = Button(mContext).apply {
            text = "还原"
            visibility = View.GONE
            setTextColor(textColor)
            textSize = 12f  // 设置更小的文字大小
            minHeight = 0   // 移除最小高度限制
            minimumHeight = dip2px(32)  // 设置合适的按钮高度
            setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4))  // 设置内边距
            background = GradientDrawable().apply {
                setColor(activeTheme.functionKeyBackgroundColor)
                setShape(GradientDrawable.RECTANGLE)
                setCornerRadius(ThemeManager.prefs.keyRadius.getValue().toFloat())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 将按钮添加到按钮容器中
        buttonContainer.addView(detailButton)
        buttonContainer.addView(restoreButton)

        // 将视图添加到主容器中
        mContainer.addView(viewContext)
        mContainer.addView(buttonContainer)
        
        // 创建置顶图标视图
        val viewIvYopTips = ImageView(mContext).apply {
            id = R.id.clipboard_adapter_top_tips
            setImageResource(R.drawable.ic_baseline_top_tips_32)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, 
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
                addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
            }
        }
        
        // 将视图添加到主容器中
        mContainer.addView(viewIvYopTips)  // 确保添加置顶图标
        
        // 修改显示按钮的条件
        if (UserManager.isLoggedIn() && subMode == SkbMenuMode.ClipBoard) {
            buttonContainer.visibility = View.VISIBLE
        } else {
            buttonContainer.visibility = View.GONE
        }
        
        return SymbolHolder(mContainer, detailButton, restoreButton, viewIvYopTips)  // 传入置顶图标
    }

    // 绑定数据到ViewHolder
    override fun onBindViewHolder(holder: SymbolHolder, position: Int) {
        val data = mDatas[position]
        val originalContent = data.content  // 保存原始内容
        
        // 设置内容文本
        holder.textView.text = data.content.replace("\n", "\\n")
        holder.ivTopTips.visibility = if(data.isKeep == 1) View.VISIBLE else View.GONE
        
        // AI 回复按钮点击事件
        holder.detailButton.setOnClickListener {
            showContentDialog(data.content, holder, originalContent)  // 传入原始内容
        }

        // 还原按钮点击事件
        holder.restoreButton.setOnClickListener {
            sendToInputBox(originalContent, holder)
            holder.restoreButton.visibility = View.GONE  // 还原后隐藏按钮
        }
    }

    // 修改发送内容到输入框的方法
    private fun sendToInputBox(text: String, holder: SymbolHolder) {
        // 直接更新文本视图的内容
        holder.textView.text = text
        
        // 同时更新数据源中的内容
        val position = holder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            mDatas[position].content = text
            // 如果需要持久化保存，还可以更新数据库
            (mContext as? ImeService)?.let { service ->
                DataBaseKT.instance.clipboardDao().update(mDatas[position])
            }
        }
    }

    // 修改显示内容的方法
    private fun showContentDialog(content: String, holder: SymbolHolder, originalContent: String) {
        val currentButton = holder.detailButton
        
        if (!UserManager.isLoggedIn()) {
            Toast.makeText(mContext, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        
        currentButton.isEnabled = false
        currentButton.text = "思考中..."

        val client = OkHttpClient()
        val jsonBody = JSONObject().apply {
            put("question", content)
        }

        val user = UserManager.getCurrentUser()!!
        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/chat")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(RequestBody.create("application/json".toMediaType(), jsonBody.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    currentButton.isEnabled = true
                    currentButton.text = AI_BUTTON_TEXT
                    Toast.makeText(mContext, "请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                mainHandler.post {
                    currentButton.isEnabled = true
                    currentButton.text = AI_BUTTON_TEXT
                    if (response.isSuccessful && responseBody != null) {
                        sendToInputBox(responseBody, holder)
                        holder.restoreButton.visibility = View.VISIBLE  // 显示还原按钮
                    } else {
                        Toast.makeText(mContext, "服务器响应错误: ${response.code}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 获取数据项数量
    override fun getItemCount(): Int {
        return mDatas.size
    }

    // ViewHolder内部类
    inner class SymbolHolder(
        view: RelativeLayout, 
        button: Button,
        val restoreButton: Button,
        val ivTopTips: ImageView  // 添加置顶图标引用
    ) : RecyclerView.ViewHolder(view) {
        var textView: TextView
        var detailButton: Button

        init {
            textView = view.findViewById(R.id.clipboard_adapter_content)
            textView.setTextColor(textColor)
            textView.textSize = px2dip(EnvironmentSingleton.instance.candidateTextSize)
            detailButton = button
        }
    }
}
