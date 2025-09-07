package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import radium.backend.Radium
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command to clear inventory for self or another player
 */
class Clear(private val radium: Radium) {
    
    companion object {
        val INVENTORY_CHANNEL = MinecraftChannelIdentifier.create("radium", "inventorycommand")
    }
    
    @Command("clear")
    @CommandPermission("radium.command.clear")
    @Description("Clears inventory for self or another player")
    fun clear(actor: VelocityCommandActor, targetName: String? = null) {
        radium.scope.launch {
            try {
                if (targetName != null) {
                    // Clear another player's inventory
                    val targetPlayer = radium.server.getPlayer(targetName).orElse(null)
                    if (targetPlayer == null) {
                        actor.reply(Component.text("Player '$targetName' not found!", NamedTextColor.RED))
                        return@launch
                    }
                    
                    val targetServer = targetPlayer.currentServer.orElse(null)
                    if (targetServer == null) {
                        actor.reply(Component.text("Target player must be connected to a server!", NamedTextColor.RED))
                        return@launch
                    }
                    
                    // Send clear request to target's server
                    val message = "clear:${targetPlayer.uniqueId}"
                    targetServer.sendPluginMessage(INVENTORY_CHANNEL, message.toByteArray())
                    
                    actor.reply(Component.text("Cleared ${targetPlayer.username}'s inventory!", NamedTextColor.GREEN))
                    targetPlayer.sendMessage(Component.text("Your inventory has been cleared by ${actor.name()}!", NamedTextColor.YELLOW))
                    
                } else {
                    // Clear own inventory
                    if (!actor.isPlayer) {
                        actor.reply(Component.text("Console must specify a target player!", NamedTextColor.RED))
                        return@launch
                    }
                    
                    val player = actor.asPlayer()
                    val currentServer = player?.currentServer?.orElse(null)
                    if (currentServer == null) {
                        actor.reply(Component.text("You must be connected to a server to use this command!", NamedTextColor.RED))
                        return@launch
                    }
                    
                    // Send clear request to current server
                    val message = "clear:${player?.uniqueId}"
                    currentServer.sendPluginMessage(INVENTORY_CHANNEL, message.toByteArray())
                    
                    actor.reply(Component.text("Your inventory has been cleared!", NamedTextColor.GREEN))
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to clear inventory: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing clear command", e)
            }
        }
    }
}
