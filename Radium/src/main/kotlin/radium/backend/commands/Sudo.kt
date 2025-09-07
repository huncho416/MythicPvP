package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Command("sudo")
class Sudo(private val radium: Radium) {

    @CommandPermission("radium.sudo")
    fun sudo(
        actor: Player,
        @Named("target") target: String,
        @Named("command") command: String
    ) {
        GlobalScope.launch {
            try {
                val targetPlayer = radium.server.getPlayer(target).orElse(null)
                
                if (targetPlayer != null) {
                    val currentServer = targetPlayer.currentServer.orElse(null)
                    if (currentServer != null) {
                        // Send plugin message to backend server to execute command as player
                        val data = mapOf(
                            "action" to "sudo",
                            "executor" to actor.uniqueId.toString(),
                            "target" to targetPlayer.uniqueId.toString(),
                            "command" to command
                        )
                        
                        val json = radium.objectMapper.writeValueAsString(data)
                        val channel = "radium:sudo"
                        
                        currentServer.server.sendPluginMessage(
                            MinecraftChannelIdentifier.from(channel),
                            json.toByteArray()
                        )
                        
                        actor.sendMessage(
                            radium.yamlFactory.getMessageComponent(
                                "commands.sudo.executed",
                                "target" to targetPlayer.username,
                                "command" to command
                            )
                        )
                        
                        // Log the sudo command for security
                        radium.logger.info("${actor.username} executed sudo on ${targetPlayer.username}: $command")
                    } else {
                        actor.sendMessage(
                            radium.yamlFactory.getMessageComponent(
                                "commands.sudo.not_connected",
                                "target" to target
                            )
                        )
                    }
                } else {
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            "commands.sudo.player_not_found",
                            "target" to target
                        )
                    )
                }
            } catch (e: Exception) {
                radium.logger.error("Error executing sudo command", e)
                actor.sendMessage(
                    radium.yamlFactory.getMessageComponent("general.unknown_error")
                )
            }
        }
    }

    @Subcommand("help")
    @CommandPermission("radium.sudo")
    fun help(actor: CommandSource) {
        actor.sendMessage(
            radium.yamlFactory.getMessageComponent("commands.sudo.usage.main")
        )
    }
}
