package love.chihuyu

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.client.OpenAI
import com.mattmalec.pterodactyl4j.DataType
import com.mattmalec.pterodactyl4j.PteroBuilder
import com.mattmalec.pterodactyl4j.UtilizationState
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
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
import love.chihuyu.chatgpt.CommunicationBridge
import love.chihuyu.util.MemberUtils.averageColor
import love.chihuyu.util.MemberUtils.checkInfraPermission
import java.time.format.DateTimeFormatter

@OptIn(BetaOpenAI::class)
val chatCache = mutableMapOf<ULong, MutableList<ChatMessage>>()

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
@OptIn(BetaOpenAI::class)
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

                                embed {
                                    title = "Information of `${nodes[0].name}`"
                                    color = Color(100, 255, 100)
                                    description = nodes[0].description
                                    timestamp = Clock.System.now()
                                    field("Address", true) { nodes[0].fqdn }
                                    field("Allocations", true) { nodes[0].retrieveAllocations().execute().size.toString() }
                                    field("Directory", true) { nodes[0].retrieveConfiguration().execute().system.dataPath }
                                    field("CPU Usage", true) { "${"%.2f".format(servers.sumOf { it.cpu })}%" }
                                    field("Memory Usage", true) { "${"%.2f".format(servers.sumOf { it.memory } / 1024.0 / 1024.0 / 1024.0)}/${nodes[0].allocatedMemory.toInt() / 2048.0}GB" }
                                    field("Disk Usage", true) { "${"%.2f".format(servers.sumOf { it.disk } / 1024.0 / 1024.0 / 1024.0)}GB" }
                                    field("Network Ingress", true) { "${"%.2f".format(servers.sumOf { it.networkIngress } / 1024.0 / 1024.0)}MB" }
                                    field("Network Egress", true) { "${"%.2f".format(servers.sumOf { it.networkEgress } / 1024.0 / 1024.0)}MB" }
                                    field("Creation", true) { nodes[0].creationDate.format(DateTimeFormatter.ofPattern("YYYY/MM/dd HH:mm:ss")) }
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

                                embed {
                                    title = "Information of `${servers[0].name}`"
                                    color = when (utilization.state) {
                                        UtilizationState.STARTING -> Color(255, 255, 100)
                                        UtilizationState.STOPPING -> Color(255, 255, 100)
                                        UtilizationState.RUNNING -> Color(100, 255, 100)
                                        UtilizationState.OFFLINE -> Color(255, 100, 100)
                                        else -> Color(255, 100, 100)
                                    }
                                    description = servers[0].description
                                    timestamp = Clock.System.now()
                                    field("Node", true) { servers[0].node }
                                    field("Status", true) { utilization.state.name }
                                    field("Primary Allocation", true) { servers[0].primaryAllocation.fullAddress }
                                    field("CPU Usage", true) { "${utilization.cpu}%" }
                                    field("Memory Usage", true) { utilization.getMemoryFormatted(DataType.GB) }
                                    field("Disk Usage", true) { utilization.getDiskFormatted(DataType.GB) }
                                    field("Network Ingress", true) { utilization.getNetworkIngressFormatted(DataType.MB) }
                                    field("Network Egress", true) { utilization.getNetworkEgressFormatted(DataType.MB) }
                                    field("Uptime", true) { utilization.uptimeFormatted }
                                    field("Creation", true) { serversApplication[0].creationDate.format(DateTimeFormatter.ofPattern("YYYY/MM/dd HH:mm:ss")) }
                                }

                                actionRow {
                                    interactionButton(ButtonStyle.Primary, "upserver-${servers[0].name}") {
                                        label = "Start"
                                        emoji = DiscordPartialEmoji(name = "⬆️")
                                    }
                                }
                            }
                            toRequest()
                        }
                    }
                    "up" -> {
                        interaction.deferPublicResponse().respond {
                            val name = command.strings["name"]
                            val servers = pteroClient.retrieveServersByName(name, false).execute()
                            content = if (interaction.user.checkInfraPermission()) {
                                "You don't have permissions."
                            } else if (servers.size == 0) {
                                "`$name` was not found."
                            } else {
                                servers[0].start().execute()
                                "⬆️ `${servers[0].name}` has started."
                            }
                            toRequest()
                        }
                    }
                    "down" -> {
                        interaction.deferPublicResponse().respond {
                            val name = command.strings["name"]
                            val servers = pteroClient.retrieveServersByName(name, false).execute()
                            content = if (interaction.user.checkInfraPermission()) {
                                "You don't have permissions."
                            } else if (servers.size == 0) {
                                "`$name` was not found."
                            } else {
                                servers[0].stop().execute()
                                "⬇️ `${servers[0].name}` has stopped."
                            }
                            toRequest()
                        }
                    }
                    "restart" -> {
                        interaction.deferPublicResponse().respond {
                            val name = command.strings["name"]
                            val servers = pteroClient.retrieveServersByName(name, false).execute()
                            content = if (interaction.user.checkInfraPermission()) {
                                "You don't have permissions."
                            } else if (servers.size == 0) {
                                "`$name` was not found."
                            } else {
                                servers[0].restart().execute()
                                "⬆️ `${servers[0].name}` has restarted."
                            }
                            toRequest()
                        }
                    }
                    "kill" -> {
                        interaction.deferPublicResponse().respond {
                            val name = command.strings["name"]
                            val servers = pteroClient.retrieveServersByName(name, false).execute()
                            content = if (interaction.user.checkInfraPermission()) {
                                "You don't have permissions."
                            } else if (servers.size == 0) {
                                "`$name` was not found."
                            } else {
                                servers[0].kill().execute()
                                "\uD83D\uDC80 `$name` has killed."
                            }
                            toRequest()
                        }
                    }
                    "send" -> {
                        interaction.deferPublicResponse().respond {
                            val name = command.strings["name"]
                            val command = command.strings["command"]
                            val servers = pteroClient.retrieveServersByName(name, false).execute()
                            content = if (interaction.user.checkInfraPermission()) {
                                "You don't have permissions."
                            } else if (servers.size == 0) {
                                "`$name` was not found."
                            } else {
                                try {
                                    servers[0].sendCommand(command).execute()
                                    "\uD83D\uDCAB `$command` sent to `$name`"
                                } catch (e: Throwable) {
                                    "Error encountered!\n```${
                                        e.message + "\n" + 
                                        e.stackTrace.joinToString("\n")
                                    }```"
                                }
                            }
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
                            content = CommunicationBridge.chat(openai, interaction, command)
                        }
                    }
                    "reply" -> {
                        interaction.deferPublicResponse().respond {
                            chatCache.putIfAbsent(interaction.user.id.value, mutableListOf())
                            content = CommunicationBridge.chat(openai, interaction,command)
                        }
                    }
                    "image" -> {
                        interaction.deferPublicResponse().respond {
                            content = CommunicationBridge.image(openai, interaction, command)
                        }
                    }
                }
            }
        }
    }

    kord.on<GuildButtonInteractionCreateEvent> {
        val id = interaction.componentId.split("-")

        when (id[0]) {
            "refreshstatus" -> {
                interaction.message.edit {
                    val servers = pteroClient.retrieveServersByName(id[1], false).execute()
                    val serversApplication = pteroApplication.retrieveServersByNode(pteroApplication.retrieveNodesByName(servers[0].node, true).execute()[0]).execute()
                    val utilization = servers[0].retrieveUtilization().execute()
                    embed {
                        title = "Information of `${servers[0].name}`"
                        color = when (utilization.state) {
                            UtilizationState.STARTING -> Color(255, 255, 100)
                            UtilizationState.STOPPING -> Color(255, 255, 100)
                            UtilizationState.RUNNING -> Color(100, 255, 100)
                            UtilizationState.OFFLINE -> Color(255, 100, 100)
                            else -> Color(255, 100, 100)
                        }
                        description = servers[0].description
                        timestamp = Clock.System.now()
                        field("Node", true) { servers[0].node }
                        field("Status", true) { utilization.state.name }
                        field("Primary Allocation", true) { servers[0].primaryAllocation.fullAddress }
                        field("CPU Usage", true) { "${utilization.cpu}%" }
                        field("Memory Usage", true) { utilization.getMemoryFormatted(DataType.GB) }
                        field("Disk Usage", true) { utilization.getDiskFormatted(DataType.GB) }
                        field("Network Ingress", true) { utilization.getNetworkIngressFormatted(DataType.MB) }
                        field("Network Egress", true) { utilization.getNetworkEgressFormatted(DataType.MB) }
                        field("Uptime", true) { utilization.uptimeFormatted }
                        field("Creation", true) { serversApplication[0].creationDate.format(DateTimeFormatter.ofPattern("YYYY/MM/dd HH:mm:ss")) }
                    }
                }
            }
            "upserver" -> {
                interaction.deferPublicResponse().respond {
                    val servers = pteroClient.retrieveServersByName(id[1], false).execute()
                    content = if (interaction.user.checkInfraPermission()) {
                        "You don't have permissions."
                    } else if (servers.size == 0) {
                        "`${id[1]}` was not found."
                    } else {
                        servers[0].restart().execute()
                        "⬆️ `${servers[0].name}` has started."
                    }
                }
            }
            "restartserver" -> {

            }
            "downserver" -> {

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
