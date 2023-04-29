package love.chihuyu.database

import org.jetbrains.exposed.dao.id.IntIdTable

object BoketsuPoints : IntIdTable("boketsu") {
    val snowflake = ulong("snowflake")
    val point = long("point")

    init {
        uniqueIndex(snowflake)
    }
}
