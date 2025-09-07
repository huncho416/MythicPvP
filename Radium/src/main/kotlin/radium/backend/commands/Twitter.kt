package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource

@Command("twitter")
class Twitter(private val radium: Radium) {

    fun twitter(actor: CommandSource) {
        try {
            actor.sendMessage(
                radium.yamlFactory.getMessageComponent("commands.twitter.message")
            )
        } catch (e: Exception) {
            radium.logger.error("Error executing twitter command", e)
            actor.sendMessage(
                radium.yamlFactory.getMessageComponent("general.unknown_error")
            )
        }
    }
}
