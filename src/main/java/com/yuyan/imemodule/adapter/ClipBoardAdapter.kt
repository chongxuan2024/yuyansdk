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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * 剪切板界面适配器
 */
class ClipBoardAdapter(context: Context, datas: MutableList<Clipboard>) :
    RecyclerView.Adapter<ClipBoardAdapter.SymbolHolder>() {
    // 数据源列表
    private var mDatas : MutableList<Clipboard>
    // 上下文对象
    private val mContext: Context
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
            maxLines = 3  // 最多显示3行
            ellipsize = TextUtils.TruncateAt.END  // 超出显示省略号
            gravity = Gravity.CENTER
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                margin = marginValue
                addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
            }
        }

        // 创建详情按钮
        val detailButton = Button(mContext).apply {
            id = View.generateViewId()
            text = "AI解答"  // 按钮文字
            setTextColor(textColor)  // 使用与文本相同的颜色
            background = GradientDrawable().apply {
                setColor(activeTheme.functionKeyBackgroundColor)
                setShape(GradientDrawable.RECTANGLE)
                setCornerRadius(ThemeManager.prefs.keyRadius.getValue().toFloat())
            }
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.BELOW, R.id.clipboard_adapter_content)
                setMargins(0, dip2px(5), dip2px(5), dip2px(5))
            }
        }

        // 创建置顶图标视图
        val viewIvYopTips = ImageView(mContext).apply {
            id = R.id.clipboard_adapter_top_tips
            setImageResource(R.drawable.ic_baseline_top_tips_32)
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
                addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
            }
        }

        // 将视图添加到容器中
        mContainer.addView(viewContext)
        mContainer.addView(viewIvYopTips)
        
        // 只在用户登录时显示 AI 解答按钮
        if (UserManager.isLoggedIn()) {
            mContainer.addView(detailButton)
        }
        
        return SymbolHolder(mContainer, detailButton)
    }

    // 绑定数据到ViewHolder
    override fun onBindViewHolder(holder: SymbolHolder, position: Int) {
        val data = mDatas[position]
        // 设置内容文本,将换行符替换为\n显示
        holder.textView.text = data.content.replace("\n", "\\n")
        // 根据是否置顶显示或隐藏图标
        holder.ivTopTips.visibility = if(data.isKeep == 1)View.VISIBLE else View.GONE
        
        // 设置按钮点击事件
        holder.detailButton.setOnClickListener {
            showContentDialog(data.content, holder)  // 传入 holder
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
    private fun showContentDialog(content: String, holder: SymbolHolder) {
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
                    currentButton.text = "AI解答"
                    Toast.makeText(mContext, "请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                mainHandler.post {
                    currentButton.isEnabled = true
                    currentButton.text = "AI解答"
                    if (response.isSuccessful && responseBody != null) {
                        sendToInputBox(responseBody, holder)
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
    inner class SymbolHolder(view: RelativeLayout, button: Button) : RecyclerView.ViewHolder(view) {
        var textView: TextView
        var ivTopTips: ImageView
        var detailButton: Button  // 添加按钮引用
        
        init {
            // 初始化视图引用
            textView = view.findViewById(R.id.clipboard_adapter_content)
            // 设置文本颜色和大小
            textView.setTextColor(textColor)
            textView.textSize = px2dip(EnvironmentSingleton.instance.candidateTextSize)
            ivTopTips = view.findViewById(R.id.clipboard_adapter_top_tips)
            detailButton = button  // 初始化按钮引用
        }
    }
}
