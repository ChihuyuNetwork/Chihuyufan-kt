package love.chihuyu.pterodactyl

import com.mattmalec.pterodactyl4j.client.entities.PteroClient
import dev.kord.core.entity.interaction.GuildButtonInteraction
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.core.entity.interaction.InteractionCommand
import love.chihuyu.util.MemberUtils.checkInfraPermission

object OperationResponder {

    fun getButtonRespond(pteroClient: PteroClient, id: List<String>, interaction: GuildButtonInteraction, operationType: OperationType): String {
        val servers = pteroClient.retrieveServersByName(id[1], false).execute()
        return if (interaction.user.checkInfraPermission()) {
            "You don't have permissions."
        } else if (servers.size == 0) {
            "`${id[1]}` was not found."
        } else {
            try {
                when (operationType) {
                    OperationType.UP -> {
                        servers[0].start().execute()
                        "\uD83D\uDFE9 `${servers[0].name}` has started."
                    }
                    OperationType.DOWN -> {
                        servers[0].stop().execute()
                        "\uD83D\uDFE5 `${servers[0].name}` has stopped."
                    }
                    OperationType.RESTART -> {
                        servers[0].restart().execute()
                        "⬆️ `${servers[0].name}` has restarted."
                    }
                    OperationType.KILL -> {
                        servers[0].kill().execute()
                        "\uD83D\uDC80 `${servers[0].name}` has killed."
                    }
                    else -> {
                        "null"
                    }
                }
            } catch (e: Throwable) {
                "Error encountered!\n```${
                    e.message + "\n" + e.stackTrace.joinToString("\n")
                }```"
            }
        }
    }

    fun getInputRespond(interaction: GuildChatInputCommandInteraction, command: InteractionCommand, pteroClient: PteroClient, operationType: OperationType): String {
        val name = command.strings["name"]
        val commandToSend = command.strings["command"]
        val servers = pteroClient.retrieveServersByName(name, false).execute()
        return if (interaction.user.checkInfraPermission()) {
            "You don't have permissions."
        } else if (servers.size == 0) {
            "`$name` was not found."
        } else {
            try {
                when (operationType) {
                    OperationType.SEND -> {
                        servers[0].sendCommand(commandToSend).execute()
                        "\uD83D\uDCAB `$commandToSend` sent to `$name`"
                    }
                    OperationType.UP -> {
                        servers[0].start().execute()
                        "\uD83D\uDFE9 `${servers[0].name}` has started."
                    }
                    OperationType.DOWN -> {
                        servers[0].stop().execute()
                        "\uD83D\uDFE5 `${servers[0].name}` has stopped."
                    }
                    OperationType.RESTART -> {
                        servers[0].restart().execute()
                        "⬆️ `${servers[0].name}` has restarted."
                    }
                    OperationType.KILL -> {
                        servers[0].kill().execute()
                        "\uD83D\uDC80 `$name` has killed."
                    }
                }
            } catch (e: Throwable) {
                "Error encountered!\n```${
                    e.message + "\n" + e.stackTrace.joinToString("\n")
                }```"
            }
        }
    }
}