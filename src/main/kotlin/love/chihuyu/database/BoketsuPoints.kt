package love.chihuyu.database

import org.jetbrains.exposed.dao.id.IntIdTable

object BoketsuPoints: IntIdTable("boketsu") {
    @OptIn(ExperimentalUnsignedTypes::class)
    val snowflake = ulong("snowflake").uniqueIndex()
    val point = long("point")
}