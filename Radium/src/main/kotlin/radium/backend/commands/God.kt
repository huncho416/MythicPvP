package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Command("god")
class God(private val radium: Radium) {

    @CommandPermission("radium.god")
    fun god(
        actor: Player,
        @Named("target") @Optional target: String?
    ) {
        GlobalScope.launch {
            try {
                val targetPlayer = if (target != null) {
                    radium.server.getPlayer(target).orElse(null)
                } else {
                    actor
                }
                
                if (targetPlayer != null) {
                    val currentServer = targetPlayer.currentServer.orElse(null)
                    if (currentServer != null) {
                        // Send plugin message to backend server to toggle god mode
                        val data = mapOf(
                            "action" to "god",
                            "executor" to actor.uniqueId.toString(),
                            "target" to targetPlayer.uniqueId.toString()
                        )
                        
                        val json = radium.objectMapper.writeValueAsString(data)
                        val channel = "radium:god"
                        
                        currentServer.server.sendPluginMessage(
                            MinecraftChannelIdentifier.from(channel),
                            json.toByteArray()
                        )
                        
                        if (targetPlayer == actor) {
                            actor.sendMessage(
                                radium.yamlFactory.getMessageComponent("commands.god.toggled_self")
                            )
                        } else {
                            actor.sendMessage(
                                radium.yamlFactory.getMessageComponent(
                                    "commands.god.toggled_other",
                                    "target" to targetPlayer.username
                                )
                            )
                        }
                    } else {
                        actor.sendMessage(
                            radium.yamlFactory.getMessageComponent(
                                "commands.god.not_connected",
                                "target" to (target ?: "You")
                            )
                        )
                    }
                } else {
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            "commands.god.player_not_found",
                            "target" to target!!
                        )
                    )
                }
            } catch (e: Exception) {
                radium.logger.error("Error executing god command", e)
                actor.sendMessage(
                    radium.yamlFactory.getMessageComponent("general.unknown_error")
                )
            }
        }
    }

    @Subcommand("help")
    @CommandPermission("radium.god")
    fun help(actor: CommandSource) {
        actor.sendMessage(
            radium.yamlFactory.getMessageComponent("commands.god.usage.main")
        )
    }
}
