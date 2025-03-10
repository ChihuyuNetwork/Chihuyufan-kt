package love.chihuyu

import com.mattmalec.pterodactyl4j.PteroBuilder
import com.mattmalec.pterodactyl4j.UtilizationState
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.cache.map.lruLinkedHashMap
import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.reply
import dev.kord.core.cache.lruCache
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.*
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.ALL
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.Image
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import love.chihuyu.pterodactyl.EmbedGenerator
import love.chihuyu.pterodactyl.OperationResponder
import love.chihuyu.pterodactyl.OperationType
import love.chihuyu.util.MemberUtils.checkInfraPermission

// 本来suspendにしないといけないが、メイン関数にするためにrunBlockingにしている
@OptIn(PrivilegedIntent::class)
fun main() =
    runBlocking {
        val pteroApplication = PteroBuilder.createApplication("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_APP_TOKEN"))
        val pteroClient = PteroBuilder.createClient("https://panel.chihuyu.love/", System.getenv("PTERODACTYL_CLIENT_TOKEN"))
        val kord =
            Kord(System.getenv("CHIHUYUFANKT_TOKEN")) {
                // キャッシュしておくことでAPIを叩くことなくデータを取得できる
                cache {
                    defaultGenerator = lruCache(2147483647)
                    users { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    members { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    roles { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    guilds { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    channels { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    voiceState { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    emojis { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    stickers { cache, description -> MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap()) }
                    messages { cache, description -> MapEntryCache(cache, description, MapLikeCollection.lruLinkedHashMap(2147483647)) }
                }
            }

        kord.createGlobalApplicationCommands {
            input("ping", "Pong!")
            input("avatar", "メンバーのアバターを表示します") {
                user("member", "メンバー")
            }
            input("roles", "メンバーのロール一覧を表示します") {
                user("member", "メンバー")
            }
            input("pterodactyl", "Pterodactylに関する操作を行います") {
                subCommand("servers", "サーバー一覧を表示します")
                subCommand("nodeinfo", "ノードに関する情報を表示します") {
                    string("name", "ノード名") { required = true }
                }
                subCommand("serverinfo", "サーバーに関する情報を表示します") {
                    string("name", "サーバー名") { required = true }
                }
                subCommand("up", "サーバーを開始します") {
                    string("name", "サーバー名") { required = true }
                }
                subCommand("down", "サーバーを停止します") {
                    string("name", "サーバー名") { required = true }
                }
                subCommand("restart", "サーバーを再起動します") {
                    string("name", "サーバー名") { required = true }
                }
                subCommand("kill", "サーバーを強制停止します") {
                    string("name", "サーバー名") { required = true }
                }
                subCommand("send", "サーバーにコマンドを送信します") {
                    string("name", "サーバー名") { required = true }
                    string("command", "コマンド") { required = true }
                }
                subCommand("backups", "バックアップ一覧を表示します")
            }
            input("youtube-thumbnail", "Youtubeの動画のサムネイルを取得します") {
                string("target", "URL") { required = true }
            }
            input("valorant-custom", "Valorantカスタムの割り振りをします") {
                user("ignore1", "無視するメンバー")
                user("ignore2", "無視するメンバー")
                user("ignore3", "無視するメンバー")
                user("ignore4", "無視するメンバー")
                user("ignore5", "無視するメンバー")
                user("ignore6", "無視するメンバー")
                user("ignore7", "無視するメンバー")
                user("ignore8", "無視するメンバー")
                user("ignore9", "無視するメンバー")
                user("ignore10", "無視するメンバー")
                user("ignore11", "無視するメンバー")
                user("ignore12", "無視するメンバー")
                user("ignore13", "無視するメンバー")
                user("ignore14", "無視するメンバー")
                user("ignore15", "無視するメンバー")
                user("ignore16", "無視するメンバー")
                user("ignore17", "無視するメンバー")
                user("ignore18", "無視するメンバー")
                user("ignore19", "無視するメンバー")
                user("ignore20", "無視するメンバー")
            }
            input("message-ranking", "メンバーのメッセージ数のランキングを表示します")
            input("random-vc", "現在いるVCからランダムにメンバーを抽選します")
            input("emoji-image", "絵文字の画像リンクを取得します") {
                string("emoji", "絵文字") { required = true }
            }
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
                            image =
                                avatar.cdnUrl.toUrl {
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
                    interaction.deferPublicResponse().respond {
                        val ignoreds = command.users.values

                        val channel = interaction.user.getVoiceStateOrNull()?.getChannelOrNull() as? VoiceChannel
                        if (channel == null) {
                            content = "VCに参加してから実行してください"
                            return@respond
                        }
                        val attackers = mutableListOf<User>()
                        val defenders = mutableListOf<User>()

                        channel.voiceStates.toList().shuffled().filter {
                            interaction.guild.getMember(it.userId) !in ignoreds && !interaction.guild.getMember(it.userId).isBot
                        }.forEachIndexed { index, voiceState ->
                            val user = interaction.guild.getMember(voiceState.userId)
                            if (index % 2 == 1) {
                                attackers += user
                            } else {
                                defenders += user
                            }
                        }

                        embed {
                            title = "アタッカーサイド"
                            color = Color(255, 100, 100)
                            description = attackers.joinToString("\n") { it.mention }
                        }
                        embed {
                            title = "ディフェンダーサイド"
                            color = Color(100, 200, 190)
                            description = defenders.joinToString("\n") { it.mention }
                        }

                        val maps = listOf("サンセット", "ロータス", "パール", "フラクチャー", "ブリーズ", "アイスボックス", "バインド", "ヘイブン", "スプリット", "アセント", "アビス")

                        embed {
                            title = "マップ"
                            color = Color(200, 200, 200)
                            description = maps.random()
                        }
                    }
                }
                "youtube-thumbnail" -> {
                    interaction.deferPublicResponse().respond {
                        val target = interaction.command.strings["target"]!!
                        listOf("default", "hqdefault", "mqdefault", "sddefault", "maxresdefault")
                            .forEach {
                                embed {
                                    title = it
                                    image = "https://img.youtube.com/vi_webp/${
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
                                    }/$it.webp"
                                }
                            }
                    }
                }
                "random-vc" -> {
                    interaction.deferPublicResponse().respond {
                        val vc = interaction.user.getVoiceState().getChannelOrNull() as? VoiceChannel
                        if (vc == null) {
                            content = "VCに参加してから実行してください"
                            return@respond
                        }
                        val vcMembers = vc.fetchChannel().voiceStates.toList()
                        val member = vcMembers.filterNot { it.getMember().isBot }.random()
                        content = member.getMember().mention
                    }
                }
                "emoji-image" -> {
                    interaction.deferPublicResponse().respond {
                        val emoji = interaction.guild.emojis.firstOrNull { it.name == command.strings["emoji"] }
                        if (emoji == null) {
                            content = "絵文字が見つかりませんでした"
                            return@respond
                        }
                        embed {
                            title = emoji.name
                            image =
                                emoji.image.cdnUrl.toUrl {
                                    size = Image.Size.Size4096
                                    format = if (emoji.isAnimated) Image.Format.GIF else Image.Format.PNG
                                }
                            timestamp = Clock.System.now()
                        }
                    }
                }
                "message-ranking" -> {
                    // 15分でインタラクションのメッセージは期限が切れてエラー起きるのでここで完結させておく
                    interaction.deferPublicResponse().respond {
                        content = "集計を開始します"
                    }

                    val msg = interaction.channel.createMessage("メッセージを集計中・・・")
                    val messageCountMap = mutableMapOf<Snowflake, Int>()
                    val channels = interaction.guild.channels.toList()

                    suspend fun countMessages(targetChannel: GuildMessageChannel) {
                        val messages = targetChannel.messages
                        val name = targetChannel.name
                        var messageCount = 0
                        messages.collect message@{ message ->
                            val author = message.author ?: return@message
                            messageCountMap[author.id] = (messageCountMap[author.id] ?: 0).inc()
                            messageCount += 1
                            println("[#$name] Found $messageCount messages")
                        }
                        interaction.channel.createMessage("【集計完了】${targetChannel.mention} / ${messageCount}msgs")
                    }

                    suspend fun countThreadMessages(targetChannel: ThreadParentChannel) {
                        targetChannel.activeThreads.collect {
                            countMessages(it)
                        }
                        targetChannel.getPublicArchivedThreads(Clock.System.now(), Int.MAX_VALUE).collect {
                            countMessages(it)
                        }
                    }

                    val jobs = mutableListOf<Job>()

                    channels.forEach channel@{ channel ->
                        val job = launch {
                                when (channel) {
                                    is TextChannel, is NewsChannel -> {
                                        countMessages(channel)
                                        countThreadMessages(channel)
                                    }
                                    is MediaChannel, is ForumChannel -> {
                                        countThreadMessages(channel)
                                    }
                                    is VoiceChannel -> countMessages(channel)
                                    is StageChannel -> countMessages(channel)
                                }
                            }
                        jobs += job
                        job.invokeOnCompletion {
                            jobs -= job
                            if (jobs.isNotEmpty()) return@invokeOnCompletion
                            launch {
                                msg.edit {
                                    content = "全チャンネルの集計が完了しました"
                                }

                                val chunked = messageCountMap.toList().sortedByDescending { it.second }.chunked(30)
                                val mainContent = mutableListOf<List<String>>()
                                chunked.forEach { chunk ->
                                    mainContent += chunk.map {
                                        "**${chunk.indexOf(it).inc() + (chunked.indexOf(chunk) * 30)}.** <@${interaction.guild.getMemberOrNull(it.first)?.id?.value}> / ${it.second}msg"
                                    }
                                }
                                mainContent.forEachIndexed { index, s ->
                                    msg.reply {
                                        content = "集計結果"
                                        embed {
                                            title = "**～${30 * index.inc()}位**"
                                            description = s.joinToString("\n")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "pterodactyl" -> {
                    when (command.data.options.value?.get(0)?.name) {
                        "servers" -> {
                            interaction.deferPublicResponse().respond {
                                content =
                                    pteroClient.retrieveServers().joinToString("\n") {
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
                                    embeds =
                                        mutableListOf(
                                            EmbedGenerator.nodeInfo(nodes[0], servers),
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
                                    val serversApplication =
                                        pteroApplication.retrieveServersByNode(
                                            pteroApplication.retrieveNodesByName(servers[0].node, true).execute()[0],
                                        ).execute()
                                    val utilization = servers[0].retrieveUtilization().execute()

                                    embeds =
                                        mutableListOf(
                                            EmbedGenerator.serverInfo(servers[0], utilization, serversApplication[0]),
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
                                        val backups =
                                            pteroClient.retrieveServerByIdentifier(
                                                it.identifier,
                                            ).execute().retrieveBackups().execute()
                                        if (backups.isNotEmpty()) {
                                            field(it.name, false) {
                                                backups.joinToString(
                                                    "\n",
                                                ) { backup ->
                                                    "${backup.name} | ${"%.2f".format(
                                                        backup.size / 1024.0 / 1024.0 / 1024.0,
                                                    )}GB"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        kord.on<GuildButtonInteractionCreateEvent> {
            when (val id = interaction.componentId) {
                else ->
                    when (val splid = id.split("-")[0]) {
                        "refreshstatusserver" -> {
                            interaction.deferPublicMessageUpdate().edit {
                                interaction.message.edit {
                                    val servers = pteroClient.retrieveServersByName(id.split("-")[1], false).execute()
                                    val serversApplication =
                                        pteroApplication.retrieveServersByNode(
                                            pteroApplication.retrieveNodesByName(servers[0].node, true).execute()[0],
                                        ).execute()
                                    val utilization = servers[0].retrieveUtilization().execute()
                                    embeds =
                                        mutableListOf(
                                            EmbedGenerator.serverInfo(servers[0], utilization, serversApplication[0]),
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
                                    embeds =
                                        mutableListOf(
                                            EmbedGenerator.nodeInfo(nodes[0], utilizations),
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
