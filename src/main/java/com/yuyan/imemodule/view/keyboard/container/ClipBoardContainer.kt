package com.yuyan.imemodule.view.keyboard.container

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.yuyan.imemodule.R
import com.yuyan.imemodule.adapter.ClipBoardAdapter
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.data.model.KnowledgeBase
import com.yuyan.imemodule.data.model.PaymentType
import com.yuyan.imemodule.data.model.TemplateType
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.database.entry.Clipboard
import com.yuyan.imemodule.libs.recyclerview.*
import com.yuyan.imemodule.manager.InputModeSwitcherManager
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.prefs.behavior.ClipboardLayoutMode
import com.yuyan.imemodule.prefs.behavior.PopupMenuMode
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.utils.DevicesUtils
import com.yuyan.imemodule.view.keyboard.InputView
import com.yuyan.imemodule.view.keyboard.KeyboardManager
import com.yuyan.imemodule.view.keyboard.manager.CustomGridLayoutManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import splitties.dimensions.dp
import splitties.views.textResource
import kotlin.math.ceil
import java.io.IOException

/**
 * 粘贴板列表键盘容器
 *
 * 使用RecyclerView实现垂直ListView列表布局。
 */
@SuppressLint("ViewConstructor")
class ClipBoardContainer(context: Context, private val inputView: InputView) : LinearLayout(context) {
    private val mPaint : Paint = Paint() // 测量字符串长度
    private val mRVSymbolsView: SwipeRecyclerView = SwipeRecyclerView(context)
    private var mTVLable: TextView? = null
    private var itemMode:SkbMenuMode? = null
    private var currentKnowledgeBase: KnowledgeBase? = null
    private val client = OkHttpClient()

