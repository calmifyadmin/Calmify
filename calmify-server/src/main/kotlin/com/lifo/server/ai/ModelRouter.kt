package com.lifo.server.ai

/**
 * Selects the optimal Gemini model based on task type and user tier.
 * Premium users get Pro for chat, free users get Flash.
 * Insight/analysis always uses Flash with low temperature for structured output.
 * Crisis detection uses Pro for maximum accuracy.
 */
class ModelRouter {

    enum class TaskType {
        CHAT,
        CHAT_STREAM,
        INSIGHT,
        TEXT_ANALYSIS,
        CRISIS_DETECT,
    }

    enum class UserTier { FREE, PREMIUM }

    fun selectModel(taskType: TaskType, userTier: UserTier = UserTier.FREE): String {
        return when (taskType) {
            TaskType.CHAT -> when (userTier) {
                UserTier.PREMIUM -> "gemini-2.0-pro"
                UserTier.FREE -> "gemini-2.0-flash"
            }
            TaskType.CHAT_STREAM -> "gemini-2.0-flash"
            TaskType.INSIGHT -> "gemini-2.0-flash"
            TaskType.TEXT_ANALYSIS -> "gemini-2.0-flash"
            TaskType.CRISIS_DETECT -> "gemini-2.0-pro"
        }
    }
}
