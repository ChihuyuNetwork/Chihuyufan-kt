package love.chihuyu.util

import dev.kord.common.Color
import dev.kord.core.entity.Member
import dev.kord.rest.Image

object MemberUtils {

    suspend fun Member.averageColor(): Color {
        val image = memberAvatar?.getImage(Image.Size.Size4096)?.data ?: return Color(255, 255, 255)
        var redBucket = 0
        var blueBucket = 0
        var greenBucket = 0
        image.forEach {
            val color = Color(it.toInt())
            redBucket += color.red
            blueBucket += color.blue
            greenBucket += color.green
        }
        return Color(redBucket / image.size, greenBucket / image.size, blueBucket / image.size)
    }

    fun Member.checkInfraPermission(): Boolean {
        return roleIds.none { it.value in listOf(1026898339121340446u, 1069260736179744839u, 1069828175128956948u) }
    }
}
