package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import radium.backend.annotations.OnlinePlayers
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier

/**
 * Command to teleport self to a player
 */
class Teleport(private val radium: Radium) {
    
    @Command("teleport", "tp")
    @CommandPermission("radium.command.teleport")
    @Description("Teleports self to a player")
    fun teleport(actor: VelocityCommandActor, @OnlinePlayers targetName: String) {
        if (!actor.isPlayer) {
            actor.reply(Component.text("Only players can teleport!", NamedTextColor.RED))
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
                
                val targetServer = targetPlayer.currentServer.orElse(null)
                if (targetServer == null) {
                    actor.reply(Component.text("Target player must be connected to a server!", NamedTextColor.RED))
                    return@launch
                }
                
                // Connect to the target's server first if different
                val currentServer = player?.currentServer?.orElse(null)
                if (currentServer == null || currentServer.serverInfo.name != targetServer.serverInfo.name) {
                    player?.createConnectionRequest(targetServer.server)?.fireAndForget()
                    actor.reply(Component.text("Connecting to ${targetServer.serverInfo.name}...", NamedTextColor.YELLOW))
                }
                
                // Send teleport request to target's server
                val message = "tp:${player?.uniqueId}:${targetPlayer.uniqueId}"
                targetServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:teleportcommand"), message.toByteArray())
                
                actor.reply(Component.text("Teleporting to ${targetPlayer.username}...", NamedTextColor.GREEN))
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to teleport: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing teleport command", e)
            }
        }
    }
}
