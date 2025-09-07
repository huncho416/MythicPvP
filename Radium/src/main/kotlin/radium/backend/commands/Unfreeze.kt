package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import radium.backend.freeze.FreezeManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command to unfreeze players
 */
class Unfreeze(private val radium: Radium) {
    
    @Command("unfreeze")
    @CommandPermission("radium.command.freeze")
    @Description("Unfreeze a player")
    fun unfreeze(actor: VelocityCommandActor, targetName: String) {
        radium.scope.launch {
            try {
                val targetPlayer = radium.server.getPlayer(targetName).orElse(null)
                if (targetPlayer == null) {
                    actor.reply(Component.text("Player '$targetName' not found!", NamedTextColor.RED))
                    return@launch
                }
                
                // Check if player is frozen
                if (!FreezeManager.isFrozen(targetPlayer.uniqueId)) {
                    actor.reply(Component.text("$targetName is not frozen!", NamedTextColor.RED))
                    return@launch
                }
                
                val success = radium.freezeManager.unfreezePlayer(
                    targetPlayer.uniqueId,
                    targetPlayer.username,
                    actor.name()
                )
                
                if (success) {
                    actor.reply(Component.text("Successfully unfroze $targetName!", NamedTextColor.GREEN))
                } else {
                    actor.reply(Component.text("Failed to unfreeze $targetName!", NamedTextColor.RED))
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Error unfreezing player: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error in unfreeze command", e)
            }
        }
    }
}
