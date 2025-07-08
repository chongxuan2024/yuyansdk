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
import androidx.core.view.marginLeft
import com.yuyan.imemodule.data.model.KnowledgeBase
import com.yuyan.imemodule.data.model.PaymentType
import com.yuyan.imemodule.data.model.TemplateType
import com.yuyan.imemodule.utils.LogUtils
import java.time.LocalDateTime
import java.time.ZoneId


private const val AI_BUTTON_TEXT = "AI答复"
private const val AI_BUTTON_TEXT_RETRY = "重新生成"

/**
 * 剪切板界面适配器
 */
class ClipBoardAdapter(
    context: Context,
    datas: MutableList<Clipboard>,
    subMode: SkbMenuMode,
) : RecyclerView.Adapter<ClipBoardAdapter.SymbolHolder>() {
    // 数据源列表
    private var mDatas: MutableList<Clipboard> = datas
    // 上下文对象
    private val mContext: Context = context
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val subMode: SkbMenuMode = subMode
    // 文本颜色
    private var textColor: Int = activeTheme.keyTextColor
    // 剪贴板布局模式
    private var clipboardLayoutCompact: ClipboardLayoutMode =   ClipboardLayoutMode.ListView

    // 添加Handler用于主线程更新UI
    private val mainHandler = Handler(Looper.getMainLooper())
    // 添加协程作用域
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentSessionId: String? = null
    private var knowledgeBases: List<KnowledgeBase> = listOf()
    private var selectedKnowledgeBaseId: String? = null // null 表示全选

    // 初始化块
    init {
        LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "初始化剪贴板适配器: 数据项数=${datas.size}, 模式=$subMode")

    }

    private fun loadKnowledgeBases() {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("isAdmin", "false")
        }
        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/list")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
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
                                    creatorUser = item.getString("creatorUser"),
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


    // 创建ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolHolder {
        try {
            LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "创建剪贴板项ViewHolder")
            
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
                setPadding(dip2px(14), dip2px(4), dip2px(14), dip2px(4))  // 设置内边距
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
                setPadding(dip2px(14), dip2px(4), dip2px(14), dip2px(4))  // 设置内边距
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

            // 创建新会话按钮
            val newSessionButton = Button(mContext).apply {
                text = "新对话"
                visibility = View.VISIBLE
                setTextColor(textColor)
                textSize = 12f  // 设置更小的文字大小
                minHeight = 0   // 移除最小高度限制
                minimumHeight = dip2px(32)  // 设置合适的按钮高度
                setPadding(dip2px(14), dip2px(4), dip2px(14), dip2px(4))  // 设置内边距
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

            // 替换知识库选择按钮为Spinner
            val selectKnowledgeSpinner = Spinner(mContext).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dip2px(32)
                ).apply {
                    marginEnd = dip2px(5)
                }
                background = GradientDrawable().apply {
                    setColor(activeTheme.functionKeyBackgroundColor)
                    setShape(GradientDrawable.RECTANGLE)
                    setCornerRadius(ThemeManager.prefs.keyRadius.getValue().toFloat())
                    setStroke(dip2px(1), textColor)
                }
            }

            // 将Spinner添加到按钮容器中
            buttonContainer.addView(detailButton)
            buttonContainer.addView(restoreButton)
            buttonContainer.addView(newSessionButton)

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

            return SymbolHolder(mContainer, detailButton, restoreButton, viewIvYopTips, newSessionButton, selectKnowledgeSpinner)
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "创建剪贴板项ViewHolder失败", e)
            
            // 创建一个简单的备用视图
            val fallbackContainer = RelativeLayout(mContext)
            val fallbackText = TextView(mContext).apply {
                text = "加载失败"
                setTextColor(Color.RED)
                gravity = Gravity.CENTER
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
            }
            fallbackContainer.addView(fallbackText)
            
            // 创建空的按钮和图标，避免空指针异常
            val emptyButton = Button(mContext)
            val emptyImageView = ImageView(mContext)
            val emptySpinner = Spinner(mContext)
            
            return SymbolHolder(fallbackContainer, emptyButton, emptyButton, emptyImageView, emptyButton, emptySpinner)
        }
    }

    // 绑定数据到ViewHolder
    override fun onBindViewHolder(holder: SymbolHolder, position: Int) {
        try {
            if (position >= mDatas.size) {
                LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "绑定数据失败: 位置 $position 超出数据范围 ${mDatas.size}")
                return
            }
            
            val data = mDatas[position]
            val originalContent = data.content

            holder.textView.text = data.content.replace("\n", "\\n")
            holder.ivTopTips.visibility = if(data.isKeep == 1) View.VISIBLE else View.GONE

            // 设置Spinner的适配器和选中项
            val items = listOf("知识库(全部)") + knowledgeBases.map { "知识库(${it.name})" }
            val adapter = object : ArrayAdapter<String>(
                mContext,
                android.R.layout.simple_spinner_item,
                items
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    (view as TextView).apply {
                        setTextColor(textColor)
                        textSize = 12f
                        setPadding(dip2px(14), 0, dip2px(14), 0)
                    }
                    return view
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    (view as TextView).apply {
                        setTextColor(textColor)
                        textSize = 12f
                        setPadding(dip2px(14), dip2px(8), dip2px(14), dip2px(8))
                        background = GradientDrawable().apply {
                            setColor(activeTheme.keyBackgroundColor)
                        }
                    }
                    return view
                }
            }
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            holder.selectKnowledgeSpinner.adapter = adapter

            // 设置当前选中项
            val selectedPosition = if (selectedKnowledgeBaseId == null) 0 else {
                knowledgeBases.indexOfFirst { it.id == selectedKnowledgeBaseId }.let { if (it == -1) 0 else it + 1 }
            }
            holder.selectKnowledgeSpinner.setSelection(selectedPosition)

            // 设置选择监听器
            holder.selectKnowledgeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedKnowledgeBaseId = if (position == 0) null else knowledgeBases[position - 1].id
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedKnowledgeBaseId = null
                }
            }

            // AI 回复按钮点击事件
            holder.detailButton.setOnClickListener {
                // 如果selectedKnowledgeBaseId为空，使用所有知识库ID
                val knowledgeBaseIds = if (selectedKnowledgeBaseId == null) {
                    knowledgeBases.map { it.id }.joinToString(",")
                } else {
                    selectedKnowledgeBaseId
                }
                
                showContentDialog(data.content, holder, originalContent, knowledgeBaseIds)
            }

            // 还原按钮点击事件
            holder.restoreButton.setOnClickListener {
                holder.detailButton.text = AI_BUTTON_TEXT
                sendToInputBox(originalContent, holder)
            }
            
            // 新会话按钮点击事件
            holder.newSessionButton.setOnClickListener {
                clearSession(holder)
            }
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "绑定数据到ViewHolder失败: position=$position", e)
        }
    }

    // 修改发送内容到输入框的方法
    private fun sendToInputBox(text: String, holder: SymbolHolder) {
        try {
            LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "发送内容到输入框: $text")
            
            // 直接更新文本视图的内容
            holder.textView.text = text

            // 同时更新数据源中的内容
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION && position < mDatas.size) {
                mDatas[position].content = text
                // 如果需要持久化保存，还可以更新数据库
                (mContext as? ImeService)?.let { service ->
                    DataBaseKT.instance.clipboardDao().update(mDatas[position])
                }
            }
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "发送内容到输入框失败", e)
        }
    }

    // 修改显示内容的方法
    private fun showContentDialog(content: String, holder: SymbolHolder, originalContent: String, knowledgeBaseIds: String?) {
        try {
            LogUtils.Companion.i(LogUtils.LogType.AI_QUERY, "发起AI查询: content=$content, knowledgeBaseIds=$knowledgeBaseIds")
            
            val currentButton = holder.detailButton

            if (!UserManager.isLoggedIn()) {
                Toast.makeText(mContext, "请先登录", Toast.LENGTH_SHORT).show()
                return
            }

            currentButton.isEnabled = false
            currentButton.text = "思考中..."
            var targetContent = content
            if (content != originalContent) {
                targetContent = "回答不满意，请重新生成"
            }

            val jsonBody = JSONObject().apply {
                put("question", targetContent)
                put("lastSessionId", currentSessionId)
                knowledgeBaseIds?.let { put("knowledgeBaseIds", it) }
            }

            val user = UserManager.getCurrentUser()!!
            val request = Request.Builder()
                .url("https://www.qingmiao.cloud/userapi/knowledge/chatNew")
                .addHeader("Authorization", user.token)
                .addHeader("openid", user.username)
                .post(RequestBody.create("application/json".toMediaType(), jsonBody.toString()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    LogUtils.Companion.e(LogUtils.LogType.AI_REPLY, "AI回复请求失败", e)
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
                        currentButton.text = AI_BUTTON_TEXT_RETRY
                        if (response.isSuccessful && responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                LogUtils.Companion.i(LogUtils.LogType.AI_REPLY, "AI回复成功: $responseBody")
                                
                                currentSessionId = jsonResponse.getString("sessionId")
                                val aiReply = jsonResponse.getString("text")
                                sendToInputBox(aiReply, holder)
                                holder.restoreButton.visibility = View.VISIBLE
                                holder.newSessionButton.visibility = View.VISIBLE
                            } catch (e: Exception) {
                                LogUtils.Companion.e(LogUtils.LogType.AI_REPLY, "解析AI回复失败", e)
                                Toast.makeText(mContext, "响应解析错误: ${response.code}",
                                    Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            LogUtils.Companion.e(LogUtils.LogType.AI_REPLY, "AI回复失败，状态码: ${response.code}")
                            Toast.makeText(mContext, "服务器响应错误: ${response.code}",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.AI_QUERY, "显示内容对话框失败", e)
            Toast.makeText(mContext, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 清除会话
    private fun clearSession(holder: SymbolHolder) {
        try {
            LogUtils.Companion.i(LogUtils.LogType.AI_QUERY, "清除会话")
            currentSessionId = null
            Toast.makeText(mContext, "已开始新会话", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.AI_QUERY, "清除会话失败", e)
        }
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
        val newSessionButton: Button,
        val selectKnowledgeSpinner: Spinner
    ) : RecyclerView.ViewHolder(view) {
        var textView: TextView
        var detailButton: Button

        init {
            try {
                textView = view.findViewById(R.id.clipboard_adapter_content)
                textView.setTextColor(textColor)
                textView.textSize = px2dip(EnvironmentSingleton.instance.candidateTextSize)
                detailButton = button
            } catch (e: Exception) {
                LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "初始化SymbolHolder失败", e)
                // 创建备用TextView，避免空指针异常
                textView = TextView(view.context)
                detailButton = button
            }
        }
    }
}
