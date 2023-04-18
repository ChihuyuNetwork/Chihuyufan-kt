package love.chihuyu.database

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction

class BoketsuPoint(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BoketsuPoint>(BoketsuPoints) {
        inline fun findOrNew(snowflake: ULong, crossinline init: BoketsuPoint.() -> Unit = {}) =
            find(snowflake) ?: new {
                this.snowflake = snowflake
                this.point = 0
                transaction {
                    init()
                }
            }

        fun find(snowflake: ULong) =
            transaction { find { BoketsuPoints.snowflake eq snowflake }.limit(1).firstOrNull() }
    }

    var snowflake by BoketsuPoints.snowflake
    var point by BoketsuPoints.point
}