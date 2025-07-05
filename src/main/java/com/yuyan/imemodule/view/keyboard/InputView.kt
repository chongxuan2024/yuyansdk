package com.yuyan.imemodule.view.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import com.yuyan.imemodule.R
import com.yuyan.imemodule.callback.CandidateViewListener
import com.yuyan.imemodule.callback.IResponseKeyEvent
import com.yuyan.imemodule.data.emojicon.EmojiconData.SymbolPreset
import com.yuyan.imemodule.data.emojicon.YuyanEmojiCompat
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.database.entry.Phrase
import com.yuyan.imemodule.entity.keyboard.SoftKey
import com.yuyan.imemodule.manager.InputModeSwitcherManager
import com.yuyan.imemodule.prefs.AppPrefs.Companion.getInstance
import com.yuyan.imemodule.prefs.behavior.KeyboardOneHandedMod
import com.yuyan.imemodule.prefs.behavior.PopupMenuMode
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import com.yuyan.imemodule.service.DecodingInfo
import com.yuyan.imemodule.service.ImeService
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.ui.utils.InputMethodUtil
import com.yuyan.imemodule.utils.DevicesUtils
import com.yuyan.imemodule.utils.KeyboardLoaderUtil
import com.yuyan.imemodule.utils.StringUtils
import com.yuyan.imemodule.view.CandidatesBar
import com.yuyan.imemodule.view.ComposingView
import com.yuyan.imemodule.view.FullDisplayKeyboardBar
import com.yuyan.imemodule.view.keyboard.container.CandidatesContainer
import com.yuyan.imemodule.view.keyboard.container.ClipBoardContainer
import com.yuyan.imemodule.view.keyboard.container.InputViewParent
import com.yuyan.imemodule.view.keyboard.container.SymbolContainer
import com.yuyan.imemodule.view.keyboard.container.T9TextContainer
import com.yuyan.imemodule.view.popup.PopupComponent
import com.yuyan.imemodule.view.preference.ManagedPreference
import com.yuyan.imemodule.view.widget.ImeEditText
import com.yuyan.imemodule.view.widget.LifecycleRelativeLayout
import com.yuyan.inputmethod.CustomEngine
import com.yuyan.inputmethod.core.CandidateListItem
import com.yuyan.inputmethod.util.T9PinYinUtils
import splitties.bitflags.hasFlag
import splitties.views.bottomPadding
import splitties.views.rightPadding
import kotlin.math.absoluteValue
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import android.os.Handler
import android.os.Looper
import android.app.Dialog
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.content.res.ColorStateList

/**
 * 输入法主界面。
 * 包含拼音显示、候选词栏、键盘界面等。
 * 在 该类中处理界面绘制、、输入逻辑等为输入法核心处理类。
 * 注: 所有键盘自定义 View禁用构造方法警告，且不创建含AttributeSet的构造方法。为了实现代码混淆效果。
 */

@SuppressLint("ViewConstructor")
class InputView(context: Context, service: ImeService) : LifecycleRelativeLayout(context), IResponseKeyEvent {
    private val clipboardItemTimeout = getInstance().clipboard.clipboardItemTimeout.getValue()
    private var chinesePrediction = true
    var isAddPhrases = false
    var isAddAIQuery = false
    private var mEtAddPhrasesContent: ImeEditText? = null
    private var mSearchButton: ImageButton? = null

    private var tvAddPhrasesTips:TextView? = null
    private var service: ImeService
    private var mImeState = ImeState.STATE_IDLE // 当前的输入法状态
    private var mChoiceNotifier = ChoiceNotifier()
    private lateinit var mComposingView: ComposingView // 组成字符串的View，用于显示输入的拼音。
    lateinit var mSkbRoot: RelativeLayout
    lateinit var mSkbCandidatesBarView: CandidatesBar //候选词栏根View
    private lateinit var mHoderLayoutLeft: LinearLayout
    private lateinit var mHoderLayoutRight: LinearLayout
    private lateinit var mOnehandHoderLayout: LinearLayout
    lateinit var mAddPhrasesLayout: RelativeLayout
    private lateinit var mLlKeyboardBottomHolder: LinearLayout
    private lateinit var mRightPaddingKey: ManagedPreference.PInt
    private lateinit var mBottomPaddingKey: ManagedPreference.PInt
    private var mFullDisplayKeyboardBar:FullDisplayKeyboardBar? = null

