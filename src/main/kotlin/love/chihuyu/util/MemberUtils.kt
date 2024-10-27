package love.chihuyu.util

import dev.kord.core.entity.Member

object MemberUtils {
    fun Member.checkInfraPermission(): Boolean {
        return roleIds.none { it.value in listOf(1026898339121340446u, 1069260736179744839u, 1069828175128956948u) }
    }
}