    private val headerView = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16, 8, 16, 8)

        // 添加输入框
        val etInput = EditText(context).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            hint = "输入问题..."
            setSingleLine()
        }
        addView(etInput)

        // 添加AI问答按钮
        val btnAsk = MaterialButton(context).apply {
            text = "AI问答"
            setOnClickListener {
                val question = etInput.text.toString()
                if (question.isNotBlank()) {
                    askAI(question)
                    etInput.text.clear()
                }
            }
        }
        addView(btnAsk)

        // 添加切换知识库按钮
        val btnSwitch = MaterialButton(context).apply {
            text = "切换知识库"
            setOnClickListener {
                showKnowledgeBaseDialog()
            }
        }
        addView(btnSwitch)
    }

    init {
        mPaint.textSize = dp(22f)
        initView(context)
        initKnowledgeBase()
    }

    private fun initView(context: Context) {
        orientation = LinearLayout.VERTICAL
        addView(headerView)

        mTVLable = TextView(context).apply {
            textResource = R.string.clipboard_empty_ltip
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.activeTheme.keyTextColor)
            textSize = DevicesUtils.px2dip(EnvironmentSingleton.instance.candidateTextSize)
        }
        mRVSymbolsView.setItemAnimator(null)
        val layoutParams2 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        mRVSymbolsView.layoutParams = layoutParams2
        addView(mRVSymbolsView)
    }

    private fun initKnowledgeBase() {
        val user = UserManager.getCurrentUser() ?: return

        val jsonBody = JSONObject().apply {
            put("isAdmin", false)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/list")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.getBoolean("success")) {
                        val dataArray = jsonResponse.getJSONArray("data")
                        if (dataArray.length() > 0) {
                            val firstKnowledgeBase = dataArray.getJSONObject(0)
                            currentKnowledgeBase = KnowledgeBase(
                                id = firstKnowledgeBase.getString("id"),
                                name = firstKnowledgeBase.getString("name"),
                                paymentType = PaymentType.valueOf(firstKnowledgeBase.getString("paymentType")),
                                templateType = TemplateType.valueOf(firstKnowledgeBase.getString("templateType")),
                                owner = firstKnowledgeBase.getString("owner"),
                                createdAt = firstKnowledgeBase.getLong("createdAt")
                            )
                        }
                    }
                }
            }
        })
    }

    private fun askAI(question: String) {
        val user = UserManager.getCurrentUser() ?: return
        val knowledgeBase = currentKnowledgeBase ?: return

        val jsonBody = JSONObject().apply {
            put("question", question)
            put("knowledgeBaseId", knowledgeBase.id)
        }

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/ask")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.getBoolean("success")) {
                        val answer = jsonResponse.getString("data")
                        // 保存到剪贴板数据库
                        DataBaseKT.instance.clipboardDao().insert(Clipboard(answer))
                        showClipBoardView(SkbMenuMode.ClipBoard)
                    }
                }
            }
        })
    }

    private fun showKnowledgeBaseDialog() {
        val user = UserManager.getCurrentUser() ?: return

        val request = Request.Builder()
            .url("https://www.qingmiao.cloud/userapi/knowledge/list")
            .addHeader("Authorization", user.token)
            .addHeader("openid", user.username)
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.getBoolean("success")) {
                        val dataArray = jsonResponse.getJSONArray("data")
                        val items = Array(dataArray.length()) { i ->
                            val kb = dataArray.getJSONObject(i)
                            kb.getString("name")
                        }
                        
                        post {
                            AlertDialog.Builder(context)
                                .setTitle("选择知识库")
                                .setItems(items) { _, which ->
                                    val selectedKb = dataArray.getJSONObject(which)
                                    currentKnowledgeBase = KnowledgeBase(
                                        id = selectedKb.getString("id"),
                                        name = selectedKb.getString("name"),
                                        paymentType = PaymentType.valueOf(selectedKb.getString("paymentType")),
                                        templateType = TemplateType.valueOf(selectedKb.getString("templateType")),
                                        owner = selectedKb.getString("owner"),
                                        createdAt = selectedKb.getLong("createdAt")
                                    )
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                }
            }
        })
    }

    /**
     * 显示候选词界面 , 点击候选词时执行
     */
    fun showClipBoardView(item: SkbMenuMode) {
        CustomConstant.lockClipBoardEnable = false
        itemMode = item
        mRVSymbolsView.setHasFixedSize(true)
        val copyContents : MutableList<Clipboard> =
            if(itemMode == SkbMenuMode.ClipBoard) {
                DataBaseKT.instance.clipboardDao().getAll().toMutableList()
            } else {
                DataBaseKT.instance.phraseDao().getAll().map { line -> Clipboard(line.content) }.toMutableList()
            }
        var manager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

//        val manager =  when (AppPrefs.getInstance().clipboard.clipboardLayoutCompact.getValue()){
//            ClipboardLayoutMode.ListView ->  LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
//            ClipboardLayoutMode.GridView -> CustomGridLayoutManager(context, 2)
//            ClipboardLayoutMode.FlexboxView -> {
//                calculateColumn(copyContents)
//                CustomGridLayoutManager(context, 6).apply {
//                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
//                        override fun getSpanSize(i: Int) = mHashMapSymbols[i] ?: 1
//                    }
//                }
//            }
//        }
        mRVSymbolsView.setLayoutManager(manager)
        val viewParent = mTVLable?.parent
        if (viewParent != null) {
            (viewParent as ViewGroup).removeView(mTVLable)
        }
        if(copyContents.size == 0){
            this.addView(mTVLable, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        val adapter = ClipBoardAdapter(context, copyContents, item)
        mRVSymbolsView.setAdapter(null)
        mRVSymbolsView.setOnItemClickListener{ _: View?, position: Int ->
            inputView.responseLongKeyEvent(Pair(PopupMenuMode.Text, copyContents[position].content))
            if(!CustomConstant.lockClipBoardEnable)KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
        }
        mRVSymbolsView.setSwipeMenuCreator{ _: SwipeMenu, rightMenu: SwipeMenu, position: Int ->
            val topItem = SwipeMenuItem(context).apply {
                setImage(if(itemMode == SkbMenuMode.ClipBoard) {
                    val data: Clipboard = copyContents[position]
                    if(data.isKeep == 1)R.drawable.ic_baseline_untop_circle_32
                    else R.drawable.ic_baseline_top_circle_32
                }
                else R.drawable.ic_menu_edit)
            }
            rightMenu.addMenuItem(topItem)
            val deleteItem = SwipeMenuItem(context).apply {
                setImage(R.drawable.ic_menu_delete)
            }
            rightMenu.addMenuItem(deleteItem)
        }
        mRVSymbolsView.setOnItemMenuClickListener { menuBridge: SwipeMenuBridge, position: Int ->
            menuBridge.closeMenu()
            if(itemMode == SkbMenuMode.ClipBoard){
                if(menuBridge.position == 0) {
                    val data: Clipboard = copyContents[position]
                    data.isKeep = 1 - data.isKeep
                    DataBaseKT.instance.clipboardDao().update(data)
                    showClipBoardView(SkbMenuMode.ClipBoard)
                } else if(menuBridge.position == 1){
                    val data: Clipboard = copyContents.removeAt(position)
                    DataBaseKT.instance.clipboardDao().deleteByContent(data.content)
                    mRVSymbolsView.adapter?.notifyItemRemoved(position)
                }
            } else {
                val content = copyContents[position].content
                if(menuBridge.position == 0) {
                    inputView.onSettingsMenuClick(SkbMenuMode.AddPhrases, content)
                    KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                } else if(menuBridge.position == 1){
                    DataBaseKT.instance.phraseDao().deleteByContent(content)
                    showClipBoardView(SkbMenuMode.Phrases)
                }
            }
        }
        mRVSymbolsView.setAdapter(adapter)
    }

    private val mHashMapSymbols = HashMap<Int, Int>() //候选词索引列数对应表
    private fun calculateColumn(data : MutableList<Clipboard>) {
        mHashMapSymbols.clear()
        val itemWidth = EnvironmentSingleton.instance.skbWidth/6 - dp(10)
        var mCurrentColumn = 0
        val contents = data.map { it.content }
        contents.forEachIndexed { position, candidate ->
            var count = getSymbolsCount(candidate, itemWidth)
            var nextCount = 0
            if (contents.size > position + 1) {
                val nextCandidate = contents[position + 1]
                nextCount = getSymbolsCount(nextCandidate, itemWidth)
            }
            mCurrentColumn = if (mCurrentColumn + count + nextCount > 6) {
                count = 6 - mCurrentColumn
                0
            } else  (mCurrentColumn + count) % 6
            mHashMapSymbols[position] = count
        }
    }

    private fun getSymbolsCount(data: String, itemWidth:Int): Int {
        return if (!TextUtils.isEmpty(data)) ceil(mPaint.measureText(data).div(itemWidth)).toInt() else 0
    }
    fun getMenuMode():SkbMenuMode? {
       return itemMode
    }
}
