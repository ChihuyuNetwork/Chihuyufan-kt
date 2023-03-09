package love.chihuyu

import com.mattmalec.pterodactyl4j.PteroBuilder
import com.mattmalec.pterodactyl4j.UtilizationState
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
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
import love.chihuyu.util.EmbedUtils.setTimestamp
import love.chihuyu.util.MemberUtils.averageColor
import love.chihuyu.util.MemberUtils.checkInfraPermission

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
fun main() = runBlocking {
    val pteroApp = PteroBuilder.createApplication("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_TOKEN"))
    val pteroClient = PteroBuilder.createClient("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_TOKEN"))
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
            subCommand("nodeinfo", "Display informations of specify node machine") {
                string("name", "Node name to display informations") {
                    required = true
                    pteroApp.retrieveNodes().execute().forEach {
                        choice(it.name, it.description ?: "A node machine")
                    }
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
                        setTimestamp()
                    }
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
                        setTimestamp()
                    }
                }
            }
            "pterodactyl" -> {
                when (command.data.options.value?.get(0)?.name) {
                    "servers" -> {
                        val servers = pteroClient.retrieveServers().execute().joinToString("\n") {
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
                        interaction.deferPublicResponse().respond {
                            content = servers
                        }
                    }
                    "nodeinfo" -> {
                        val name = command.strings["name"]
                        val nodes = pteroApp.retrieveNodesByName(name, false).execute()
                        if (nodes.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            return@on
                        }

                        var cpu = 0.0
                        var memory = 0L
                        var disk = 0.0
                        var netIg = 0L
                        var netEg = 0L

                        pteroClient.retrieveServers().execute().forEach {
                            val utilization = it.retrieveUtilization().execute()
                            cpu += utilization.cpu
                            memory += utilization.memory
                            disk += utilization.disk
                            netIg += utilization.networkIngress
                            netEg += utilization.networkEgress
                        }

                        interaction.deferPublicResponse().respond {
                            embed {
                                title = "Information of `${nodes[0].name}`"
                                field("CPU Usage", true) { "${cpu}%/CPU" }
                                field("Memory Usage", true) { "${memory}/${nodes[0].allocatedMemory}" }
                                field("Disk Usage", true) { "${disk}/${nodes[0].allocatedDisk}" }
                                field("Network Igress", true) { "${netIg}MB" }
                                field("Network Egress", true) { "${netEg}MB" }
                                setTimestamp()
                            }
                        }
                    }
                    "up" -> {
                        if (interaction.user.checkInfraPermission()) interaction.deferPublicResponse().respond {
                            content = "You don't have permissions."
                            return@on
                        }
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            return@on
                        }

                        servers[0].start().execute()
                        interaction.deferPublicResponse().respond {
                            content = "\uD83D\uDFE9 `${servers[0].name}` has started."
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
                            return@on
                        }

                        servers[0].stop().execute()
                        interaction.deferPublicResponse().respond {
                            content = "\uD83D\uDFE5 `${servers[0].name}` has stopped."
                        }
                    }
                    "restart" -> {
                        if (interaction.user.checkInfraPermission()) interaction.deferPublicResponse().respond {
                            content = "You don't have permissions."
                            return@on
                        }
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            return@on
                        }

                        servers[0].restart().execute()
                        interaction.deferPublicResponse().respond {
                            content = "⬆️ `${servers[0].name}` has restarted."
                        }
                    }
                    "kill" -> {
                        if (interaction.user.checkInfraPermission()) interaction.deferPublicResponse().respond {
                            content = "You don't have permissions."
                            return@on
                        }
                        val name = command.strings["name"]
                        val servers = pteroClient.retrieveServersByName(name, false).execute()
                        if (servers.isEmpty()) interaction.deferPublicResponse().respond {
                            content = "`$name` was not found."
                            return@on
                        }

                        servers[0].kill().execute()
                        interaction.deferPublicResponse().respond {
                            content = "\uD83D\uDC80 `$name` has killed."
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
