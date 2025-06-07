package com.yuyan.imemodule.ui.knowledge

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.view.WindowManager
import android.widget.Toast

class TextInputDialog(context: Context) : Dialog(context) {
    private var onTextSubmitted: ((String) -> Unit)? = null
    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建布局
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        // 创建输入框
        editText = EditText(context).apply {
            hint = "请输入文本内容"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }
        layout.addView(editText)

        // 创建按钮
        val button = Button(context).apply {
            text = "确定"
            setOnClickListener {
                val text = editText.text.toString()
                if (text.isBlank()) {
                    Toast.makeText(context, "请输入内容", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                onTextSubmitted?.invoke(text)
                dismiss()
            }
        }
        layout.addView(button)

        setContentView(layout)

        // 设置对话框宽度
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    fun setOnTextSubmitted(listener: (String) -> Unit) {
        onTextSubmitted = listener
    }
}