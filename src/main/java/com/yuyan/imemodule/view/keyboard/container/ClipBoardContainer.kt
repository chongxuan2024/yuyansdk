package com.yuyan.imemodule.view.keyboard.container

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.R
import com.yuyan.imemodule.adapter.ClipBoardAdapter
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.database.entry.Clipboard
import com.yuyan.imemodule.libs.recyclerview.SwipeMenu
import com.yuyan.imemodule.libs.recyclerview.SwipeMenuBridge
import com.yuyan.imemodule.libs.recyclerview.SwipeMenuItem
import com.yuyan.imemodule.libs.recyclerview.SwipeRecyclerView
import com.yuyan.imemodule.manager.InputModeSwitcherManager
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.prefs.behavior.ClipboardLayoutMode
import com.yuyan.imemodule.prefs.behavior.PopupMenuMode
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import com.yuyan.imemodule.service.ClipboardHelper
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.utils.DevicesUtils
import com.yuyan.imemodule.utils.LogUtils
import com.yuyan.imemodule.view.keyboard.InputView
import com.yuyan.imemodule.view.keyboard.KeyboardManager
import com.yuyan.imemodule.view.keyboard.manager.CustomGridLayoutManager
import splitties.dimensions.dp
import splitties.views.textResource
import kotlin.math.ceil

/**
 * 粘贴板列表键盘容器
 *
 * 使用RecyclerView实现垂直ListView列表布局。
 */
@SuppressLint("ViewConstructor")
class ClipBoardContainer(context: Context, inputView: InputView) : BaseContainer(context, inputView) {
    private val mPaint : Paint = Paint() // 测量字符串长度
    private val mRVSymbolsView: SwipeRecyclerView = SwipeRecyclerView(context)
    private var mTVLable: TextView? = null
    private var itemMode:SkbMenuMode? = null

