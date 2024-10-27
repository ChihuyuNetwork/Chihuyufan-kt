package love.chihuyu.pterodactyl

import com.mattmalec.pterodactyl4j.DataType
import com.mattmalec.pterodactyl4j.UtilizationState
import com.mattmalec.pterodactyl4j.application.entities.ApplicationServer
import com.mattmalec.pterodactyl4j.application.entities.Node
import com.mattmalec.pterodactyl4j.client.entities.ClientServer
import com.mattmalec.pterodactyl4j.client.entities.Utilization
import dev.kord.common.Color
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.datetime.Clock
import java.time.format.DateTimeFormatter

object EmbedGenerator {
    fun nodeInfo(
        node: Node,
        utilizations: List<Utilization>,
    ): EmbedBuilder {
        return EmbedBuilder().apply {
            title = "Information of `${node.name}`"
            color = Color(100, 255, 100)
            description = node.description
            timestamp = Clock.System.now()
            field("Address", true) { node.fqdn }
            field("Allocations", true) { node.retrieveAllocations().execute().size.toString() }
            field("Directory", true) { node.retrieveConfiguration().execute().system.dataPath }
            field("CPU Usage", true) { "${"%.2f".format(utilizations.sumOf { it.cpu })}%" }
            field("Memory Usage", true) {
                "${"%.2f".format(utilizations.sumOf { it.memory } / 1024.0 / 1024.0 / 1024.0)}/${node.allocatedMemory.toInt() / 2048.0}GB"
            }
            field("Disk Usage", true) { "${"%.2f".format(utilizations.sumOf { it.disk } / 1024.0 / 1024.0 / 1024.0)}GB" }
            field("Network Ingress", true) { "${"%.2f".format(utilizations.sumOf { it.networkIngress } / 1024.0 / 1024.0)}MB" }
            field("Network Egress", true) { "${"%.2f".format(utilizations.sumOf { it.networkEgress } / 1024.0 / 1024.0)}MB" }
            field("Creation", true) { node.creationDate.format(DateTimeFormatter.ofPattern("YYYY/MM/dd HH:mm:ss")) }
        }
    }

    fun serverInfo(
        server: ClientServer,
        utilization: Utilization,
        serverApplication: ApplicationServer,
    ): EmbedBuilder {
        return EmbedBuilder().apply {
            title = "Information of `${server.name}`"
            color =
                when (utilization.state) {
                    UtilizationState.STARTING -> Color(100, 100, 255)
                    UtilizationState.STOPPING -> Color(100, 100, 255)
                    UtilizationState.RUNNING -> Color(100, 255, 100)
                    UtilizationState.OFFLINE -> Color(255, 100, 100)
                    else -> Color(255, 100, 100)
                }
            description = server.description
            timestamp = Clock.System.now()
            field("Node", true) { server.node }
            field("Status", true) { utilization.state.name }
            field("Primary Allocation", true) { server.primaryAllocation.fullAddress }
            field("CPU Usage", true) { "${utilization.cpu}%" }
            field("Memory Usage", true) { utilization.getMemoryFormatted(DataType.GB) }
            field("Disk Usage", true) { utilization.getDiskFormatted(DataType.GB) }
            field("Network Ingress", true) { utilization.getNetworkIngressFormatted(DataType.MB) }
            field("Network Egress", true) { utilization.getNetworkEgressFormatted(DataType.MB) }
            field("Uptime", true) { utilization.uptimeFormatted }
            field("Creation", true) { serverApplication.creationDate.format(DateTimeFormatter.ofPattern("YYYY/MM/dd HH:mm:ss")) }
        }
    }
}
