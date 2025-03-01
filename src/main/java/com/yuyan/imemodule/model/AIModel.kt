data class AIModel(
    val id: String,
    val name: String,
    val description: String
)

// Mock数据
object AIModelManager {
    val availableModels = listOf(
        AIModel("gpt-3.5", "GPT-3.5", "通用AI助手"),
        AIModel("gpt-4", "GPT-4", "更强大的AI助手"),
        AIModel("claude", "Claude", "Anthropic的AI助手")
    )
} 