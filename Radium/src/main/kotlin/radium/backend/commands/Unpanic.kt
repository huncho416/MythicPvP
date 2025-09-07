package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import radium.backend.panic.PanicManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command for staff to remove panic mode from players
 */
class Unpanic(private val radium: Radium) {
    
    @Command("unpanic")
    @CommandPermission("radium.command.unpanic")
    @Description("Remove panic mode from a player")
    fun unpanic(actor: VelocityCommandActor, targetName: String) {
        radium.scope.launch {
            try {
                val targetPlayer = radium.server.getPlayer(targetName).orElse(null)
                if (targetPlayer == null) {
                    actor.reply(Component.text("Player '$targetName' not found!", NamedTextColor.RED))
                    return@launch
                }
                
                // Check if player is in panic mode
                if (!PanicManager.isInPanic(targetPlayer.uniqueId)) {
                    actor.reply(Component.text("$targetName is not in panic mode!", NamedTextColor.RED))
                    return@launch
                }
                
                val success = radium.panicManager.removePanic(
                    targetPlayer.uniqueId,
                    targetPlayer.username,
                    actor.name()
                )
                
                if (success) {
                    actor.reply(Component.text("Successfully removed panic mode from $targetName!", NamedTextColor.GREEN))
                } else {
                    actor.reply(Component.text("Failed to remove panic mode from $targetName!", NamedTextColor.RED))
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Error removing panic mode: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error in unpanic command", e)
            }
        }
    }
}
