package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier

/**
 * Command to give maximum stack amount of held item
 */
class More(private val radium: Radium) {
    
    @Command("more")
    @CommandPermission("radium.command.more")
    @Description("Gives maximum stack amount of held item")
    fun more(actor: VelocityCommandActor) {
        if (!actor.isPlayer) {
            actor.reply(Component.text("This command can only be used by players!", NamedTextColor.RED))
            return
        }

        val player = actor.asPlayer()
        
        radium.scope.launch {
            try {
                // Send plugin message to the player's current server to maximize stack
                val currentServer = player?.currentServer?.orElse(null)
                if (currentServer == null) {
                    actor.reply(Component.text("You must be connected to a server to use this command!", NamedTextColor.RED))
                    return@launch
                }
                
                // Send more request to backend server via plugin messaging
                val message = "more:${player.uniqueId}"
                currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:itemcommand"), message.toByteArray())
                
                actor.reply(Component.text("Item stack maximized!", NamedTextColor.GREEN))
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to maximize item stack: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing more command", e)
            }
        }
    }
}
