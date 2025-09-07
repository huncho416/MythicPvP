package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Command("teleportworld", "tpworld")
class TeleportWorld(private val radium: Radium) {

    @CommandPermission("radium.teleport.world")
    fun teleportWorld(
        actor: Player,
        @Named("world") world: String
    ) {
        GlobalScope.launch {
            try {
                val currentServer = actor.currentServer.orElse(null)
                if (currentServer != null) {
                    // Send plugin message to backend server to teleport to world
                    val data = mapOf(
                        "action" to "teleportworld",
                        "player" to actor.uniqueId.toString(),
                        "world" to world
                    )
                    
                    val json = radium.objectMapper.writeValueAsString(data)
                    val channel = "radium:teleport"
                    
                    currentServer.server.sendPluginMessage(
                        MinecraftChannelIdentifier.from(channel),
                        json.toByteArray()
                    )
                    
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            "commands.teleport.world",
                            "world" to world
                        )
                    )
                } else {
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent("commands.teleport.not_connected")
                    )
                }
            } catch (e: Exception) {
                radium.logger.error("Error executing teleportworld command", e)
                actor.sendMessage(
                    radium.yamlFactory.getMessageComponent("general.unknown_error")
                )
            }
        }
    }

    @Subcommand("help")
    @CommandPermission("radium.teleport.world")
    fun help(actor: CommandSource) {
        actor.sendMessage(
            radium.yamlFactory.getMessageComponent("commands.teleport.usage.world")
        )
    }
}
