package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource

@Command("discord")
class Discord(private val radium: Radium) {

    fun discord(actor: CommandSource) {
        try {
            actor.sendMessage(
                radium.yamlFactory.getMessageComponent("commands.discord.message")
            )
        } catch (e: Exception) {
            radium.logger.error("Error executing discord command", e)
            actor.sendMessage(
                radium.yamlFactory.getMessageComponent("general.unknown_error")
            )
        }
    }
}
