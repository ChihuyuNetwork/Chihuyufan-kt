package love.chihuyu.util

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.image.ImageCreation
import com.aallam.openai.api.image.ImageSize
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.core.entity.interaction.InteractionCommand
import love.chihuyu.chatCache

object ChatGPTBridger {

    @OptIn(BetaOpenAI::class)
    suspend fun chat(openai: OpenAI, interaction: GuildChatInputCommandInteraction, command: InteractionCommand): String {
        return try {
            chatCache[interaction.user.id.value]!! += ChatMessage(ChatRole.User, command.strings["text"]!!)
            val completion = openai.chatCompletion(
                ChatCompletionRequest(
                    ModelId("gpt-3.5-turbo"),
                    chatCache[interaction.user.id.value]!!,
                    temperature = command.numbers["temperature"],
                    maxTokens = command.integers["max_tokens"]?.toInt()
                )
            ).choices.last().message
            chatCache[interaction.user.id.value]!! += completion!!
            completion?.content!!
        } catch (e: Throwable) {
            "Error encountered!\n```${e.stackTraceToString()}```"
        }
    }

    @OptIn(BetaOpenAI::class)
    suspend fun image(openai: OpenAI, command: InteractionCommand): String {
        return try {
            openai.imageURL(ImageCreation(command.strings["words"]!!, size = ImageSize.is1024x1024)).last().url
        } catch (e: Throwable) {
            "Error encountered!\n```${e.stackTraceToString()}```"
        }
    }
}