package huncho.main.lobby.listeners

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.GameMode
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerPluginMessageEvent
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.*

/**
 * Handles plugin messages from Radium for gamemode changes
 */
class GamemodePluginMessageListener(private val plugin: LobbyPlugin) : EventListener<PlayerPluginMessageEvent> {
    
    override fun eventType(): Class<PlayerPluginMessageEvent> = PlayerPluginMessageEvent::class.java
    
    override fun run(event: PlayerPluginMessageEvent): EventListener.Result {
        // Handle plugin messages from Radium
        if (event.identifier == "radium:gamemode") {
            try {
                val input = ByteArrayInputStream(event.message)
                val dataInput = DataInputStream(input)
                
                val action = dataInput.readUTF()
                
                if (action == "SET_GAMEMODE") {
                    val targetUUID = UUID.fromString(dataInput.readUTF())
                    val targetName = dataInput.readUTF()
                    val gamemodeName = dataInput.readUTF()
                    val executor = dataInput.readUTF()
                    
                    // Find the target player
                    val targetPlayer: Player? = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == targetUUID }
                    if (targetPlayer != null) {
                        val gameMode: GameMode? = parseGameMode(gamemodeName)
                        if (gameMode != null) {
                            targetPlayer.gameMode = gameMode
                            plugin.logger.info("Set ${targetPlayer.username}'s gamemode to $gamemodeName (requested by $executor)")
                        } else {
                            plugin.logger.warn("Invalid gamemode '$gamemodeName' requested for ${targetPlayer.username}")
                        }
                    } else {
                        plugin.logger.debug("Target player $targetName ($targetUUID) not found on this server")
                    }
                }
                
            } catch (e: Exception) {
                plugin.logger.error("Error handling gamemode plugin message: ${e.message}", e)
            }
        }
        
        return EventListener.Result.SUCCESS
    }
    
    /**
     * Parse gamemode string to Minestom GameMode enum
     */
    private fun parseGameMode(gamemodeName: String): GameMode? {
        return when (gamemodeName.lowercase()) {
            "survival", "0" -> GameMode.SURVIVAL
            "creative", "1" -> GameMode.CREATIVE
            "adventure", "2" -> GameMode.ADVENTURE
            "spectator", "3" -> GameMode.SPECTATOR
            else -> null
        }
    }
}
