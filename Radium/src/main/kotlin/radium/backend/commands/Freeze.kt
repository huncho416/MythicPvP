package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import radium.backend.freeze.FreezeManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command to freeze players
 */
class Freeze(private val radium: Radium) {
    
    @Command("freeze")
    @CommandPermission("radium.command.freeze")
    @Description("Freeze a player")
    fun freeze(actor: VelocityCommandActor, targetName: String) {
        radium.scope.launch {
            try {
                val targetPlayer = radium.server.getPlayer(targetName).orElse(null)
                if (targetPlayer == null) {
                    actor.reply(Component.text("Player '$targetName' not found!", NamedTextColor.RED))
                    return@launch
                }
                
                // Check if player is already frozen
                if (FreezeManager.isFrozen(targetPlayer.uniqueId)) {
                    actor.reply(Component.text("$targetName is already frozen!", NamedTextColor.RED))
                    return@launch
                }
                
                val success = radium.freezeManager.freezePlayer(
                    targetPlayer.uniqueId,
                    targetPlayer.username,
                    actor.name()
                )
                
                if (success) {
                    actor.reply(Component.text("Successfully froze $targetName!", NamedTextColor.GREEN))
                } else {
                    actor.reply(Component.text("Failed to freeze $targetName!", NamedTextColor.RED))
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Error freezing player: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error in freeze command", e)
            }
        }
    }
}