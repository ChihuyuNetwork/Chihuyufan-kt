package love.chihuyu

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.client.OpenAI
import com.mattmalec.pterodactyl4j.PteroBuilder
import com.mattmalec.pterodactyl4j.UtilizationState
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.ForumChannel
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.Image
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import love.chihuyu.database.BoketsuPoint
import love.chihuyu.database.BoketsuPoints
import love.chihuyu.pterodactyl.EmbedGenerator
import love.chihuyu.pterodactyl.OperationResponder
import love.chihuyu.pterodactyl.OperationType
import love.chihuyu.util.ChatGPTBridger
import love.chihuyu.util.MemberUtils.checkInfraPermission
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

@OptIn(BetaOpenAI::class)
val chatCache = mutableMapOf<ULong, MutableList<ChatMessage>>()

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
@OptIn(BetaOpenAI::class)
fun main() = runBlocking {
    val pteroApplication = PteroBuilder.createApplication("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_APP_TOKEN"))
    val pteroClient = PteroBuilder.createClient("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_CLIENT_TOKEN"))
    val openai = OpenAI(System.getenv("OPENAI_TOKEN"))
    val dbFile = File("data.db")
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

    if (!dbFile.exists()) {
        dbFile.createNewFile()
    }

    Database.connect("jdbc:sqlite:${dbFile.path}", driver = "org.sqlite.JDBC")
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.createMissingTablesAndColumns(BoketsuPoints, withLogs = true)
    }

    kord.createGlobalApplicationCommands {
        input("ping", "Pong!")
        input("avatar", "Display member's avatar") {
            user("member", "Specify user to display avatar")
        }
        input("roles", "Display member's roles") {
            user("member", "Specify user to display roles")
        }
        input("boketsu", "Manage boketsu user's boketsu points") {
            subCommand("add", "Add specify boketsu points to user") {
                user("user", "User to add boketsu point") {
                    required = true
                }
                integer("point", "Amount of boketsu point to add") {
                    required = true
                }
            }
            subCommand("remove", "Remove specify boketsu points to user") {
                user("user", "User to remove boketsu point") {
                    required = true
                }
                integer("point", "Amount of boketsu point to remove") {
                    required = true
                }
            }
            subCommand("stats", "Show boketsu stats of specify user") {
                user("user", "User to show boketsu stats") {
                    required = true
                }
            }
            subCommand("ranking", "Show boketsu point ranking")
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
            subCommand("backups", "List all server's backups")
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
                integer("max_tokens", "Max message length (100~4000)") {
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
                integer("max_tokens", "Max message length (100~4000)") {
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
        input("youtube-thumbnail", "Show thumbnails of youtube video") {
            string("target", "ID or URL of youtube video") {
                required = true
            }
            string("mode", "Thumbnail type of video") {
                required = true
                choice("Default", "default")
                choice("High Quality", "hqdefault")
                choice("Medium Quality", "mqdefault")
                choice("Standard", "sddefault")
                choice("Maximum", "maxresdefault")
            }
        }
        input("valorant-custom", "Spread members play valorant for custom mode")
        input("message-ranking", "Show ranking of all users messages")
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
                        color = member.accentColor
                        val avatar = (member.memberAvatar ?: member.avatar ?: member.defaultAvatar)
                        image = avatar.cdnUrl.toUrl {
                            size = Image.Size.Size4096
                            format = if (avatar.isAnimated) Image.Format.GIF else Image.Format.PNG
                        }
                        timestamp = Clock.System.now()
                    }
                }
            }
            "roles" -> {
                interaction.deferPublicResponse().respond {
                    embed {
                        val member = command.members["member"] ?: interaction.user
                        title = (member.nickname ?: member.username) + "#${member.discriminator}"
                        color = member.accentColor
                        field {
                            value = member.roles.toList().sortedByDescending { it.rawPosition }.joinToString(" ") { it.mention }
                        }
                        timestamp = Clock.System.now()
                    }
                }
            }
            "valorant-spread" -> {
                val msg = interaction.deferPublicResponse().respond {
                    content = "カスタム参加者は✅を押してください"
                    actionRow {
                        interactionButton(ButtonStyle.Primary, "valorantspread") {
                            label = "割り振る"
                        }
                    }
                }

                msg.message.addReaction(ReactionEmoji.Unicode("✅"))
            }
            "message-ranking" -> {
                val msg = interaction.deferPublicResponse().respond {
                    content = "チャンネル/スレッドをカウント中・・・"
                }.message

                val messageCountMap = mutableMapOf<Snowflake, Int>()
                val channels = interaction.guild.channels.toList()
                val threads = channels.flatMap {
                    ((it as? TextChannel)?.activeThreads?.toList()?.toMutableList() ?: mutableListOf()) +
                            ((it as? TextChannel)?.getPublicArchivedThreads()?.toList()?.toMutableList() ?: mutableListOf()) +
                            ((it as? TextChannel)?.getPrivateArchivedThreads()?.toList()?.toMutableList() ?: mutableListOf())
                }

                suspend fun countMessages(targetChannel: GuildMessageChannel) {
                    val messages = targetChannel.messages.toList()
                    messages.forEachIndexed message@{ index, message ->
                        val author = message.author ?: return@message
                        if (author.isBot) return@message
                        messageCountMap[author.id] = (messageCountMap[author.id] ?: 0).inc()
                        println("Counting ${targetChannel.name}: $index/${messages.lastIndex}")
                    }
                }

                channels.forEach channel@{ channel ->

                    if (channel is ForumChannel) {
                        channel.activeThreads.toList().forEach {
                            countMessages(it)
                        }
                    } else if (channel is TextChannel) {
                        countMessages(channel)
                    }

                    msg.edit {
                        content = """
                            メッセージをカウント中・・・

                            `${channels.indexOf(channel)}/${channels.size + threads.size}`チャンネル/スレッドが集計完了しました
                        """.trimIndent()
                    }
                }

                threads.forEach thread@{ thread ->
                    countMessages(thread)

                    msg.edit {
                        content = """
                            メッセージをカウント中・・・

                            `${threads.indexOf(thread) + channels.size}/${channels.size + threads.size}`チャンネル/スレッドが集計完了しました
                        """.trimIndent()
                    }
                }

                msg.edit {
                    content = "最新メッセージランキング\n"
                    messageCountMap.toList().sortedByDescending { it.second }.forEachIndexed { index, pair ->
                        content += "\n**${index.inc()}**. `${
                            interaction.guild.getMemberOrNull(pair.first)?.displayName ?: kord.getUser(pair.first)?.username ?: "Deleted User"
                        }` (${pair.second}メッセージ)"
                    }
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
                        }
                    }
                    "backups" -> {
                        interaction.deferPublicResponse().respond {
                            embed {
                                timestamp = Clock.System.now()
                                title = "Backup List"
                                color = Color(100, 255, 100)
                                pteroApplication.retrieveServers().execute().forEach {
                                    val backups = pteroClient.retrieveServerByIdentifier(it.identifier).execute().retrieveBackups().execute()
                                    if (backups.isNotEmpty()) {
                                        field(it.name, false) {
                                            backups.joinToString("\n") { backup -> "${backup.name} | ${"%.2f".format(backup.size / 1024.0 / 1024.0 / 1024.0)}GB" }
                                        }
                                    }
                                }
                            }
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
                            content = ChatGPTBridger.chat(openai, interaction, command)
                        }
                    }
                    "image" -> {
                        interaction.deferPublicResponse().respond {
                            content = ChatGPTBridger.image(openai, command)
                        }
                    }
                    "models" -> {
                        interaction.deferEphemeralResponse().respond {
                            content = "使用可能なモデルの一覧はこちらです\n```${openai.models().joinToString("\n") { it.id.id }}```"
                        }
                    }
                }
            }
            "youtube-thumbnail" -> {
                interaction.deferPublicResponse().respond {
                    val target = interaction.command.strings["target"]!!
                    val mode = interaction.command.strings["mode"]!!
                    embed {
                        val id = "https://img.youtube.com/vi/${
                        if ("https://" in target) {
                            if ("youtu.be" in target) {
                                target.substringAfter("be/").substringBefore('&')
                            } else if ("v=" in target) {
                                target.substringAfter("v=").substringBefore('&')
                            } else if ("shorts" in target) {
                                target.substringAfter("shorts/").substringBefore('&')
                            } else {
                                target
                            }
                        } else {
                            target
                        }
                        }/$mode.jpg"
                        timestamp = Clock.System.now()
                        image = id
                    }
                }
            }

            "boketsu" -> {
                when (command.data.options.value?.get(0)?.name) {
                    "add" -> {
                        interaction.deferPublicResponse().respond {
                            newSuspendedTransaction {
                                addLogger(StdOutSqlLogger)
                                val user = interaction.command.users["user"]!!.asMember(interaction.guildId)
                                val amount = interaction.command.integers["point"]!!
                                BoketsuPoint.findOrNew(user.id.value).point += amount
                                content = if (interaction.user.id.value != 716263398886604830.toULong()) "貴様、ボケツではないな・・・" else "${user.displayName}に**${amount}ボケツポイント**を追加"
                            }
                        }
                    }
                    "remove" -> {
                        interaction.deferPublicResponse().respond {
                            newSuspendedTransaction {
                                addLogger(StdOutSqlLogger)
                                val user = interaction.command.users["user"]!!.asMember(interaction.guildId)
                                val amount = interaction.command.integers["point"]!!
                                BoketsuPoint.findOrNew(user.id.value).point -= 1
                                content = if (interaction.user.id.value != 716263398886604830.toULong()) "貴様、ボケツではないな・・・" else "${user.displayName}から**${amount}ボケツポイント**を没収"
                            }
                        }
                    }
                    "stats" -> {
                        interaction.deferPublicResponse().respond {
                            newSuspendedTransaction {
                                val user = interaction.command.users["user"]!!.asMember(interaction.guildId)
                                content = "${user.displayName}は**${BoketsuPoint.findOrNew(user.id.value).point}ボケツポイント**を所有しています"
                            }
                        }
                    }
                    "ranking" -> {
                        interaction.deferPublicResponse().respond {
                            newSuspendedTransaction {
                                fun memberName(r: ResultRow) = suspend { interaction.guild.getMember(Snowflake(r[BoketsuPoints.snowflake])).displayName }
                                content = BoketsuPoints.selectAll().limit(20).sortedByDescending { it[BoketsuPoints.point] }.filter { it[BoketsuPoints.point] != 0L }.mapIndexed { index, resultRow ->
                                    "${index.inc()}. ${memberName(resultRow).invoke()} (${resultRow[BoketsuPoints.point]}pt)"
                                }.joinToString("\n")
                            }
                        }
                    }
                }
            }
        }
    }

    kord.on<GuildButtonInteractionCreateEvent> {
        when (val id = interaction.componentId) {
            "valorantspread" -> {
                interaction.deferPublicResponse().respond {
                    val reactors = interaction.message.getReactors(ReactionEmoji.Unicode("✅"))
                    val teams = reactors.toList().shuffled().minus(kord.getSelf()).map { it.mention }.chunked(reactors.count() / 2)
                    embeds = mutableListOf(
                        if (teams.size < 2) {
                            EmbedBuilder()
                                .apply {
                                    title = "人数が足りません"
                                    timestamp = Clock.System.now()
                                }
                        } else {
                            EmbedBuilder()
                                .apply {
                                    title = "チーム割り振り結果"
                                    field("アタッカーサイド", false) { teams[0].joinToString("\n") }
                                    field("ディフェンダーサイド", false) { teams[1].joinToString("\n") }
                                    timestamp = Clock.System.now()
                                }
                        }
                    )
                }
            }
            else -> when (val splid = id.split("-")[0]) {
                "refreshstatusserver" -> {
                    interaction.deferPublicMessageUpdate().edit {
                        interaction.message.edit {
                            val servers = pteroClient.retrieveServersByName(id.split("-")[1], false).execute()
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
                            val nodes = pteroApplication.retrieveNodesByName(id.split("-")[1], false).execute()
                            val utilizations = pteroClient.retrieveServers().execute().filter { it.node == nodes[0].name }.map { it.retrieveUtilization().execute() }
                            embeds = mutableListOf(
                                EmbedGenerator.nodeInfo(nodes[0], utilizations)
                            )
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
