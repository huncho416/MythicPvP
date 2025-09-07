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
 * Command to feed self or others
 */
class Feed(private val radium: Radium) {
    
    companion object {
        val FEED_CHANNEL = MinecraftChannelIdentifier.create("radium", "feedcommand")
    }
    
    @Command("feed")
    @CommandPermission("radium.command.feed")
    @Description("Feeds self or others")
    fun feed(actor: VelocityCommandActor, targetName: String? = null) {
        radium.scope.launch {
            try {
                if (targetName != null) {
                    // Feed another player
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
                    
                    // Send feed request to target's server
                    val message = "feed:${targetPlayer.uniqueId}"
                    targetServer.sendPluginMessage(FEED_CHANNEL, message.toByteArray())
                    
                    actor.reply(Component.text("Fed ${targetPlayer.username}!", NamedTextColor.GREEN))
                    targetPlayer.sendMessage(Component.text("You have been fed by ${actor.name()}!", NamedTextColor.GREEN))
                    
                } else {
                    // Feed self
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
                    
                    // Send feed request to current server
                    val message = "feed:${player?.uniqueId}"
                    currentServer.sendPluginMessage(FEED_CHANNEL, message.toByteArray())
                    
                    actor.reply(Component.text("You have been fed!", NamedTextColor.GREEN))
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to feed player: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing feed command", e)
            }
        }
    }
}
