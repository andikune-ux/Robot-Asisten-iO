package dev.andikune.robotasisten.io.data.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

 data class RelxChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class RelxConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<RelxChatMessage>
)

data class RelxChatProfile(
    val persona: String,
    val memory: String,
    val instructions: String,
    val reasoningDepth: String = "balanced",
    val chatMode: RailxChatMode = RailxChatMode.SEDANG
)

data class RelxPendingChatRequest(
    val conversationId: String,
    val mode: String,
    val history: List<Pair<String, String>>,
    val userMessage: String,
    val pendingMessages: List<RelxChatMessage>,
    val persona: String,
    val memory: String,
    val instructions: String,
    val reasoningDepth: String,
    val chatMode: RailxChatMode = RailxChatMode.SEDANG
)

/** Local-only chat history and profile settings. Never included in project documentation export. */
class RelxChatStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("relx_ai_chat", Context.MODE_PRIVATE)

    fun profile(): RelxChatProfile {
        val storedModeName = prefs.getString(KEY_CHAT_MODE, RailxChatMode.SEDANG.name).orEmpty()
        val parsedMode = runCatching { RailxChatMode.valueOf(storedModeName) }.getOrDefault(RailxChatMode.SEDANG)
        return RelxChatProfile(
            persona = prefs.getString(KEY_PERSONA, DEFAULT_PERSONA).orEmpty(),
            memory = prefs.getString(KEY_MEMORY, "").orEmpty(),
            instructions = prefs.getString(KEY_INSTRUCTIONS, "").orEmpty(),
            reasoningDepth = prefs.getString(KEY_REASONING_DEPTH, "balanced").orEmpty().ifBlank { "balanced" },
            chatMode = parsedMode
        )
    }

    fun saveProfile(profile: RelxChatProfile) {
        prefs.edit()
            .putString(KEY_PERSONA, profile.persona.take(MAX_PROFILE_CHARS))
            .putString(KEY_MEMORY, profile.memory.take(MAX_PROFILE_CHARS))
            .putString(KEY_INSTRUCTIONS, profile.instructions.take(MAX_PROFILE_CHARS))
            .putString(KEY_REASONING_DEPTH, profile.reasoningDepth.take(20))
            .putString(KEY_CHAT_MODE, profile.chatMode.name)
            .apply()
    }

    fun conversations(): List<RelxConversation> = decodeConversations()
        .sortedByDescending { it.updatedAt }

    fun currentConversation(): RelxConversation? {
        val id = prefs.getString(KEY_CURRENT_ID, null) ?: return null
        return decodeConversations().firstOrNull { it.id == id }
    }

    fun newConversation(): RelxConversation {
        val conversation = RelxConversation(
            id = UUID.randomUUID().toString(),
            title = "Percakapan baru",
            updatedAt = System.currentTimeMillis(),
            messages = emptyList()
        )
        persist(conversations() + conversation)
        prefs.edit().putString(KEY_CURRENT_ID, conversation.id).apply()
        return conversation
    }

    fun selectConversation(id: String): RelxConversation? {
        val selected = decodeConversations().firstOrNull { it.id == id } ?: return null
        prefs.edit().putString(KEY_CURRENT_ID, id).apply()
        return selected
    }

    fun saveMessages(conversationId: String, messages: List<RelxChatMessage>) {
        val existing = decodeConversations().toMutableList()
        val index = existing.indexOfFirst { it.id == conversationId }
        val previous = existing.getOrNull(index)
        val firstUserText = messages.firstOrNull { it.role == ROLE_USER }?.content.orEmpty()
            .replace('\n', ' ').take(TITLE_CHARS).ifBlank { "Percakapan baru" }
        val updated = RelxConversation(
            id = conversationId,
            title = if (previous?.title == "Percakapan baru" || previous == null) firstUserText else previous.title,
            updatedAt = System.currentTimeMillis(),
            messages = messages.takeLast(MAX_MESSAGES)
        )
        if (index >= 0) existing[index] = updated else existing.add(updated)
        persist(existing)
    }

    fun deleteConversation(id: String) {
        val remaining = decodeConversations().filterNot { it.id == id }
        persist(remaining)
        if (prefs.getString(KEY_CURRENT_ID, null) == id) {
            prefs.edit().remove(KEY_CURRENT_ID).apply()
        }
    }

    fun deleteSingleMessage(conversationId: String, messageId: String): List<RelxChatMessage> {
        val existing = decodeConversations().toMutableList()
        val index = existing.indexOfFirst { it.id == conversationId }
        if (index < 0) return emptyList()
        val currentConv = existing[index]
        val filtered = currentConv.messages.filterNot { it.id == messageId }
        val updated = currentConv.copy(
            messages = filtered,
            updatedAt = System.currentTimeMillis()
        )
        existing[index] = updated
        persist(existing)
        return filtered
    }

    fun deleteAllConversations() {
        persist(emptyList())
        prefs.edit().remove(KEY_CURRENT_ID).remove(KEY_PENDING_REQUEST).apply()
    }

    fun savePendingRequest(request: RelxPendingChatRequest) {
        val json = JSONObject().apply {
            put("conversationId", request.conversationId)
            put("mode", request.mode)
            put("userMessage", request.userMessage)
            put("persona", request.persona)
            put("memory", request.memory)
            put("instructions", request.instructions)
            put("reasoningDepth", request.reasoningDepth)
            put("chatMode", request.chatMode.name)
            put("history", JSONArray().apply {
                request.history.forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content))
                }
            })
            put("pendingMessages", encodeMessages(request.pendingMessages))
        }
        prefs.edit().putString(KEY_PENDING_REQUEST, json.toString()).apply()
    }

    fun pendingRequest(): RelxPendingChatRequest? = runCatching {
        val json = JSONObject(prefs.getString(KEY_PENDING_REQUEST, null) ?: return null)
        val historyJson = json.optJSONArray("history") ?: JSONArray()
        val history = (0 until historyJson.length()).mapNotNull { index ->
            historyJson.optJSONObject(index)?.let { it.optString("role") to it.optString("content") }
        }
        val storedChatModeName = json.optString("chatMode", RailxChatMode.SEDANG.name)
        val parsedChatMode = runCatching { RailxChatMode.valueOf(storedChatModeName) }.getOrDefault(RailxChatMode.SEDANG)
        RelxPendingChatRequest(
            conversationId = json.getString("conversationId"),
            mode = json.getString("mode"),
            history = history,
            userMessage = json.getString("userMessage"),
            pendingMessages = decodeMessages(json.optJSONArray("pendingMessages") ?: JSONArray()),
            persona = json.optString("persona"),
            memory = json.optString("memory"),
            instructions = json.optString("instructions"),
            reasoningDepth = json.optString("reasoningDepth", "balanced"),
            chatMode = parsedChatMode
        )
    }.getOrNull()

    fun clearPendingRequest() {
        prefs.edit().remove(KEY_PENDING_REQUEST).apply()
    }

    private fun persist(conversations: List<RelxConversation>) {
        val json = JSONArray()
        conversations.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS).forEach { conversation ->
            json.put(JSONObject().apply {
                put("id", conversation.id)
                put("title", conversation.title)
                put("updatedAt", conversation.updatedAt)
                put("messages", encodeMessages(conversation.messages))
            })
        }
        prefs.edit().putString(KEY_CONVERSATIONS, json.toString()).apply()
    }

    private fun encodeMessages(messages: List<RelxChatMessage>): JSONArray = JSONArray().apply {
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            put(JSONObject().apply {
                put("id", message.id)
                put("role", message.role)
                put("content", message.content)
                put("createdAt", message.createdAt)
            })
        }
    }

    private fun decodeMessages(messagesJson: JSONArray): List<RelxChatMessage> =
        (0 until messagesJson.length()).mapNotNull { messageIndex ->
            val message = messagesJson.optJSONObject(messageIndex) ?: return@mapNotNull null
            val role = message.optString("role")
            if (role != ROLE_USER && role != ROLE_ASSISTANT) return@mapNotNull null
            RelxChatMessage(
                id = message.optString("id", UUID.randomUUID().toString()),
                role = role,
                content = message.optString("content"),
                createdAt = message.optLong("createdAt")
            )
        }

    private fun decodeConversations(): List<RelxConversation> = runCatching {
        val array = JSONArray(prefs.getString(KEY_CONVERSATIONS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val messagesJson = item.optJSONArray("messages") ?: JSONArray()
            val messages = decodeMessages(messagesJson)
            val id = item.optString("id")
            if (id.isBlank()) null else RelxConversation(
                id = id,
                title = item.optString("title", "Percakapan"),
                updatedAt = item.optLong("updatedAt"),
                messages = messages
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private const val DEFAULT_PERSONA = "Asisten yang ramah, teliti, dan jelas"
        private const val KEY_PERSONA = "persona"
        private const val KEY_MEMORY = "memory"
        private const val KEY_INSTRUCTIONS = "instructions"
        private const val KEY_REASONING_DEPTH = "reasoning_depth"
        private const val KEY_CHAT_MODE = "chat_mode"
        private const val KEY_CURRENT_ID = "current_conversation_id"
        private const val KEY_CONVERSATIONS = "conversations"
        private const val KEY_PENDING_REQUEST = "pending_chat_request"
        private const val MAX_CONVERSATIONS = 30
        private const val MAX_MESSAGES = 100
        private const val MAX_PROFILE_CHARS = 4_000
        private const val TITLE_CHARS = 48
    }
}
