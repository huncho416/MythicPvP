package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource

@Command("store", "shop")
class Store(private val radium: Radium) {

    fun store(actor: CommandSource) {
        try {
            actor.sendMessage(
                radium.yamlFactory.getMessageComponent("commands.store.message")
            )
        } catch (e: Exception) {
            radium.logger.error("Error executing store command", e)
            actor.sendMessage(
                radium.yamlFactory.getMessageComponent("general.unknown_error")
            )
        }
    }
}