    init {
        LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "初始化剪贴板容器")
        mPaint.textSize = dp(22f)
        initView(context)
    }

    private fun initView(context: Context) {
        try {
            mTVLable = TextView(context).apply {
                textResource = R.string.clipboard_empty_ltip
                gravity = Gravity.CENTER
                setTextColor(ThemeManager.activeTheme.keyTextColor)
                textSize = DevicesUtils.px2dip(EnvironmentSingleton.instance.candidateTextSize)
            }
            mRVSymbolsView.setItemAnimator(null)
            val layoutParams2 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            mRVSymbolsView.layoutParams = layoutParams2
            this.addView(mRVSymbolsView)
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "初始化剪贴板视图失败", e)
            // 创建一个简单的错误提示
            val errorText = TextView(context).apply {
                text = "加载剪贴板失败，请重试"
                gravity = Gravity.CENTER
                setTextColor(ThemeManager.activeTheme.keyTextColor)
                textSize = DevicesUtils.px2dip(EnvironmentSingleton.instance.candidateTextSize)
            }
            this.addView(errorText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    /**
     * 显示候选词界面 , 点击候选词时执行
     */
    fun showClipBoardView(item: SkbMenuMode) {
        try {
            LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "显示剪贴板视图: mode=$item")
            
            CustomConstant.lockClipBoardEnable = false
            itemMode = item
            mRVSymbolsView.setHasFixedSize(true)
            
            // 从数据库获取数据
            val copyContents : MutableList<Clipboard> = if(itemMode == SkbMenuMode.ClipBoard) {
                try {
                    val clipboards = DataBaseKT.instance.clipboardDao().getAll()
                    LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "获取剪贴板数据: ${clipboards.size}条")
                    clipboards.toMutableList()
                } catch (e: Exception) {
                    LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "获取剪贴板数据失败", e)
                    // 如果数据库访问失败，尝试添加一个测试项
                    val testItem = Clipboard("测试项 - 请尝试复制新内容")
                    ClipboardHelper.addClipboardItem(testItem.content)
                    mutableListOf(testItem)
                }
            } else {
                try {
                    val phrases = DataBaseKT.instance.phraseDao().getAll()
                    LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "获取短语数据: ${phrases.size}条")
                    phrases.map { line -> Clipboard(line.content) }.toMutableList()
                } catch (e: Exception) {
                    LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "获取短语数据失败", e)
                    mutableListOf(Clipboard("示例短语"))
                }
            }

            // 设置布局管理器
            val manager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            mRVSymbolsView.setLayoutManager(manager)
            
            // 处理空视图
            val viewParent = mTVLable?.parent
            if (viewParent != null) {
                (viewParent as ViewGroup).removeView(mTVLable)
            }
            
            if(copyContents.isEmpty()){
                this.addView(mTVLable, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                LogUtils.Companion.w(LogUtils.LogType.CLIPBOARD, "剪贴板/短语列表为空")
            } else {
                LogUtils.Companion.d(LogUtils.LogType.CLIPBOARD, "显示剪贴板/短语列表: ${copyContents.size}条")
            }
            
            // 创建适配器
            val adapter = ClipBoardAdapter(context, copyContents, item)
            mRVSymbolsView.setAdapter(null)
            
            // 设置点击事件
            mRVSymbolsView.setOnItemClickListener{ _: View?, position: Int ->
                if (position < copyContents.size) {
                    LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "点击剪贴板项: position=$position")
                    inputView.responseLongKeyEvent(Pair(PopupMenuMode.Text, copyContents[position].content))
                    if(!CustomConstant.lockClipBoardEnable) {
                        KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                    }
                }
            }
            
            // 设置滑动菜单
            mRVSymbolsView.setSwipeMenuCreator{ _: SwipeMenu, rightMenu: SwipeMenu, position: Int ->
                val topItem = SwipeMenuItem(mContext).apply {
                    setImage(if(itemMode == SkbMenuMode.ClipBoard) {
                        val data: Clipboard = copyContents[position]
                        if(data.isKeep == 1)R.drawable.ic_baseline_untop_circle_32
                        else R.drawable.ic_baseline_top_circle_32
                    }
                    else R.drawable.ic_menu_edit)
                }
                rightMenu.addMenuItem(topItem)
                val deleteItem = SwipeMenuItem(mContext).apply {
                    setImage(R.drawable.ic_menu_delete)
                }
                rightMenu.addMenuItem(deleteItem)
            }
            
            // 设置菜单点击事件
            mRVSymbolsView.setOnItemMenuClickListener { menuBridge: SwipeMenuBridge, position: Int ->
                menuBridge.closeMenu()
                if (position >= copyContents.size) {
                    LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "菜单点击位置无效: position=$position, size=${copyContents.size}")
                    return@setOnItemMenuClickListener
                }
                
                if(itemMode == SkbMenuMode.ClipBoard){
                    if(menuBridge.position == 0) {
                        LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "切换剪贴板项置顶状态: position=$position")
                        val data: Clipboard = copyContents[position]
                        data.isKeep = 1 - data.isKeep
                        DataBaseKT.instance.clipboardDao().update(data)
                        showClipBoardView(SkbMenuMode.ClipBoard)
                    } else if(menuBridge.position == 1){
                        LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "删除剪贴板项: position=$position")
                        val data: Clipboard = copyContents.removeAt(position)
                        DataBaseKT.instance.clipboardDao().deleteByContent(data.content)
                        mRVSymbolsView.adapter?.notifyItemRemoved(position)
                        if (copyContents.isEmpty()) {
                            this.addView(mTVLable, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                        }
                    }
                } else {
                    val content = copyContents[position].content
                    if(menuBridge.position == 0) {
                        LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "编辑短语: position=$position")
                        inputView.onSettingsMenuClick(SkbMenuMode.AddPhrases, content)
                        KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                    } else if(menuBridge.position == 1){
                        LogUtils.Companion.i(LogUtils.LogType.CLIPBOARD, "删除短语: position=$position")
                        DataBaseKT.instance.phraseDao().deleteByContent(content)
                        showClipBoardView(SkbMenuMode.Phrases)
                    }
                }
            }
            
            // 设置适配器
            mRVSymbolsView.setAdapter(adapter)
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "显示剪贴板视图失败", e)
            // 显示错误提示
            Toast.makeText(context, "加载剪贴板失败，请重试", Toast.LENGTH_SHORT).show()
            
            // 尝试创建一个简单的视图
            val errorText = TextView(context).apply {
                text = "加载失败，请重试"
                gravity = Gravity.CENTER
                setTextColor(ThemeManager.activeTheme.keyTextColor)
            }
            
            this.removeAllViews()
            this.addView(errorText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    private val mHashMapSymbols = HashMap<Int, Int>() //候选词索引列数对应表
    private fun calculateColumn(data : MutableList<Clipboard>) {
        try {
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
        } catch (e: Exception) {
            LogUtils.Companion.e(LogUtils.LogType.CLIPBOARD, "计算列宽失败", e)
        }
    }

    private fun getSymbolsCount(data: String, itemWidth:Int): Int {
        return if (!TextUtils.isEmpty(data)) ceil(mPaint.measureText(data).div(itemWidth)).toInt() else 0
    }
    
    fun getMenuMode():SkbMenuMode? {
        return itemMode
    }
}
