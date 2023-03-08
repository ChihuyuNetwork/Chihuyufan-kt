package love.chihuyu.util

import dev.kord.rest.builder.message.EmbedBuilder
import java.time.LocalDateTime

object EmbedUtils {

    fun EmbedBuilder.setTimestamp() {
        footer = EmbedBuilder.Footer().apply {
            val time = LocalDateTime.now()
            text = "${time.year}/${
                "%02d".format(time.monthValue)
            }/${
                "%02d".format(time.dayOfMonth)
            } ${
                "%02d".format(time.hour)
            }:${
                "%02d".format(time.minute)
            }:${
                "%02d".format(time.second)
            }"
        }
    }
}