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
 * Command to teleport a player to you
 */
class TeleportHere(private val radium: Radium) {
    
    @Command("teleporthere", "tphere")
    @CommandPermission("radium.command.teleporthere")
    fun teleporthere(actor: VelocityCommandActor, targetName: String) {
        if (!actor.isPlayer) {
            actor.reply(Component.text("Only players can use this command!", NamedTextColor.RED))
            return
        }
        
        val player = actor.asPlayer()
        
        radium.scope.launch {
            try {
                val targetPlayer = radium.server.getPlayer(targetName).orElse(null)
                if (targetPlayer == null) {
                    actor.reply(Component.text("Player '$targetName' not found!", NamedTextColor.RED))
                    return@launch
                }
                
                val currentServer = player?.currentServer?.orElse(null)
                if (currentServer == null) {
                    actor.reply(Component.text("You must be connected to a server!", NamedTextColor.RED))
                    return@launch
                }
                
                // Connect target to your server first if different
                val targetServer = targetPlayer.currentServer.orElse(null)
                if (targetServer == null || targetServer.serverInfo.name != currentServer.serverInfo.name) {
                    targetPlayer.createConnectionRequest(currentServer.server).fireAndForget()
                    targetPlayer.sendMessage(Component.text("${player?.username} is teleporting you to them...", NamedTextColor.YELLOW))
                }
                
                // Send teleport request to your server
                val message = "tphere:${targetPlayer.uniqueId}:${player?.uniqueId}"
                currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:teleportcommand"), message.toByteArray())
                
                actor.reply(Component.text("Teleporting ${targetPlayer.username} to you...", NamedTextColor.GREEN))
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to teleport player: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing teleporthere command", e)
            }
        }
    }
}
