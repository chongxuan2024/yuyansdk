package com.yuyan.imemodule.adapter

// 导入所需的Android和自定义组件
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
import com.yuyan.imemodule.service.ImeService
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.*
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
import com.yuyan.imemodule.data.model.KnowledgeBase
import com.yuyan.imemodule.data.model.PaymentType
import com.yuyan.imemodule.data.model.TemplateType
import java.time.LocalDateTime
import java.time.ZoneId

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
    private val client = OkHttpClient()
    private val subMode: SkbMenuMode
    // 文本颜色
    private var textColor: Int
    // 剪贴板布局模式
    private var clipboardLayoutCompact: ClipboardLayoutMode

    // 添加Handler用于主线程更新UI
    private val mainHandler = Handler(Looper.getMainLooper())
    // 添加协程作用域
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentSessionId: String? = null
    private var knowledgeBases: List<KnowledgeBase> = listOf()
    private var selectedKnowledgeBaseId: String? = null // null 表示全选

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
        loadKnowledgeBases()
    }

    private fun loadKnowledgeBases() {
        val user = UserManager.getCurrentUser() ?: return

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/list")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    Toast.makeText(mContext, "获取知识库列表失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.getBoolean("success")) {
                        val dataArray = jsonResponse.getJSONArray("data")
                        val newKnowledgeBases = mutableListOf<KnowledgeBase>()
                        
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            val localDateTime = LocalDateTime.parse(item.getString("createTime"))
                            // 指定时区（比如 UTC）
                            val zoneId = ZoneId.of("UTC")
                            val zonedDateTime = localDateTime.atZone(zoneId)

                            newKnowledgeBases.add(
                                KnowledgeBase(
                                    id = item.getString("id"),
                                    name = item.getString("name"),
                                    paymentType = PaymentType.valueOf(item.getString("paymentType")),
                                    templateType = TemplateType.valueOf(item.getString("aiTemplate")),
                                    owner = item.getString("creatorId"),
                                    createdAt = zonedDateTime.toInstant().toEpochMilli() ,
                                    members = emptyList() // 暂时使用空列表，因为响应中没有 members 字段
                                )
                            )

                        }
                        
                        mainHandler.post {
                            knowledgeBases = newKnowledgeBases
                            notifyDataSetChanged()
                        }
                    }
                }
            }
        })
    }

    private fun showKnowledgeBaseDialog(holder: SymbolHolder) {
        val items = arrayOf("全选") + knowledgeBases.map { it.name }.toTypedArray()
        val checkedItem = if (selectedKnowledgeBaseId == null) 0 else {
            knowledgeBases.indexOfFirst { it.id == selectedKnowledgeBaseId } + 1
        }

        AlertDialog.Builder(mContext)
            .setTitle("选择知识库")
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                selectedKnowledgeBaseId = if (which == 0) null else knowledgeBases[which - 1].id
                dialog.dismiss()
                notifyDataSetChanged()
            }
            .show()
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

        // 创建新会话按钮
        val newSessionButton = Button(mContext).apply {
            text = "新会话"
            visibility = View.VISIBLE
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

        // 创建重试按钮
        val retryButton = Button(mContext).apply {
            text = "重试"
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

        // 添加知识库选择按钮
        val selectKnowledgeButton = Button(mContext).apply {
            text = "选择知识库"
            setTextColor(textColor)
            textSize = 12f
            minHeight = 0
            minimumHeight = dip2px(32)
            setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4))
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

        // 将按钮添加到按钮容器中
        buttonContainer.addView(selectKnowledgeButton)
        buttonContainer.addView(detailButton)
        buttonContainer.addView(restoreButton)
        buttonContainer.addView(newSessionButton)
        buttonContainer.addView(retryButton)

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

        return SymbolHolder(mContainer, detailButton, restoreButton, viewIvYopTips, newSessionButton, retryButton, selectKnowledgeButton)  // 传入置顶图标
    }

    // 绑定数据到ViewHolder
    override fun onBindViewHolder(holder: SymbolHolder, position: Int) {
        val data = mDatas[position]
        val originalContent = data.content  // 保存原始内容

        // 设置内容文本
        holder.textView.text = data.content.replace("\n", "\\n")
        holder.ivTopTips.visibility = if(data.isKeep == 1) View.VISIBLE else View.GONE

        // 设置知识库选择按钮文本和点击事件
        holder.selectKnowledgeButton.text = if (selectedKnowledgeBaseId == null) 
            "知识库(全部)" 
        else 
            "知识库(${knowledgeBases.find { it.id == selectedKnowledgeBaseId }?.name ?: "未知"})"
        
        holder.selectKnowledgeButton.setOnClickListener {
            showKnowledgeBaseDialog(holder)
        }

        // AI 回复按钮点击事件
        holder.detailButton.setOnClickListener {
            showContentDialog(data.content, holder, originalContent, selectedKnowledgeBaseId)
        }

        // 还原按钮点击事件
        holder.restoreButton.setOnClickListener {
            sendToInputBox(originalContent, holder)
//            holder.restoreButton.visibility = View.GONE  // 还原后隐藏按钮
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
    private fun showContentDialog(content: String, holder: SymbolHolder, originalContent: String, knowledgeBaseId: String?) {
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
            put("lastSessionId", currentSessionId)
            knowledgeBaseId?.let { put("knowledgeBaseId", it) }
        }

        val user = UserManager.getCurrentUser()!!
        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/chatNew")
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
                        val jsonResponse = JSONObject(responseBody)
                            println(jsonResponse)
                        try {
                            currentSessionId = jsonResponse.getString("sessionId")
                            sendToInputBox(jsonResponse.getString("text"), holder)
                            holder.restoreButton.visibility = View.VISIBLE
                            holder.newSessionButton.visibility = View.VISIBLE
                            holder.retryButton.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            Toast.makeText(mContext, "响应解析错误: ${response.code}",
                                Toast.LENGTH_SHORT).show()
                        }

                    } else {
                        Toast.makeText(mContext, "服务器响应错误: ${response.code}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 清除会话
    private fun clearSession(holder: SymbolHolder) {
        currentSessionId == null;
        Toast.makeText(mContext, "已开始新会话", Toast.LENGTH_SHORT).show();

    }

    // 重试回答
    private fun retryAnswer(content: String, holder: SymbolHolder) {

        val client = OkHttpClient()
        val jsonBody = JSONObject().apply {
            put("question", "答案不满意，请重新检索知识库，提供更加准确的答案")
            put("lastSessionId", currentSessionId)
        }

        val user = UserManager.getCurrentUser()!!
        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/user/chatNew")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(RequestBody.create("application/json".toMediaType(), jsonBody.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    Toast.makeText(mContext, "请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                mainHandler.post {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)

                        try {
                            currentSessionId = jsonResponse.getString("sessionId")
                            sendToInputBox(jsonResponse.getString("text"), holder)
                        } catch (e: Exception) {
                            Toast.makeText(mContext, "响应解析错误: ${response.code}",
                                Toast.LENGTH_SHORT).show()
                        }

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
        val ivTopTips: ImageView,
        val newSessionButton: Button,  // 新增新会话按钮
        val retryButton: Button,        // 新增重试按钮
        val selectKnowledgeButton: Button
    ) : RecyclerView.ViewHolder(view) {
        var textView: TextView
        var detailButton: Button

        init {
            textView = view.findViewById(R.id.clipboard_adapter_content)
            textView.setTextColor(textColor)
            textView.textSize = px2dip(EnvironmentSingleton.instance.candidateTextSize)
            detailButton = button

            // 设置新会话按钮点击事件
            newSessionButton.setOnClickListener {
                clearSession(this)
            }

            // 设置重试按钮点击事件
            retryButton.setOnClickListener {
                retryAnswer(textView.text.toString(), this)
            }
        }
    }
}
