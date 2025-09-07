package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Command("give")
class Give(private val radium: Radium) {

    @CommandPermission("radium.give")
    fun give(
        actor: Player,
        @Named("target") target: String,
        @Named("item") item: String,
        @Named("amount") @Optional amount: Int? = 1
    ) {
        GlobalScope.launch {
            try {
                val targetPlayer = radium.server.getPlayer(target).orElse(null)
                
                if (targetPlayer != null) {
                    // Forward command to target's current server
                    val currentServer = targetPlayer.currentServer.orElse(null)
                    if (currentServer != null) {
                        // Send plugin message to backend server to give item
                        val data = mapOf(
                            "action" to "give",
                            "executor" to actor.uniqueId.toString(),
                            "target" to targetPlayer.uniqueId.toString(),
                            "item" to item,
                            "amount" to (amount ?: 1)
                        )
                        
                        val json = radium.objectMapper.writeValueAsString(data)
                        val channel = "radium:give"
                        
                        currentServer.server.sendPluginMessage(
                            MinecraftChannelIdentifier.from(channel),
                            json.toByteArray()
                        )
                        
                        actor.sendMessage(
                            radium.yamlFactory.getMessageComponent(
                                "commands.give.success",
                                "target" to targetPlayer.username,
                                "item" to item,
                                "amount" to (amount ?: 1).toString()
                            )
                        )
                    } else {
                        actor.sendMessage(
                            radium.yamlFactory.getMessageComponent(
                                "commands.give.not_connected",
                                "target" to target
                            )
                        )
                    }
                } else {
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            "commands.give.player_not_found",
                            "target" to target
                        )
                    )
                }
            } catch (e: Exception) {
                radium.logger.error("Error executing give command", e)
                actor.sendMessage(
                    radium.yamlFactory.getMessageComponent("general.unknown_error")
                )
            }
        }
    }

    @Subcommand("help")
    @CommandPermission("radium.give")
    fun help(actor: CommandSource) {
        actor.sendMessage(
            radium.yamlFactory.getMessageComponent("commands.give.usage.main")
        )
    }
}
