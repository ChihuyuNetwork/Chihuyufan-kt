package love.chihuyu

import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.service.RestClient
import kotlinx.coroutines.runBlocking

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
fun main(args: Array<String>) = runBlocking {
    val rest = RestClient(args[0])
    val kord = Kord(args[0]) {
        // キャッシュしておくことでAPIを叩くことなくデータを取得できる
        cache {
            users { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
            members { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
            roles { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
            guilds { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
            channels { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
            voiceState { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
            emojis { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
            stickers { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
        }
    }

    kord.createGlobalChatInputCommand("ping", "a pong")

    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        val command = interaction.command

        when (command.rootName) {
            "ping" -> {
                interaction.deferPublicResponse().respond {
                    content = kord.gateway.averagePing?.toString()
                }
            }
        }
    }

    // ログインは最後に書く
    kord.login {
        println("Logined as ${kord.getSelf().tag}")
    }
}
