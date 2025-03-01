import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.yuyan.imemodule.R


class AIModelSelectionDialog : DialogFragment() {
    private var selectedModelId: String = ""
    private var onModelSelected: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val models = AIModelManager.availableModels
        val names = models.map { it.name }.toTypedArray()

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_model_selection)
            .setSingleChoiceItems(names, models.indexOfFirst { it.id == selectedModelId }) { dialog, which ->
                selectedModelId = models[which].id
                onModelSelected?.invoke(selectedModelId)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun newInstance(currentModelId: String, onSelected: (String) -> Unit): AIModelSelectionDialog {
            return AIModelSelectionDialog().apply {
                selectedModelId = currentModelId
                onModelSelected = onSelected
            }
        }
    }
} 