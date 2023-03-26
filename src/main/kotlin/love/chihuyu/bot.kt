package love.chihuyu

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.client.OpenAI
import com.mattmalec.pterodactyl4j.PteroBuilder
import com.mattmalec.pterodactyl4j.UtilizationState
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.Image
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import love.chihuyu.pterodactyl.EmbedGenerator
import love.chihuyu.pterodactyl.OperationResponder
import love.chihuyu.pterodactyl.OperationType
import love.chihuyu.util.ChatGPTBridger
import love.chihuyu.util.MemberUtils.averageColor
import love.chihuyu.util.MemberUtils.checkInfraPermission

@OptIn(BetaOpenAI::class)
val chatCache = mutableMapOf<ULong, MutableList<ChatMessage>>()

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
@OptIn(BetaOpenAI::class, KordUnsafe::class)
fun main() = runBlocking {
    val pteroApplication = PteroBuilder.createApplication("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_APP_TOKEN"))
    val pteroClient = PteroBuilder.createClient("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_CLIENT_TOKEN"))
    val openai = OpenAI(System.getenv("OPENAI_TOKEN"))
    val kord = Kord(System.getenv("CHIHUYUFANKT_TOKEN")) {
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

    kord.createGlobalApplicationCommands {
        input("ping", "Pong!")
        input("avatar", "Display member's avatar") {
            user("member", "Specify user to display avatar")
        }
        input("roles", "Display member's roles") {
            user("member", "Specify user to display roles")
        }
        input("pterodactyl", "Manage chihuyu network's pterodactyl") {
            subCommand("servers", "List all servers")
            subCommand("nodeinfo", "Display informations of specify node") {
                string("name", "Node name to display informations") {
                    required = true
                }
            }
            subCommand("serverinfo", "Display informations of specify server") {
                string("name", "Server name to display informations") {
                    required = true
                }
            }
            subCommand("up", "Start the specify server") {
                string("name", "Server name to start") {
                    required = true
                }
            }
            subCommand("down", "Shutdown the specify server") {
                string("name", "Server name to shutdown") {
                    required = true
                }
            }
            subCommand("restart", "Restart the specify server") {
                string("name", "Server name to shutdown") {
                    required = true
                }
            }
            subCommand("kill", "Kill the specify server") {
                string("name", "Server name to shutdown") {
                    required = true
                }
            }
            subCommand("send", "Send command to server") {
                string("name", "Server name to send command") {
                    required = true
                }
                string("command", "Don't need slash") {
                    required = true
                }
            }
        }
        input("chatgpt", "Use CahtGPT API") {
            subCommand("new", "Start new session with ChatGPT") {
                string("text", "Text message to send to chatgpt") {
                    required = true
                }
                string("model", "Model of chatgpt to use")
                number("temperature", "Temperature of ChatGPT message(0~2)") {
                    minValue = .0
                    maxValue = 2.0
                }
                int("max_tokens", "Max message length (100~4000)") {
                    minValue = 100
                    maxValue = 4000
                }
            }
            subCommand("reply", "Continue communication in current session") {
                string("text", "Text message to send to chatgpt") {
                    required = true
                }
                string("model", "Model of chatgpt to use")
                number("temperature", "Temperature of ChatGPT message(0~2)") {
                    minValue = .0
                    maxValue = 2.0
                }
                int("max_tokens", "Max message length (100~4000)") {
                    minValue = 100
                    maxValue = 4000
                }
            }
            subCommand("image", "Create image by given words") {
                string("words", "Words to generate image") {
                    required = true
                }
            }
            subCommand("models", "List of chatgpt's model")
        }
    }

    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        val command = interaction.command

        when (command.rootName) {
            "ping" -> {
                interaction.deferPublicResponse().respond {
                    content = "Avg. " + kord.gateway.averagePing?.toString()
                    toRequest()
                }
            }
            "avatar" -> {
                interaction.deferPublicResponse().respond {
                    embed {
                        val member = command.members["member"] ?: interaction.user
                        title = (member.nickname ?: member.username) + "#${member.discriminator}"
                        color = member.averageColor()
                        val avatar = (member.memberAvatar ?: member.avatar ?: member.defaultAvatar)
                        image = avatar.cdnUrl.toUrl {
                            size = Image.Size.Size4096
                            format = if (avatar.animated) Image.Format.GIF else Image.Format.PNG
                        }
                        timestamp = Clock.System.now()
                    }
                    toRequest()
                }
            }
            "roles" -> {
                interaction.deferPublicResponse().respond {
                    embed {
                        val member = command.members["member"] ?: interaction.user
                        title = (member.nickname ?: member.username) + "#${member.discriminator}"
                        color = member.averageColor()
                        field {
                            value = member.roles.toList().sortedByDescending { it.rawPosition }.joinToString(" ") { it.mention }
                        }
                        timestamp = Clock.System.now()
                    }
                    toRequest()
                }
            }
            "pterodactyl" -> {
                when (command.data.options.value?.get(0)?.name) {
                    "servers" -> {
                        interaction.deferPublicResponse().respond {
                            content = pteroClient.retrieveServers().joinToString("\n") {
                                "${
                                    when (it.retrieveUtilization().execute().state) {
                                        UtilizationState.STARTING -> "⬆️"
                                        UtilizationState.STOPPING -> "⬇️"
                                        UtilizationState.RUNNING -> "\uD83D\uDFE9"
                                        UtilizationState.OFFLINE -> "\uD83D\uDFE5"
                                        else -> "\uD83D\uDFE5"
                                    }
                                } " + "`${it.description}`: `${it.name}`"
                            }
                            toRequest()
                        }
                    }
                    "nodeinfo" -> {
                        interaction.deferPublicResponse().respond {
                            val name = command.strings["name"]
                            val nodes = pteroApplication.retrieveNodesByName(name, false).execute()
                            if (nodes.size == 0) {
                                content = "`$name` was not found."
                            } else {
                                val servers = pteroClient.retrieveServers().execute().filter { it.node == nodes[0].name }.map { it.retrieveUtilization().execute() }
                                embeds = mutableListOf(
                                    EmbedGenerator.nodeInfo(nodes[0], servers)
                                )

                                actionRow {
                                    interactionButton(ButtonStyle.Primary, "refreshstatusnode-${nodes[0].name}") {
                                        label = "Refresh"
                                    }
                                }
                            }
                            toRequest()
                        }
                    }
                    "serverinfo" -> {
                        interaction.deferPublicResponse().respond {
                            val name = command.strings["name"]
                            val servers = pteroClient.retrieveServersByName(name, false).execute()
                            if (servers.size == 0) {
                                content = "`$name` was not found."
                            } else {
                                val serversApplication = pteroApplication.retrieveServersByNode(pteroApplication.retrieveNodesByName(servers[0].node, true).execute()[0]).execute()
                                val utilization = servers[0].retrieveUtilization().execute()

                                embeds = mutableListOf(
                                    EmbedGenerator.serverInfo(servers[0], utilization, serversApplication[0])
                                )

                                actionRow {
                                    interactionButton(ButtonStyle.Primary, "refreshstatusserver-${servers[0].name}") {
                                        label = "Refresh"
                                    }
                                    interactionButton(ButtonStyle.Success, "upserver-${servers[0].name}") {
                                        label = "Start"
                                    }
                                    interactionButton(ButtonStyle.Success, "restartserver-${servers[0].name}") {
                                        label = "Restart"
                                    }
                                    interactionButton(ButtonStyle.Danger, "downserver-${servers[0].name}") {
                                        label = "Stop"
                                    }
                                    interactionButton(ButtonStyle.Danger, "killserver-${servers[0].name}") {
                                        label = "Kill"
                                    }
                                }
                                actionRow {
                                    linkButton("https://panel.chihuyu.love/server/${servers[0].identifier}") {
                                        label = "Console"
                                    }
                                    linkButton("https://panel.chihuyu.love/admin/servers/view/${servers[0].internalId}") {
                                        label = "Manage"
                                    }
                                }
                            }
                            toRequest()
                        }
                    }
                    "up", "down", "restart", "kill", "send" -> {
                        if (interaction.user.checkInfraPermission()) {
                            interaction.respondEphemeral {
                                content = "You don't have permissions."
                            }
                        }
                        interaction.deferPublicResponse().respond {
                            content = OperationResponder.getInputRespond(command, pteroClient, OperationType.valueOf(command.data.options.value!![0].name.uppercase()))
                            toRequest()
                        }
                    }
                }
            }
            "chatgpt" -> {
                when (command.data.options.value?.get(0)?.name) {
                    "new" -> {
                        interaction.deferPublicResponse().respond {
                            chatCache[interaction.user.id.value] = mutableListOf()
                            content = ChatGPTBridger.chat(openai, interaction, command)
                        }
                    }
                    "reply" -> {
                        interaction.deferPublicResponse().respond {
                            chatCache.putIfAbsent(interaction.user.id.value, mutableListOf())
                            content = ChatGPTBridger.chat(openai, interaction,command)
                        }
                    }
                    "image" -> {
                        interaction.deferPublicResponse().respond {
                            content = ChatGPTBridger.image(openai, command)
                        }
                    }
                    "models" -> {
                        interaction.deferPublicResponse().respond {
                            content = "使用可能なモデルの一覧はこちらです\n```${openai.models().joinToString("\n") { it.id.id }}```"
                        }
                    }
                }
            }
        }
    }

    kord.on<GuildButtonInteractionCreateEvent> {
        val id = interaction.componentId.split("-")

        when (id[0]) {
            "refreshstatusserver" -> {
                interaction.deferPublicMessageUpdate().edit {
                    interaction.message.edit {
                        val servers = pteroClient.retrieveServersByName(id[1], false).execute()
                        val serversApplication = pteroApplication.retrieveServersByNode(pteroApplication.retrieveNodesByName(servers[0].node, true).execute()[0]).execute()
                        val utilization = servers[0].retrieveUtilization().execute()
                        embeds = mutableListOf(
                            EmbedGenerator.serverInfo(servers[0], utilization, serversApplication[0])
                        )
                    }
                }
            }
            "upserver" -> {
                if (interaction.user.checkInfraPermission()) interaction.respondEphemeral { content = "You don't have permissions." }
                interaction.deferPublicResponse().respond {
                    content = OperationResponder.getButtonRespond(pteroClient, id, OperationType.UP)
                }
            }
            "restartserver" -> {
                if (interaction.user.checkInfraPermission()) interaction.respondEphemeral { content = "You don't have permissions." }
                interaction.deferPublicResponse().respond {
                    content = OperationResponder.getButtonRespond(pteroClient, id, OperationType.RESTART)
                }
            }
            "downserver" -> {
                if (interaction.user.checkInfraPermission()) interaction.respondEphemeral { content = "You don't have permissions." }
                interaction.deferPublicResponse().respond {
                    content = OperationResponder.getButtonRespond(pteroClient, id, OperationType.DOWN)
                }
            }
            "killserver" -> {
                if (interaction.user.checkInfraPermission()) interaction.respondEphemeral { content = "You don't have permissions." }
                interaction.deferPublicResponse().respond {
                    content = OperationResponder.getButtonRespond(pteroClient, id, OperationType.KILL)
                }
            }
            "sendcommand" -> {
                if (interaction.user.checkInfraPermission()) interaction.respondEphemeral { content = "You don't have permissions." }
                interaction.deferPublicResponse().respond {
                    content = OperationResponder.getButtonRespond(pteroClient, id, OperationType.SEND)
                }
            }

            "refreshstatusnode" -> {
                interaction.deferPublicMessageUpdate().edit {
                    interaction.message.edit {
                        val nodes = pteroApplication.retrieveNodesByName(id[1], false).execute()
                        val utilizations = pteroClient.retrieveServers().execute().filter { it.node == nodes[0].name }.map { it.retrieveUtilization().execute() }
                        embeds = mutableListOf(
                            EmbedGenerator.nodeInfo(nodes[0], utilizations)
                        )
                    }
                }
            }
        }
    }

    // ログインは最後に書く
    kord.login {
        @OptIn(PrivilegedIntent::class)
        intents += Intent.GuildMembers

        println("Logged in ${kord.getSelf().tag}")
    }
}
