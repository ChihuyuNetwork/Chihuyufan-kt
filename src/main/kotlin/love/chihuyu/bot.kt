package love.chihuyu

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.image.ImageCreation
import com.aallam.openai.api.model.ModelId
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
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.ALL
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.Image
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.actionRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import love.chihuyu.pterodactyl.EmbedGenerator
import love.chihuyu.pterodactyl.OperationResponder
import love.chihuyu.pterodactyl.OperationType
import love.chihuyu.util.MemberUtils.checkInfraPermission

@OptIn(BetaOpenAI::class)
val chatCache = mutableMapOf<ULong, MutableList<ChatMessage>>()

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
@OptIn(PrivilegedIntent::class)
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
                string("name", "Node name to display informations") { required = true }
            }
            subCommand("serverinfo", "Display informations of specify server") {
                string("name", "Server name to display informations") { required = true }
            }
            subCommand("up", "Start the specify server") {
                string("name", "Server name to start") { required = true }
            }
            subCommand("down", "Shutdown the specify server") {
                string("name", "Server name to shutdown") { required = true }
            }
            subCommand("restart", "Restart the specify server") {
                string("name", "Server name to shutdown") { required = true }
            }
            subCommand("kill", "Kill the specify server") {
                string("name", "Server name to shutdown") { required = true }
            }
            subCommand("send", "Send command to server") {
                string("name", "Server name to send command") { required = true }
                string("command", "Don't need slash") { required = true }
            }
            subCommand("backups", "List all server's backups")
        }
        input("gpt", "Use CahtGPT API") {
            subCommand("chat", "Start new session with ChatGPT") {
                string("text", "Text message to send to chatgpt") { required = true }
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
                string("text", "Text message to send to chatgpt") { required = true }
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
            subCommand("models", "List of chatgpt's model")
        }
        input("youtube-thumbnail", "Show thumbnails of youtube video") {
            string("target", "ID or URL of youtube video") { required = true }
        }
        input("valorant-custom", "Spread members play valorant for custom mode")
        input("message-ranking", "Show ranking of all users messages")
        input("random-vc", "Choose user randomly from vc")
        input("emoji-image", "Get a image link from emoji")
    }

    kord.on<MessageCreateEvent> {
        val channelId = message.channelId.value

        when (channelId) {
            // やりたいやつ
            1134479379267866624u -> {
                message.addReaction(ReactionEmoji.Unicode("✨"))
            }
            // ギャラリー
            929029506595954730u -> {
                message.addReaction(ReactionEmoji.Unicode("❤️"))
            }
        }
    }

    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        val command = interaction.command

        when (command.rootName) {
            "random-vc" -> {
                interaction.deferPublicResponse().respond {
                    val vc = interaction.user.getVoiceState().getChannelOrNull() as? VoiceChannel
                    val vcMembers = vc!!.fetchChannel().voiceStates.toList()
                    val human = vcMembers.filterNot { it.getMember().isBot }.random()
                    content = human.getMember().mention
                }
            }
            "ping" -> {
                interaction.deferPublicResponse().respond {
                    content = "Avg. " + kord.gateway.averagePing?.toString()
                }
            }
            "avatar" -> {
                interaction.deferPublicResponse().respond {
                    embed {
                        val member = command.members["member"] ?: interaction.user
                        title = member.effectiveName
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
                        title = member.effectiveName
                        color = member.accentColor
                        field {
                            value = member.roles.toList().sortedByDescending { it.rawPosition }.joinToString(" ") { it.mention }
                        }
                        timestamp = Clock.System.now()
                    }
                }
            }
            "valorant-custom" -> {
                val msg = interaction.deferPublicResponse().respond {
                    content = "カスタム参加者は✅を押してください"
                    actionRow {
                        interactionButton(ButtonStyle.Primary, "valorantcustomspread-${interaction.user.id.value}") {
                            label = "割り振る"
                        }
                    }
                }.message
                msg.addReaction(ReactionEmoji.Unicode("✅"))
            }
            "message-ranking" -> {
                val msg = interaction.deferPublicResponse().respond {
                    content = "チャンネル/スレッドをカウント中・・・"
                }

                val messageCountMap = mutableMapOf<Snowflake, Int>()
                val channels = interaction.guild.channels.toList()
                val activeThreads = channels.flatMap { (it as? TextChannel)?.activeThreads?.toList() ?: emptyList() }
                val privateThreads = channels.flatMap { (it as? TextChannel)?.getPrivateArchivedThreads()?.toList() ?: emptyList() }
                val publicThreads = channels.flatMap { (it as? TextChannel)?.getPublicArchivedThreads()?.toList() ?: emptyList() }

                suspend fun countMessages(targetChannel: GuildMessageChannel) {
                    val messages = targetChannel.messages.withIndex()
                    val messagesSize = messages.count()
                    val name = targetChannel.name
                    messages.onEach message@{ message ->
                        val author = message.value.author ?: return@message
                        if (author.isBot) return@message
                        messageCountMap[author.id] = (messageCountMap[author.id] ?: 0).inc()
                        println("Counting $name: ${message.index.inc()}/${messagesSize}")
                    }.collect()
                }

                msg.edit {
                    content = "メッセージを集計中・・・"
                }

                channels.forEach channel@{ channel ->
                    if (channel is ForumChannel) {
                        channel.activeThreads.onEach { countMessages(it) }
                    } else if (channel is TextChannel) {
                        countMessages(channel)
                    }
                }

                activeThreads.forEach thread@{
                    countMessages(it)
                }

                privateThreads.forEach thread@{
                    countMessages(it)
                }

                publicThreads.forEach thread@{
                    countMessages(it)
                }

                msg.edit {
                    content = """
                        メッセージランキング
                        
                        """.trimIndent()
                }

                val chunked = messageCountMap.toList().sortedByDescending { it.second }
                var oldContent = msg.message.content
                chunked.forEach {
                    msg.edit {
                        content = oldContent + "\n**${chunked.indexOf(it).inc()}.** `${interaction.guild.getMemberOrNull(it.first)?.effectiveName ?: "Deleted User"}` / ${it.second}msg"
                        oldContent = content ?: ""
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
            "gpt" -> {
                suspend fun chat() {
                    val msg = interaction.deferPublicResponse().respond {
                        content = "はわわ・・・"
                    }

                    chatCache[interaction.user.id.value]!! += ChatMessage(ChatRole.User, command.strings["text"]!!)
                    val completion = openai.chatCompletions(
                        ChatCompletionRequest(
                            ModelId(command.strings["model"] ?: "gpt-3.5-turbo"),
                            chatCache[interaction.user.id.value]!!,
                            temperature = command.numbers["temperature"],
                            maxTokens = command.integers["max_tokens"]?.toInt()
                        )
                    )

                    var tempContent = ""
                    var tempCount = 0

                    completion.collect { chunk ->
                        tempContent += chunk.choices[0].delta.content ?: return@collect
                        if (tempContent.isNotEmpty() && tempCount % 16 == 0) {
                            msg.edit {
                                content = tempContent
                            }
                        }
                        tempCount += 1
                    }.also {
                        msg.edit {
                            content = tempContent
                        }
                        chatCache[interaction.user.id.value]!! += ChatMessage(ChatRole.Assistant, tempContent)
                    }
                }

                when (command.data.options.value?.get(0)?.name) {
                    "chat" -> {
                        chatCache[interaction.user.id.value] = mutableListOf()
                        chat()
                    }
                    "reply" -> {
                        chatCache.putIfAbsent(interaction.user.id.value, mutableListOf())
                        chat()
                    }
                    "image" -> {
                        interaction.deferPublicResponse().respond {
                            content = openai.imageURL(ImageCreation(command.strings["text"]!!)).joinToString("\n") { it.url }
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
                    listOf("default", "hqdefault", "mqdefault", "sddefault", "maxresdefault").forEach {
                        content += "https://img.youtube.com/vi/${
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
                        }/$it.jpg"
                    }
                }
            }
        }
    }

    kord.on<GuildButtonInteractionCreateEvent> {
        when (val id = interaction.componentId) {
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
        intents += Intents.ALL

        println("Logged in ${kord.getSelf().tag}")
    }
}
