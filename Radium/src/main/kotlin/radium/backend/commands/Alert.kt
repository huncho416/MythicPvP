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
 * Command to send global alert messages
 */
class Alert(private val radium: Radium) {
    
    @Command("alert")
    @CommandPermission("radium.command.alert")
    @Description("Sends global alert messages")
    fun alert(actor: VelocityCommandActor, message: String) {
        try {
            // Format the alert message
            val formattedMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&c&l[ALERT] &f$message"
            )
            
            // Send alert to all players across all servers
            radium.server.allPlayers.forEach { player ->
                player.sendMessage(formattedMessage)
            }
            
            // Log the alert
            radium.logger.info("${actor.name()} sent global alert: $message")
            
            // Confirm to sender
            actor.reply(Component.text("Alert sent to all players!", NamedTextColor.GREEN))
            
        } catch (e: Exception) {
            actor.reply(Component.text("Failed to send alert: ${e.message}", NamedTextColor.RED))
            radium.logger.error("Error processing alert command", e)
        }
    }
}
