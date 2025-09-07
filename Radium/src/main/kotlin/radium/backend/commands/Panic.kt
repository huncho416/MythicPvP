package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import radium.backend.panic.PanicResult
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command for players to enter panic mode
 */
class Panic(private val radium: Radium) {
    
    @Command("panic")
    @CommandPermission("radium.command.panic")
    @Description("Enter panic mode during emergencies")
    fun panic(actor: VelocityCommandActor) {
        if (!actor.isPlayer) {
            actor.reply(Component.text("Only players can use panic mode!", NamedTextColor.RED))
            return
        }
        
        val player = actor.asPlayer()
        
        radium.scope.launch {
            try {
                val result = radium.panicManager.activatePanic(player?.uniqueId ?: return@launch, player.username)
                
                when (result) {
                    is PanicResult.Success -> {
                        actor.reply(Component.text("Panic mode activated! Staff have been alerted.", NamedTextColor.GREEN))
                    }
                    
                    is PanicResult.AlreadyInPanic -> {
                        actor.reply(Component.text("You are already in panic mode!", NamedTextColor.RED))
                    }
                    
                    is PanicResult.OnCooldown -> {
                        val timeText = if (result.minutes > 0) {
                            "${result.minutes}m ${result.seconds}s"
                        } else {
                            "${result.seconds}s"
                        }
                        actor.reply(Component.text("You must wait $timeText before using panic again!", NamedTextColor.RED))
                    }
                    
                    is PanicResult.Error -> {
                        actor.reply(Component.text("Failed to activate panic mode: ${result.message}", NamedTextColor.RED))
                    }
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Error activating panic mode: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error in panic command", e)
            }
        }
    }
}
