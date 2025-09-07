package radium.backend.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command to send broadcast messages to current server
 */
class Broadcast(private val radium: Radium) {
    
    @Command("broadcast", "bc")
    @CommandPermission("radium.command.broadcast")
    @Description("Sends message on the current server")
    fun broadcast(actor: VelocityCommandActor, message: String) {
        if (!actor.isPlayer) {
            // Console broadcast - send to all players
            try {
                val formattedMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "&a&l[BROADCAST] &f$message"
                )
                
                radium.server.allPlayers.forEach { player ->
                    player.sendMessage(formattedMessage)
                }
                
                radium.logger.info("Console sent global broadcast: $message")
                actor.reply(Component.text("Broadcast sent to all players!", NamedTextColor.GREEN))
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to send broadcast: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing broadcast command", e)
            }
            return
        }

        val player = actor.asPlayer()
        
        try {
            val currentServer = player?.currentServer?.orElse(null)
            if (currentServer == null) {
                actor.reply(Component.text("You must be connected to a server to use this command!", NamedTextColor.RED))
                return
            }
            
            // Format the broadcast message
            val formattedMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&a&l[BROADCAST] &f$message"
            )
            
            // Send broadcast to all players on the current server
            currentServer.server.playersConnected.forEach { serverPlayer ->
                serverPlayer.sendMessage(formattedMessage)
            }
            
            // Log the broadcast
            radium.logger.info("${actor.name()} sent broadcast to ${currentServer.serverInfo.name}: $message")
            
            // Confirm to sender
            actor.reply(Component.text("Broadcast sent to ${currentServer.serverInfo.name}!", NamedTextColor.GREEN))
            
        } catch (e: Exception) {
            actor.reply(Component.text("Failed to send broadcast: ${e.message}", NamedTextColor.RED))
            radium.logger.error("Error processing broadcast command", e)
        }
    }
}
