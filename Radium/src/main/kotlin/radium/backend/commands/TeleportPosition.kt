package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Command("teleportposition", "tppos")
class TeleportPosition(private val radium: Radium) {

    @CommandPermission("radium.teleport.position")
    fun teleportPosition(
        actor: Player,
        @Named("x") x: Double,
        @Named("y") y: Double,
        @Named("z") z: Double
    ) {
        GlobalScope.launch {
            try {
                // Forward command to actor's current server
                val currentServer = actor.currentServer.orElse(null)
                if (currentServer != null) {
                    // Send plugin message to backend server to handle teleportation
                    val data = mapOf(
                        "action" to "teleport_position",
                        "player" to actor.uniqueId.toString(),
                        "x" to x,
                        "y" to y,
                        "z" to z
                    )
                    
                    val json = radium.objectMapper.writeValueAsString(data)
                    val channel = "radium:teleport"
                    
                    currentServer.server.sendPluginMessage(
                        MinecraftChannelIdentifier.from(channel),
                        json.toByteArray()
                    )
                    
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            "commands.teleport.position_success",
                            "x" to x.toString(),
                            "y" to y.toString(),
                            "z" to z.toString()
                        )
                    )
                } else {
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent("commands.teleport.not_connected")
                    )
                }
            } catch (e: Exception) {
                radium.logger.error("Error executing teleport position command", e)
                actor.sendMessage(
                    radium.yamlFactory.getMessageComponent("general.unknown_error")
                )
            }
        }
    }

    @Subcommand("help")
    @CommandPermission("radium.teleport.position")
    fun help(actor: CommandSource) {
        actor.sendMessage(
            radium.yamlFactory.getMessageComponent("commands.teleport.usage.position")
        )
    }
}