    init {
        this.service = service
        initNavbarBackground(service)
        initView(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun initView(context: Context) {
        if (!::mSkbRoot.isInitialized) {
            mSkbRoot = LayoutInflater.from(context).inflate(R.layout.sdk_skb_container, this, false) as RelativeLayout
            addView(mSkbRoot)
            mSkbCandidatesBarView = mSkbRoot.findViewById(R.id.candidates_bar)
            mHoderLayoutLeft = mSkbRoot.findViewById(R.id.ll_skb_holder_layout_left)
            mHoderLayoutRight = mSkbRoot.findViewById(R.id.ll_skb_holder_layout_right)
            mAddPhrasesLayout = LayoutInflater.from(context).inflate(R.layout.skb_add_phrases_container, mSkbRoot, false) as RelativeLayout
            addView(mAddPhrasesLayout,  LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                addRule(ABOVE, mSkbRoot.id)
                addRule(ALIGN_LEFT, mSkbRoot.id)
            })
            val mIvcSkbContainer:InputViewParent = mSkbRoot.findViewById(R.id.skb_input_keyboard_view)
            KeyboardManager.instance.setData(mIvcSkbContainer, this)
            mLlKeyboardBottomHolder =  mSkbRoot.findViewById(R.id.iv_keyboard_holder)
            mComposingView = ComposingView(context)
            mComposingView.setPadding(DevicesUtils.dip2px(10), 0,DevicesUtils.dip2px(10),0)

            addView(mComposingView,  LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                addRule(ABOVE, mSkbRoot.id)
                addRule(ALIGN_LEFT, mSkbRoot.id)
            })
            val root = PopupComponent.get().root
            val viewParent = root.parent
            if (viewParent != null) {
                (viewParent as ViewGroup).removeView(root)
            }
            addView(root, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                addRule(ALIGN_BOTTOM, mSkbRoot.id)
                addRule(ALIGN_LEFT, mSkbRoot.id)
            })
        }
        if(isAddPhrases){
            mAddPhrasesLayout.visibility = View.VISIBLE
            handleAddPhrasesView()
        } else {
            mAddPhrasesLayout.visibility = View.GONE
        }
        mSkbCandidatesBarView.initialize(mChoiceNotifier)
        val oneHandedModSwitch = getInstance().keyboardSetting.oneHandedModSwitch.getValue()
        val oneHandedMod = getInstance().keyboardSetting.oneHandedMod.getValue()
        if(::mOnehandHoderLayout.isInitialized)mOnehandHoderLayout.visibility = GONE
        if (oneHandedModSwitch) {
            mOnehandHoderLayout = when(oneHandedMod){
                KeyboardOneHandedMod.LEFT ->  mHoderLayoutRight
                else -> mHoderLayoutLeft
            }
            mOnehandHoderLayout.visibility = VISIBLE
            mOnehandHoderLayout[0].setOnClickListener { view: View -> onClick(view) }
            mOnehandHoderLayout[1].setOnClickListener { view: View -> onClick(view) }
            (mOnehandHoderLayout[1] as ImageButton).setImageResource(if (oneHandedMod == KeyboardOneHandedMod.LEFT) R.drawable.ic_menu_one_hand_right else R.drawable.ic_menu_one_hand)
            val layoutParamsHoder = mOnehandHoderLayout.layoutParams
            layoutParamsHoder.width = EnvironmentSingleton.instance.holderWidth
            layoutParamsHoder.height = EnvironmentSingleton.instance.skbHeight
        }
        mLlKeyboardBottomHolder.removeAllViews()
        mLlKeyboardBottomHolder.layoutParams.width = EnvironmentSingleton.instance.skbWidth
        if(EnvironmentSingleton.instance.keyboardModeFloat){
            mBottomPaddingKey = (if(EnvironmentSingleton.instance.isLandscape) getInstance().internal.keyboardBottomPaddingLandscapeFloat
                else getInstance().internal.keyboardBottomPaddingFloat)
            mRightPaddingKey = (if(EnvironmentSingleton.instance.isLandscape) getInstance().internal.keyboardRightPaddingLandscapeFloat
            else getInstance().internal.keyboardRightPaddingFloat)
            bottomPadding = mBottomPaddingKey.getValue()
            rightPadding = mRightPaddingKey.getValue()
            mSkbRoot.bottomPadding = 0
            mSkbRoot.rightPadding = 0
            mLlKeyboardBottomHolder.minimumHeight = EnvironmentSingleton.instance.heightForKeyboardMove
            val mIvKeyboardMove = ImageView(context).apply {
                setImageResource(R.drawable.ic_horizontal_line)
                isClickable = true
                isEnabled = true
            }
            mLlKeyboardBottomHolder.addView(mIvKeyboardMove)
            mIvKeyboardMove.setOnTouchListener { _, event -> onMoveKeyboardEvent(event) }
        } else {
            val fullDisplayKeyboardEnable = getInstance().internal.fullDisplayKeyboardEnable.getValue()
            if(fullDisplayKeyboardEnable){
                mFullDisplayKeyboardBar = FullDisplayKeyboardBar(context, this)
                mLlKeyboardBottomHolder.addView(mFullDisplayKeyboardBar)
                mLlKeyboardBottomHolder.minimumHeight = EnvironmentSingleton.instance.heightForFullDisplayBar + EnvironmentSingleton.instance.systemNavbarWindowsBottom
            } else {
                mLlKeyboardBottomHolder.minimumHeight = EnvironmentSingleton.instance.systemNavbarWindowsBottom
            }
            bottomPadding = 0
            rightPadding = 0
            mBottomPaddingKey =  getInstance().internal.keyboardBottomPadding
            mRightPaddingKey =  getInstance().internal.keyboardRightPadding
            mSkbRoot.bottomPadding = mBottomPaddingKey.getValue()
            mSkbRoot.rightPadding = mRightPaddingKey.getValue()
        }
        updateTheme()
        DecodingInfo.candidatesLiveData.observe( this){ _ ->
            updateCandidateBar()
            (KeyboardManager.instance.currentContainer as? CandidatesContainer)?.showCandidatesView()
        }
    }

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var rightPaddingValue = 0  // 右侧边距
    private var bottomPaddingValue = 0  // 底部边距
    private fun onMoveKeyboardEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                bottomPaddingValue = mBottomPaddingKey.getValue()
                rightPaddingValue = mRightPaddingKey.getValue()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx: Float = event.rawX - initialTouchX
                val dy: Float = event.rawY - initialTouchY
                if(dx.absoluteValue > 10) {
                    rightPaddingValue -= dx.toInt()
                    rightPaddingValue = if(rightPaddingValue < 0) 0
                    else if(rightPaddingValue > EnvironmentSingleton.instance.mScreenWidth - mSkbRoot.width) {
                        EnvironmentSingleton.instance.mScreenWidth - mSkbRoot.width
                    } else rightPaddingValue
                    initialTouchX = event.rawX
                    if(EnvironmentSingleton.instance.keyboardModeFloat) {
                        rightPadding = rightPaddingValue
                    } else {
                        mSkbRoot.rightPadding = rightPaddingValue
                    }
                }
                if(dy.absoluteValue > 10 ) {
                    bottomPaddingValue -= dy.toInt()
                    bottomPaddingValue = if(bottomPaddingValue < 0) 0
                    else if(bottomPaddingValue > EnvironmentSingleton.instance.mScreenHeight - mSkbRoot.height) {
                        EnvironmentSingleton.instance.mScreenHeight - mSkbRoot.height
                    } else bottomPaddingValue
                    initialTouchY = event.rawY
                    if(EnvironmentSingleton.instance.keyboardModeFloat) {
                        bottomPadding = bottomPaddingValue
                    } else {
                        mSkbRoot.bottomPadding = bottomPaddingValue
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mRightPaddingKey.setValue(rightPaddingValue)
                mBottomPaddingKey.setValue(bottomPaddingValue)
            }
        }
        return false
    }

    // 刷新主题
    fun updateTheme() {
        setBackgroundResource(android.R.color.transparent)
        val keyTextColor = ThemeManager.activeTheme.keyTextColor
        val backgrounde = ThemeManager.activeTheme.backgroundDrawable(ThemeManager.prefs.keyBorder.getValue())
        mSkbRoot.background = if(backgrounde is BitmapDrawable) BitmapDrawable(context.resources, Bitmap.createScaledBitmap(backgrounde.bitmap, EnvironmentSingleton.instance.skbWidth, EnvironmentSingleton.instance.inputAreaHeight, true)) else backgrounde
        mComposingView.updateTheme(ThemeManager.activeTheme)
        mSkbCandidatesBarView.updateTheme(keyTextColor)
        if(::mOnehandHoderLayout.isInitialized) {
            (mOnehandHoderLayout[0] as ImageButton).drawable?.setTint(keyTextColor)
            (mOnehandHoderLayout[1] as ImageButton).drawable?.setTint(keyTextColor)
        }
        mFullDisplayKeyboardBar?.updateTheme(keyTextColor)
        mAddPhrasesLayout.setBackgroundColor(ThemeManager.activeTheme.barColor)
        mEtAddPhrasesContent?.background = GradientDrawable().apply {
            setColor(ThemeManager.activeTheme.keyBackgroundColor)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ThemeManager.prefs.keyRadius.getValue().toFloat()
        }
        mEtAddPhrasesContent?.setTextColor(keyTextColor)
        mEtAddPhrasesContent?.setHintTextColor(keyTextColor)
        tvAddPhrasesTips?.setTextColor(keyTextColor)
    }

    private fun onClick(view: View) {
        if (view.id == R.id.ib_holder_one_hand_none) {
            getInstance().keyboardSetting.oneHandedModSwitch.setValue(!getInstance().keyboardSetting.oneHandedModSwitch.getValue())
        } else {
            val oneHandedMod = getInstance().keyboardSetting.oneHandedMod.getValue()
            getInstance().keyboardSetting.oneHandedMod.setValue(if (oneHandedMod == KeyboardOneHandedMod.LEFT) KeyboardOneHandedMod.RIGHT else KeyboardOneHandedMod.LEFT)
        }
        EnvironmentSingleton.instance.initData()
        KeyboardLoaderUtil.instance.clearKeyboardMap()
        KeyboardManager.instance.clearKeyboard()
        KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
    }

    /**
     * 响应软键盘按键的处理函数。在软键盘集装箱SkbContainer中responseKeyEvent（）的调用。
     * 软键盘集装箱SkbContainer的responseKeyEvent（）在自身类中调用。
     */
    override fun responseKeyEvent(sKey: SoftKey) {
        val keyCode = sKey.keyCode
        if (sKey.isKeyCodeKey) {  // 系统的keycode,单独处理
            mImeState = ImeState.STATE_INPUT
            val keyEvent = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD)
            processKey(keyEvent)
        } else if (sKey.isUserDefKey || sKey.isUniStrKey) { // 是用户定义的keycode
            if (!DecodingInfo.isAssociate && !DecodingInfo.isCandidatesListEmpty) {
                if(InputModeSwitcherManager.isChinese)   chooseAndUpdate()
                else if(InputModeSwitcherManager.isEnglish)  commitDecInfoText(DecodingInfo.composingStrForCommit)  // 把输入的拼音字符串发送给EditText
            }
            if (InputModeSwitcherManager.USER_DEF_KEYCODE_SYMBOL_3 == keyCode) {  // 点击标点按钮
                KeyboardManager.instance.switchKeyboard(KeyboardManager.KeyboardType.SYMBOL)
                (KeyboardManager.instance.currentContainer as? SymbolContainer)?.setSymbolsView()
            } else  if (InputModeSwitcherManager.USER_DEF_KEYCODE_EMOJI_4 == keyCode) {  // 点击表情按钮
                onSettingsMenuClick(SkbMenuMode.Emojicon)
            } else if ( keyCode in InputModeSwitcherManager.USER_DEF_KEYCODE_RETURN_6 .. InputModeSwitcherManager.USER_DEF_KEYCODE_SHIFT_1) {
                InputModeSwitcherManager.switchModeForUserKey(keyCode)
            }else if(sKey.keyLabel.isNotBlank()){
                if(SymbolPreset.containsKey(sKey.keyLabel))commitPairSymbol(sKey.keyLabel)
                else commitText(sKey.keyLabel)
            }
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
        }
    }


    private var textBeforeCursor:String = ""

    /**
     * 响应软键盘长按键的处理函数。在软键盘集装箱SkbContainer中responseKeyEvent（）的调用。
     * 软键盘集装箱SkbContainer的responseKeyEvent（）在自身类中调用。
     */
    override fun responseLongKeyEvent(result:Pair<PopupMenuMode, String>) {
        if (!DecodingInfo.isAssociate && !DecodingInfo.isCandidatesListEmpty) {
            if(InputModeSwitcherManager.isChinese) {
                chooseAndUpdate()
            } else if(InputModeSwitcherManager.isEnglish){
                val displayStr = DecodingInfo.composingStrForCommit // 把输入的拼音字符串发送给EditText
                commitDecInfoText(displayStr)
            }
        }
        when(result.first){
            PopupMenuMode.Text -> {
                if(SymbolPreset.containsKey(result.second))commitPairSymbol(result.second)
                else commitText(result.second)
            }
            PopupMenuMode.SwitchIME -> InputMethodUtil.showPicker()
            PopupMenuMode.EnglishCell -> {
                getInstance().input.abcSearchEnglishCell.setValue(!getInstance().input.abcSearchEnglishCell.getValue())
                KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
            }
            PopupMenuMode.Clear -> {
                if(isAddPhrases) mEtAddPhrasesContent?.setText("")
                else {
                    val inputConnection = service.getCurrentInputConnection()
                    val clearText = inputConnection.getTextBeforeCursor(1000, InputConnection.GET_TEXT_WITH_STYLES).toString()
                    if(clearText.isNotEmpty()){
                        textBeforeCursor = clearText
                        inputConnection.deleteSurroundingText(1000, 0)
                    }
                }
            }
            PopupMenuMode.Revertl -> {
                commitText(textBeforeCursor)
                textBeforeCursor = ""
            }
            PopupMenuMode.Enter ->  commitText("\n") // 长按回车键
            else -> {}
        }
        if(result.first == PopupMenuMode.Text && mImeState != ImeState.STATE_PREDICT) resetToPredictState()
        else if(result.first != PopupMenuMode.None && mImeState != ImeState.STATE_IDLE) resetToIdleState()
    }

    override fun responseHandwritingResultEvent(words: Array<CandidateListItem>) {
        DecodingInfo.isAssociate = false
        DecodingInfo.cacheCandidates(words)
        mImeState = ImeState.STATE_INPUT
        updateCandidateBar()
    }

    /**
     * 按键处理函数
     */
    fun processKey(event: KeyEvent): Boolean {
        // 功能键处理
        if (processFunctionKeys(event)) return true
        val englishCellDisable = InputModeSwitcherManager.isEnglish && !getInstance().input.abcSearchEnglishCell.getValue()
        val result = if(englishCellDisable){
            processEnglishKey(event)
        } else if (!InputModeSwitcherManager.mInputTypePassword &&(InputModeSwitcherManager.isEnglish || InputModeSwitcherManager.isChinese)) { // 中文、英语输入模式
            processInput(event)
        } else { // 数字、符号处理
            processEnglishKey(event)
        }
        return result
    }

    /**
     * 英文非智能输入处理函数
     */
    private fun processEnglishKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        var keyChar = event.unicodeChar
        val lable = keyChar.toChar().toString()
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            sendKeyEvent(keyCode)
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if(keyCode in (KeyEvent.KEYCODE_A .. KeyEvent.KEYCODE_Z) ){
            if (!InputModeSwitcherManager.isEnglishLower) keyChar = keyChar - 'a'.code + 'A'.code
            commitText(keyChar.toChar().toString())
            return true
        } else if (keyCode != 0) {
            sendKeyEvent(keyCode)
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if (lable.isNotEmpty()) {
            if(SymbolPreset.containsKey(lable))commitPairSymbol(lable)
            else commitText(lable)
            return true
        }
        return false
    }

    /**
     * 功能键处理函数
     */
    private fun processFunctionKeys(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (service.isInputViewShown) {
                requestHideSelf()
                return true
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (DecodingInfo.isFinish || (DecodingInfo.isAssociate && !mSkbCandidatesBarView.isActiveCand())) {
                sendKeyEvent(keyCode)
                if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            } else {
                chooseAndUpdate()
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_CLEAR) {
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        }  else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (DecodingInfo.isFinish || DecodingInfo.isAssociate) {
                sendKeyEvent(keyCode)
            } else {
                commitDecInfoText(DecodingInfo.composingStrForCommit)
            }
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if(event.flags != KeyEvent.FLAG_SOFT_KEYBOARD && !DecodingInfo.isCandidatesListEmpty) {
                mSkbCandidatesBarView.updateActiveCandidateNo(keyCode)
            } else if (DecodingInfo.isFinish || DecodingInfo.isAssociate) {
                moveCursorPosition(keyCode)
            } else {
                chooseAndUpdate()
            }
            return  true
        }else if (keyCode == KeyEvent.KEYCODE_DEL && (InputModeSwitcherManager.mInputTypePassword || InputModeSwitcherManager.isNumberSkb)) {
            sendKeyEvent(keyCode)
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        }
        return false
    }

    /**
     * 按键处理函数
     */
    private fun processInput(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val keyChar = event.unicodeChar
        val lable = keyChar.toChar().toString()
        if (keyChar in 'A'.code .. 'Z'.code || keyChar in 'a'.code .. 'z'.code || keyChar in  '0'.code .. '9'.code|| keyCode == KeyEvent.KEYCODE_APOSTROPHE || keyCode == KeyEvent.KEYCODE_SEMICOLON){
            DecodingInfo.inputAction(keyCode)
            updateCandidate()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (DecodingInfo.isFinish || DecodingInfo.isAssociate) {
                sendKeyEvent(keyCode)
                if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            } else {
                DecodingInfo.deleteAction()
                updateCandidate()
            }
            return true
        } else if (keyCode != 0) {
            if (!DecodingInfo.isCandidatesListEmpty && !DecodingInfo.isAssociate) {
                chooseAndUpdate()
            }
            sendKeyEvent(keyCode)
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if(lable.isNotEmpty()) {
            if (!DecodingInfo.isCandidatesListEmpty && !DecodingInfo.isAssociate) {
                chooseAndUpdate()
            }
            if(SymbolPreset.containsKey(lable))commitPairSymbol(lable)
            else commitText(lable)
            return true
        }
        return false
    }

    /**
     * 重置到空闲状态
     */
    fun resetToIdleState() {
        resetCandidateWindow()
        mComposingView.setDecodingInfo()
        mImeState = ImeState.STATE_IDLE
    }

    /**
     * 切换到联想状态
     */
    private fun resetToPredictState() {
        resetCandidateWindow()
        mComposingView.setDecodingInfo()
        mImeState = ImeState.STATE_PREDICT
    }

    /**
     * 选择候选词，并根据条件是否进行下一步的预报。
     * @param candId 选择索引
     */
    fun chooseAndUpdate(candId: Int = mSkbCandidatesBarView.getActiveCandNo()) {
        val candidate = DecodingInfo.getCandidate(candId)
        if(candidate?.comment == "📋"){  // 处理剪贴板或常用语
            commitDecInfoText(candidate.text)
            if(mImeState != ImeState.STATE_PREDICT)resetToPredictState()
        } else {
            val choice = DecodingInfo.chooseDecodingCandidate(candId)
            if (DecodingInfo.isEngineFinish || DecodingInfo.isAssociate) {  // 选择的候选词上屏
                commitDecInfoText(choice)
                KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
                if(mImeState != ImeState.STATE_PREDICT)resetToPredictState()
            } else {  // 不上屏，继续选择
                if (!DecodingInfo.isFinish) {
                    if (InputModeSwitcherManager.isEnglish) setComposingText(DecodingInfo.composingStrForDisplay)
                    updateCandidateBar()
                    (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
                } else {
                    if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
                }
            }
        }
    }

    /**
     * 刷新候选词，重新从词库进行获取。
     */
    private fun updateCandidate() {
        DecodingInfo.updateDecodingCandidate()
        if (!DecodingInfo.isFinish) {
            updateCandidateBar()
            (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
        } else {
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
        }
        if (InputModeSwitcherManager.isEnglish)setComposingText(DecodingInfo.composingStrForDisplay)
    }

    /**
     * 显示候选词视图
     */
    fun updateCandidateBar() {
        mSkbCandidatesBarView.showCandidates()
        mComposingView.setDecodingInfo()
    }

    /**
     * 重置候选词区域
     */
    private fun resetCandidateWindow() {
        DecodingInfo.reset()
        updateCandidateBar()
        (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
    }

    /**
     * 当用户选择了候选词或者在候选词视图滑动了手势时的通知输入法。实现了候选词视图的监听器CandidateViewListener，
     * 有选择候选词的处理函数、隐藏键盘的事件
     */
    inner class ChoiceNotifier internal constructor() : CandidateViewListener {
        override fun onClickChoice(choiceId: Int) {
            DevicesUtils.tryPlayKeyDown()
            DevicesUtils.tryVibrate(KeyboardManager.instance.currentContainer)
            chooseAndUpdate(choiceId)
        }

        override fun onClickMore(level: Int) {
            if (level == 0) {
                onSettingsMenuClick(SkbMenuMode.CandidatesMore)
            } else {
                KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
            }
        }

        override fun onClickMenu(skbMenuMode: SkbMenuMode) {
            onSettingsMenuClick(skbMenuMode)
        }

        override fun onClickClearCandidate() {
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
        }

        override fun onClickClearClipBoard() {
            DataBaseKT.instance.clipboardDao().deleteAll()
            (KeyboardManager.instance.currentContainer as? ClipBoardContainer)?.showClipBoardView(SkbMenuMode.ClipBoard)
        }
    }

    fun onSettingsMenuClick(skbMenuMode: SkbMenuMode, extra:String = "") {
        when (skbMenuMode) {
            SkbMenuMode.AddPhrases -> {
                isAddPhrases = true
                isAddAIQuery = false
                DataBaseKT.instance.phraseDao().deleteByContent(extra)
                KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                initView(context)
                mEtAddPhrasesContent?.setText(extra)
                mEtAddPhrasesContent?.setSelection(extra.length)
            }
            SkbMenuMode.AddAIQuery -> {
                isAddPhrases = true
                isAddAIQuery = true
                DataBaseKT.instance.phraseDao().deleteByContent(extra)
                KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                initView(context)
                mEtAddPhrasesContent?.setText(extra)
                mEtAddPhrasesContent?.setSelection(extra.length)
            }
            else ->onSettingsMenuClick(this, skbMenuMode)
        }
        mSkbCandidatesBarView.initMenuView()
    }

    private fun handleAddPhrasesView() {
        mEtAddPhrasesContent =  mAddPhrasesLayout.findViewById(R.id.et_add_phrases_content)
        mEtAddPhrasesContent?.requestFocus()

        mSearchButton = mAddPhrasesLayout.findViewById(R.id.btn_ai_query_search)
        if(isAddAIQuery){
            mSearchButton?.visibility = View.VISIBLE
            mSearchButton?.setOnClickListener {

                addAIQueryHandle()
            }
        }else
        {
            mSearchButton?.visibility = View.GONE
        }

        tvAddPhrasesTips =  mAddPhrasesLayout.findViewById(R.id.tv_add_phrases_tips)
        val tips = "快捷输入为拼音首字母前4位:"
        mEtAddPhrasesContent?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                tvAddPhrasesTips?.text = tips.plus(com.yuyan.imemodule.libs.pinyin4j.PinyinHelper.getPinYinHeadChar(editable.toString()))
            }
        })
    }

    private fun addPhrasesHandle() {
        val content = mEtAddPhrasesContent?.text.toString()

        if(content.isNotBlank()) {
            val pinYinHeadChar = com.yuyan.imemodule.libs.pinyin4j.PinyinHelper.getPinYinHeadChar(content)
            val pinYinHeadT9 = pinYinHeadChar.map { T9PinYinUtils.pinyin2T9Key(it)}.joinToString("")
            val phrase =  Phrase(content = content, t9 = pinYinHeadT9, qwerty = pinYinHeadChar, lx17 = "")
            DataBaseKT.instance.phraseDao().insert(phrase)
            KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
        }
    }

    private fun addAIQueryHandle() {
        val content = mEtAddPhrasesContent?.text.toString()
        val mainHandler = Handler(Looper.getMainLooper())

        if (content.isNotBlank()) {
            // 创建加载中的 PopupWindow
            val loadingView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 30, 50, 30)
                background = GradientDrawable().apply {
                    setColor(ThemeManager.activeTheme.keyBackgroundColor)
                    cornerRadius = 16f
                    setStroke(2, ThemeManager.activeTheme.keyTextColor) // 添加边框
                    elevation = 10f // 添加阴影
                }
                
                // 添加加载动画
                addView(ProgressBar(context).apply {
                    indeterminateTintList = ColorStateList.valueOf(ThemeManager.activeTheme.keyTextColor)
                    layoutParams = LinearLayout.LayoutParams(
                        DevicesUtils.dip2px(100),
                        DevicesUtils.dip2px(100)
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                })

                addView(TextView(context).apply {
                    text = "正在思考中..."
                    setTextColor(ThemeManager.activeTheme.keyTextColor)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, 20, 0, 0)
                })
            }

            val loadingPopup = PopupWindow(
                loadingView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            ).apply {
                isOutsideTouchable = false
                isFocusable = false
                elevation = 10f // 添加阴影
                setBackgroundDrawable(null)
            }

            // 显示在输入框上方
            loadingPopup.showAtLocation(mEtAddPhrasesContent, Gravity.CENTER, 0, 0)

            // 检查用户登录状态
            val user = UserManager.getCurrentUser() ?: run {
                loadingPopup.dismiss()
                Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                return
            }

            // 准备请求体
            val jsonBody = JSONObject().apply {
                put("question", content)
            }

            // 创建 HTTP 客户端
            val client = OkHttpClient.Builder()
                .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // 创建请求
            val request = Request.Builder()
                .url("https://www.qingmiao.cloud/userapi/knowledge/chatNew")
                .addHeader("Authorization", user.token)
                .addHeader("openid", user.username)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // 发送请求
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mainHandler.post {
                        loadingPopup.dismiss()
                        Toast.makeText(context, "请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    mainHandler.post {
                        loadingPopup.dismiss()
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            if (jsonResponse.has("text")) {
                                val answer = jsonResponse.getString("text")

                                // 创建结果视图
                                val resultView = LinearLayout(context).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(30, 20, 30, 20)
                                    background = GradientDrawable().apply {
                                        setColor(ThemeManager.activeTheme.keyBackgroundColor)
                                        cornerRadius = 16f
                                        setStroke(2, ThemeManager.activeTheme.keyTextColor) // 添加边框
                                        elevation = 10f // 添加阴影
                                    }


                                    // 标题
                                    addView(TextView(context).apply {
                                        text = "AI回答"
                                        setTextColor(ThemeManager.activeTheme.keyTextColor)
                                        textSize = 18f
                                        gravity = Gravity.CENTER
                                        setPadding(0, 0, 0, 20)
                                    })
                                    
                                    // 滚动视图和编辑框
                                    addView(ScrollView(context).apply {
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            DevicesUtils.dip2px(200) // 限制最大高度为 200dp
                                        )
                                        addView(TextView(context).apply {
                                            text = answer
                                            setTextColor(ThemeManager.activeTheme.keyTextColor)
                                            gravity = Gravity.TOP or Gravity.START
                                            textSize = 14f
                                            background = null
                                            setPadding(20, 20, 20, 20)
                                            maxLines = 500 // 设置最大行数，避免内存占用过大
                                        })
                                    })
                                }

                                // 创建 PopupWindow 实例
                                val resultPopup = PopupWindow(
                                    resultView,
                                    WindowManager.LayoutParams.WRAP_CONTENT,
                                    WindowManager.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    isOutsideTouchable = true
                                    isFocusable = true
                                    setBackgroundDrawable(null)
                                }

                                // 添加按钮容器
                                resultView.addView(LinearLayout(context).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.END
                                    setPadding(0, 20, 0, 0)
                                    
                                    // 取消按钮
                                    addView(Button(context).apply {
                                        text = "取消"
                                        setTextColor(ThemeManager.activeTheme.keyTextColor)
                                        setOnClickListener {
                                            resultPopup.dismiss()
                                            isAddPhrases = false
                                            isAddAIQuery = false
                                            initView(context)
                                            onSettingsMenuClick(SkbMenuMode.ClipBoard)
                                        }
                                    })
                                    
                                    // 复制按钮
                                    addView(Button(context).apply {
                                        text = "拷贝结果"
                                        setTextColor(ThemeManager.activeTheme.keyTextColor)
                                        setOnClickListener {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("AI回答", answer)
                                            clipboard.setPrimaryClip(clip)
//                                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                            resultPopup.dismiss()
                                            isAddPhrases = false
                                            isAddAIQuery = false
                                            initView(context)
                                            onSettingsMenuClick(SkbMenuMode.ClipBoard)
                                        }
                                    })
                                })

                                // 显示 PopupWindow
                                resultPopup.showAtLocation(mEtAddPhrasesContent, Gravity.CENTER, 0, 0)
                            } else {
                                Toast.makeText(context, "响应格式错误", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "解析响应失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    /**
     * 输入法状态: 空闲，输入，联想
     */
    enum class ImeState {
        STATE_IDLE, STATE_INPUT, STATE_PREDICT
    }

    /**
     * 选择拼音
     */
    fun selectPrefix(position: Int) {
        // 播放按键声音和震动
        DevicesUtils.tryPlayKeyDown()
        DevicesUtils.tryVibrate(this)
        DecodingInfo.selectPrefix(position)
        updateCandidate()
    }

    //常用符号、剪切板
    fun showSymbols(symbols: Array<String>) {
        mImeState = ImeState.STATE_INPUT
        val list = symbols.map { symbol-> CandidateListItem("📋", symbol) }.toTypedArray()
        DecodingInfo.cacheCandidates(list)
        DecodingInfo.isAssociate = true
        updateCandidateBar()
    }

    fun requestHideSelf() {
        service.requestHideSelf(0)
    }

    /**
     * 模拟按键点击
     */
    private fun sendKeyEvent(keyCode: Int) {
        if(isAddPhrases){
            when(keyCode){
                KeyEvent.KEYCODE_DEL ->{
                    mEtAddPhrasesContent?.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    mEtAddPhrasesContent?.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
                KeyEvent.KEYCODE_ENTER ->{
                    println("isAddPhrases:$isAddPhrases")
                    println("isAddAIQuery:$isAddAIQuery")
                    isAddPhrases = false
                    if(!isAddAIQuery){
                        addPhrasesHandle()
                    }
                    isAddAIQuery = false

                    initView(context)
                    onSettingsMenuClick(SkbMenuMode.Phrases)
                }
                else -> {
                    val unicodeChar: Char = KeyEvent(KeyEvent.ACTION_DOWN, keyCode).unicodeChar.toChar()
                    if (unicodeChar != Character.MIN_VALUE) {
                        mEtAddPhrasesContent?.commitText(unicodeChar.toString())
                    }
                }
            }
        } else {
            if (keyCode != KeyEvent.KEYCODE_ENTER) {
                service.sendDownUpKeyEvents(keyCode)
            } else {
                val inputConnection = service.getCurrentInputConnection()
                YuyanEmojiCompat.mEditorInfo?.run {
                    if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL || imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
                        service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    } else if (!actionLabel.isNullOrEmpty() && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                        inputConnection.performEditorAction(actionId)
                    } else when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
                        EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE -> service.sendDownUpKeyEvents(keyCode)
                        else -> inputConnection.performEditorAction(action)
                    }
                }
            }
        }
    }

    /**
     * 向输入框提交预选词
     */
    private fun setComposingText(text: CharSequence) {
        if(!isAddPhrases)service.getCurrentInputConnection()?.setComposingText(text, 1)
    }

    /**
     * 发送字符串给编辑框
     */
    private fun commitText(text: String) {
        if(isAddPhrases) mEtAddPhrasesContent?.commitText(text)
        else service.getCurrentInputConnection()?.commitText(StringUtils.converted2FlowerTypeface(text), 1)
    }

    /**
     * 发送成对符号给编辑框
     */
    private fun commitPairSymbol(text: String) {
        if(isAddPhrases) {
            mEtAddPhrasesContent?.commitText(text)
        } else {
            val ic = service.getCurrentInputConnection()
            if(getInstance().input.symbolPairInput.getValue()) {
                ic?.commitText(text + SymbolPreset[text]!!, 1)
                moveCursorPosition(KeyEvent.KEYCODE_DPAD_LEFT)
            } else ic?.commitText(text, 1)
        }
    }

    /**
     * 发送候选词字符串给编辑框
     */
    private fun commitDecInfoText(resultText: String?) {
        if(resultText == null) return
        if(isAddPhrases){
            mEtAddPhrasesContent?.commitText(resultText)
        } else {
            val inputConnection = service.getCurrentInputConnection()
            inputConnection.commitText(StringUtils.converted2FlowerTypeface(resultText), 1)
            if (InputModeSwitcherManager.isEnglish && DecodingInfo.isEngineFinish && getInstance().input.abcSpaceAuto.getValue() && StringUtils.isEnglishWord(resultText)) {
                inputConnection.commitText(" ", 1)
            }
        }
    }

    /**
     * 移动光标
     */
    private fun moveCursorPosition(keyCode:Int) {
        val inputConnection = service.getCurrentInputConnection()
        inputConnection.beginBatchEdit()
        val eventTime = SystemClock.uptimeMillis()
        inputConnection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD))
        inputConnection.sendKeyEvent(KeyEvent(eventTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD))
        inputConnection.endBatchEdit()
    }

    private fun initNavbarBackground(service: ImeService) {
        service.window.window!!.also {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            it.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.isNavigationBarContrastEnforced = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            EnvironmentSingleton.instance.systemNavbarWindowsBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val fullDisplayKeyboardEnable = getInstance().internal.fullDisplayKeyboardEnable.getValue()
            mLlKeyboardBottomHolder.minimumHeight = if(EnvironmentSingleton.instance.keyboardModeFloat)  0
            else if(fullDisplayKeyboardEnable) EnvironmentSingleton.instance.heightForFullDisplayBar + EnvironmentSingleton.instance.systemNavbarWindowsBottom
            else  EnvironmentSingleton.instance.systemNavbarWindowsBottom
            insets
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        InputModeSwitcherManager.requestInputWithSkb(editorInfo)
        KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
        if(!restarting) {
            YuyanEmojiCompat.setEditorInfo(editorInfo)
            if (getInstance().clipboard.clipboardSuggestion.getValue()) {
                val lastClipboardTime = getInstance().internal.clipboardUpdateTime.getValue()
                if (System.currentTimeMillis() - lastClipboardTime <= clipboardItemTimeout * 1000) {
                    val lastClipboardContent = getInstance().internal.clipboardUpdateContent.getValue()
                    if (lastClipboardContent.isNotBlank()) {
                        showSymbols(arrayOf(lastClipboardContent))
                        getInstance().internal.clipboardUpdateTime.setValue(0L)
                    }
                }
            }
        }
    }

    fun onWindowShown() {
        chinesePrediction = getInstance().input.chinesePrediction.getValue()
    }

    fun onWindowHidden() {
        if(isAddPhrases){
            isAddPhrases = false
            if(!isAddAIQuery){
                addPhrasesHandle()
            }
            isAddAIQuery = false
            initView(context)
        }
        if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
    }

    fun onUpdateSelection() {
        if(chinesePrediction) {
            val inputConnection = service.getCurrentInputConnection()
            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0).toString()
            if (textBeforeCursor.isNotBlank()) {
                val expressionEnd = CustomEngine.parseExpressionAtEnd(textBeforeCursor)
                if(!expressionEnd.isNullOrBlank()) {
                    if(expressionEnd.length < 100) {
                        val result = CustomEngine.expressionCalculator(textBeforeCursor, expressionEnd)
                        if (result.isNotEmpty()) showSymbols(result)
                    }
                } else if (StringUtils.isChineseEnd(textBeforeCursor)) {
                    DecodingInfo.isAssociate = true
                    DecodingInfo.getAssociateWord(if (textBeforeCursor.length > 10)textBeforeCursor.substring(textBeforeCursor.length - 10) else textBeforeCursor)
                    updateCandidate()
                    updateCandidateBar()
                }
            }
        }
    }
}