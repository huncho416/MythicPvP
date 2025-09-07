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
 * Command to heal self or others
 */
class Heal(private val radium: Radium) {
    
    companion object {
        val HEAL_CHANNEL = MinecraftChannelIdentifier.create("radium", "healcommand")
    }
    
    @Command("heal")
    @CommandPermission("radium.command.heal")
    @Description("Heals self or others")
    fun heal(actor: VelocityCommandActor, targetName: String? = null) {
        radium.scope.launch {
            try {
                if (targetName != null) {
                    // Heal another player
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
                    
                    // Send heal request to target's server
                    val message = "heal:${targetPlayer.uniqueId}"
                    targetServer.sendPluginMessage(HEAL_CHANNEL, message.toByteArray())
                    
                    actor.reply(Component.text("Healed ${targetPlayer.username}!", NamedTextColor.GREEN))
                    targetPlayer.sendMessage(Component.text("You have been healed by ${actor.name()}!", NamedTextColor.GREEN))
                    
                } else {
                    // Heal self
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
                    
                    // Send heal request to current server
                    val message = "heal:${player?.uniqueId}"
                    currentServer.sendPluginMessage(HEAL_CHANNEL, message.toByteArray())
                    
                    actor.reply(Component.text("You have been healed!", NamedTextColor.GREEN))
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to heal player: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing heal command", e)
            }
        }
    }
}
