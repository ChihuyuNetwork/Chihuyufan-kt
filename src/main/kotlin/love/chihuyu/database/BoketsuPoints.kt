package love.chihuyu.database

import org.jetbrains.exposed.dao.id.IntIdTable

object BoketsuPoints: IntIdTable("boketsu") {
    val snowflake = ulong("id")
    val point = long("point")
}