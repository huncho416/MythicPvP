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
 * Command to rename the display name of held items
 */
class Rename(private val radium: Radium) {
    
    @Command("rename")
    @CommandPermission("radium.command.rename")
    @Description("Sets the display name of the held item")
    fun rename(actor: VelocityCommandActor, name: String) {
        if (!actor.isPlayer) {
            actor.reply(Component.text("This command can only be used by players!", NamedTextColor.RED))
            return
        }

        val player = actor.asPlayer()
        
        radium.scope.launch {
            try {
                // Send plugin message to the player's current server to rename the item
                val currentServer = player?.currentServer?.orElse(null)
                if (currentServer == null) {
                    actor.reply(Component.text("You must be connected to a server to use this command!", NamedTextColor.RED))
                    return@launch
                }
                
                // Send rename request to backend server via plugin messaging
                val message = "rename:${player.uniqueId}:${name.replace("&", "§")}"
                currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:itemcommand"), message.toByteArray())
                
                actor.reply(
                    Component.text("Item renamed to: ", NamedTextColor.GREEN)
                        .append(Component.text(name.replace("&", "§"), NamedTextColor.WHITE))
                )
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to rename item: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing rename command", e)
            }
        }
    }
}
