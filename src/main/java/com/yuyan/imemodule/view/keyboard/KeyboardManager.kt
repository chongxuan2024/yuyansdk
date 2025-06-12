package com.yuyan.imemodule.view.keyboard

import android.content.Context
import android.view.ViewGroup
import com.yuyan.imemodule.application.ImeSdkApplication
import com.yuyan.imemodule.manager.InputModeSwitcherManager
import com.yuyan.imemodule.view.keyboard.container.*

/**
 * 键盘显示管理类
 */
class KeyboardManager {
    enum class KeyboardType {
        T9, QWERTY, LX17, QWERTYABC, NUMBER, SYMBOL, SETTINGS, HANDWRITING, CANDIDATES, ClipBoard
    }
    private lateinit var mInputView: InputView
    private lateinit var mKeyboardRootView: InputViewParent
    private val keyboards = HashMap<KeyboardType, BaseContainer?>()
    private lateinit var mCurrentKeyboardName: KeyboardType
    var currentContainer: BaseContainer? = null
        private set

    fun setData(keyboardRootView: InputViewParent, inputView: InputView) {
        keyboards.clear() // TODO 清空缓存界面，发现调用 PinyinService.onCreateInputView时，原输入界面全部会失效。
        mKeyboardRootView = keyboardRootView
        mInputView = inputView
    }

    fun clearKeyboard() {
        keyboards.clear()
        if (::mInputView.isInitialized) mInputView.initView(mInputView.context)
    }

    fun switchKeyboard(layout: Int) {
        val keyboardName = when (layout) {
            InputModeSwitcherManager.MASK_SKB_LAYOUT_QWERTY_PINYIN -> KeyboardType.QWERTY
            InputModeSwitcherManager.MASK_SKB_LAYOUT_QWERTY_ABC -> KeyboardType.QWERTYABC
            InputModeSwitcherManager.MASK_SKB_LAYOUT_HANDWRITING -> KeyboardType.HANDWRITING
            InputModeSwitcherManager.MASK_SKB_LAYOUT_NUMBER -> KeyboardType.NUMBER
            InputModeSwitcherManager.MASK_SKB_LAYOUT_LX17 -> KeyboardType.LX17
            0x7000 -> KeyboardType.ClipBoard // 使用固定值替代clipboardLayout
            else -> KeyboardType.T9
        }
        switchKeyboard(keyboardName)
        mInputView.updateCandidateBar()
    }

    fun switchKeyboard(keyboardName: KeyboardType) {
        if (!::mKeyboardRootView.isInitialized) return
        var container = keyboards[keyboardName]
        if (container == null) {
            container = when (keyboardName) {
                KeyboardType.CANDIDATES ->  CandidatesContainer(ImeSdkApplication.context, mInputView)
                KeyboardType.HANDWRITING -> HandwritingContainer(ImeSdkApplication.context, mInputView)
                KeyboardType.NUMBER -> NumberContainer(ImeSdkApplication.context, mInputView)
                KeyboardType.QWERTY -> QwertyContainer(ImeSdkApplication.context, mInputView, InputModeSwitcherManager.MASK_SKB_LAYOUT_QWERTY_PINYIN)
                KeyboardType.SETTINGS -> SettingsContainer(ImeSdkApplication.context, mInputView)
                KeyboardType.SYMBOL -> SymbolContainer(ImeSdkApplication.context, mInputView)
                KeyboardType.QWERTYABC -> QwertyContainer(ImeSdkApplication.context, mInputView, InputModeSwitcherManager.MASK_SKB_LAYOUT_QWERTY_ABC)
                KeyboardType.LX17 -> QwertyContainer(ImeSdkApplication.context, mInputView, InputModeSwitcherManager.MASK_SKB_LAYOUT_LX17)
                KeyboardType.ClipBoard -> ClipBoardContainer(ImeSdkApplication.context, mInputView) as BaseContainer
                else ->  T9TextContainer(ImeSdkApplication.context, mInputView)
            }
            container.updateSkbLayout()
            keyboards[keyboardName] = container
        }
        mKeyboardRootView.showView(container)
        mCurrentKeyboardName = keyboardName
        currentContainer = container
    }

    val isInputKeyboard: Boolean
        get() = currentContainer is InputBaseContainer

    companion object {
        val instance: KeyboardManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            KeyboardManager()
        }
    }
}