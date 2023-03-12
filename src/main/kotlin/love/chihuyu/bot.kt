package love.chihuyu

import com.mattmalec.pterodactyl4j.DataType
import com.mattmalec.pterodactyl4j.PteroBuilder
import com.mattmalec.pterodactyl4j.UtilizationState
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.common.Color
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.Image
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.embed
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import love.chihuyu.util.MemberUtils.averageColor
import love.chihuyu.util.MemberUtils.checkInfraPermission

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
fun main() = runBlocking {
    val pteroApplication = PteroBuilder.createApplication("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_APP_TOKEN"))
    val pteroClient = PteroBuilder.createClient("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_CLIENT_TOKEN"))
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
                    "serverinfo" -> {
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            return@on
                        }

                        val utilization = servers[0].retrieveUtilization().execute()

                        interaction.deferPublicResponse().respond {
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
                            }
                            toRequest()
                        }
                    }
                    "up" -> {
                        if (interaction.user.checkInfraPermission()) interaction.deferPublicResponse().respond {
                            content = "You don't have permissions."
                            toRequest()
                            return@on
                        }
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            toRequest()
                            return@on
                        }

                        servers[0].start().execute()
                        interaction.deferPublicResponse().respond {
                            content = "\uD83D\uDFE9 `${servers[0].name}` has started."
                            toRequest()
                        }
                    }
                    "down" -> {
                        if (interaction.user.checkInfraPermission()) interaction.deferPublicResponse().respond {
                            content = "You don't have permissions."
                            return@on
                        }
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            toRequest()
                            return@on
                        }

                        servers[0].stop().execute()
                        interaction.deferPublicResponse().respond {
                            content = "\uD83D\uDFE5 `${servers[0].name}` has stopped."
                            toRequest()
                        }
                    }
                    "restart" -> {
                        if (interaction.user.checkInfraPermission()) interaction.deferPublicResponse().respond {
                            content = "You don't have permissions."
                            toRequest()
                            return@on
                        }
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            toRequest()
                            return@on
                        }

                        servers[0].restart().execute()
                        interaction.deferPublicResponse().respond {
                            content = "⬆️ `${servers[0].name}` has restarted."
                            toRequest()
                        }
                    }
                    "kill" -> {
                        if (interaction.user.checkInfraPermission()) interaction.deferPublicResponse().respond {
                            content = "You don't have permissions."
                            toRequest()
                            return@on
                        }
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            toRequest()
                            return@on
                        }

                        servers[0].kill().execute()
                        interaction.deferPublicResponse().respond {
                            content = "\uD83D\uDC80 `$name` has killed."
                            toRequest()
                        }
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
