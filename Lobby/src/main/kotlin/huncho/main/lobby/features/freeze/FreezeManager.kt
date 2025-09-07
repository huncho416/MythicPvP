package huncho.main.lobby.features.freeze

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerCommandEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for the freeze system
 * Handles freezing/unfreezing players and preventing actions while frozen
 */
class FreezeManager(private val plugin: LobbyPlugin) {
    
    private val logger: Logger = LoggerFactory.getLogger(FreezeManager::class.java)
    private val frozenPlayers = ConcurrentHashMap<UUID, FreezeData>()
    private val processingOperations = Collections.synchronizedSet(mutableSetOf<String>())
    
    // Configurable commands that frozen players can still execute
    private val allowedCommands = setOf(
        "msg", "message", "tell", "w", "whisper", "r", "reply", 
        "helpop", "report", "panic", "discord", "teamspeak", "ts"
    )
    
    data class FreezeData(
        val freezerName: String,
        val freezeTime: Long = System.currentTimeMillis(),
        val reason: String? = null
    )
    
    fun initialize() {
        logger.info("Initializing Freeze Manager...")
        
        try {
            setupEventListeners()
            // Freeze Manager initialized
        } catch (e: Exception) {
            logger.error("Failed to initialize Freeze Manager", e)
        }
    }
    
    private fun setupEventListeners() {
        val eventHandler = MinecraftServer.getGlobalEventHandler()
        
        // Prevent movement when frozen
        eventHandler.addListener(EventListener.of(PlayerMoveEvent::class.java) { event ->
            if (isFrozen(event.player.uuid)) {
                event.isCancelled = true
                // Show freeze message periodically
                showFreezeMessage(event.player)
            }
        })
        
        // Prevent block breaking/placing when frozen
        eventHandler.addListener(EventListener.of(PlayerBlockBreakEvent::class.java) { event ->
            if (isFrozen(event.player.uuid)) {
                event.isCancelled = true
                showFreezeMessage(event.player)
            }
        })
        
        eventHandler.addListener(EventListener.of(PlayerBlockPlaceEvent::class.java) { event ->
            if (isFrozen(event.player.uuid)) {
                event.isCancelled = true
                showFreezeMessage(event.player)
            }
        })
        
        // Prevent inventory interactions when frozen
        eventHandler.addListener(EventListener.of(InventoryPreClickEvent::class.java) { event ->
            if (event.player != null && isFrozen(event.player!!.uuid)) {
                event.isCancelled = true
                showFreezeMessage(event.player!!)
            }
        })
        
        // Prevent item dropping when frozen
        eventHandler.addListener(EventListener.of(ItemDropEvent::class.java) { event ->
            if (isFrozen(event.player.uuid)) {
                event.isCancelled = true
                showFreezeMessage(event.player)
            }
        })
        
        // Filter commands when frozen
        eventHandler.addListener(EventListener.of(PlayerCommandEvent::class.java) { event ->
            if (isFrozen(event.player.uuid)) {
                val command = event.command.split(" ")[0].lowercase()
                if (!allowedCommands.contains(command)) {
                    event.isCancelled = true
                    showFreezeMessage(event.player)
                }
            }
        })
        
        // Cleanup on disconnect
        eventHandler.addListener(EventListener.of(PlayerDisconnectEvent::class.java) { event ->
            frozenPlayers.remove(event.player.uuid)
        })
    }
    
    /**
     * Freeze a player
     */
    suspend fun freezePlayer(freezer: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else {
                // Prevent concurrent freeze/unfreeze operations
                val targetId = target.uuid.toString()
                if (processingOperations.contains(targetId)) {
                    return Result.failure(Exception("A freeze operation is already in progress for $targetName!"))
                }
                
                processingOperations.add(targetId)
                
                try {
                    // Check if target has bypass permission
                    val hasBypassPermission: Boolean = withContext(Dispatchers.IO) {
                        plugin.radiumIntegration.hasPermission(target.uuid, "hub.freeze.bypass").await()
                    }
                    if (hasBypassPermission) {
                        Result.failure(Exception("You cannot freeze $targetName!"))
                    } else if (isFrozen(target.uuid)) {
                        Result.failure(Exception("$targetName is already frozen!"))
                    } else {
                        // Add to frozen players
                        frozenPlayers[target.uuid] = FreezeData(freezer.username)
                        
                        // Send freeze messages
                        showFreezeMessage(target)
                        
                        // Notify staff through Radium - use format from lang.yml
                        GlobalScope.launch {
                            try {
                                val freezeMessage = "&b[SC] &e${target.username} &7has been &cfrozen &7by &e${freezer.username}"
                                plugin.radiumIntegration.broadcastStaffMessage(freezeMessage)
                                    .thenAccept { success ->
                                        if (success) {
                                            logger.info("Successfully broadcast freeze notification for ${target.username}")
                                        } else {
                                            logger.warn("Failed to broadcast freeze notification to staff for ${target.username}")
                                        }
                                    }
                                    .exceptionally { throwable ->
                                        logger.error("Exception while broadcasting freeze notification for ${target.username}", throwable)
                                        null
                                    }
                            } catch (e: Exception) {
                                logger.error("Error broadcasting freeze notification for ${target.username}", e)
                            }
                        }
                        
                        logger.info("${freezer.username} froze ${target.username}")
                        Result.success(Unit)
                    }
                } finally {
                    processingOperations.remove(targetId)
                }
            }
        } catch (e: Exception) {
            logger.error("Error freezing player", e)
            Result.failure(e)
        }
    }
    
    /**
     * Unfreeze a player
     */
    suspend fun unfreezePlayer(unfreezer: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else {
                // Prevent concurrent freeze/unfreeze operations
                val targetId = target.uuid.toString()
                if (processingOperations.contains(targetId)) {
                    return Result.failure(Exception("A freeze operation is already in progress for $targetName!"))
                }
                
                if (!isFrozen(target.uuid)) {
                    return Result.failure(Exception("$targetName is not frozen!"))
                }
                
                processingOperations.add(targetId)
                
                try {
                    // Remove from frozen players
                    frozenPlayers.remove(target.uuid)
                    
                    // Send unfreeze messages
                    target.sendMessage("§a§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
                    target.sendMessage("§a§lYou have been unfrozen!")
                    target.sendMessage("§7You may now move and interact normally.")
                    target.sendMessage("§a§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
                    
                    // Notify staff through Radium - use format from lang.yml
                    GlobalScope.launch {
                        try {
                            val unfreezeMessage = "&b[SC] &e${target.username} &7has been &aunfrozen &7by &e${unfreezer.username}"
                            plugin.radiumIntegration.broadcastStaffMessage(unfreezeMessage)
                                .thenAccept { success ->
                                    if (success) {
                                        logger.info("Successfully broadcast unfreeze notification for ${target.username}")
                                    } else {
                                        logger.warn("Failed to broadcast unfreeze notification to staff for ${target.username}")
                                    }
                                }
                                .exceptionally { throwable ->
                                    logger.error("Exception while broadcasting unfreeze notification for ${target.username}", throwable)
                                    null
                                }
                        } catch (e: Exception) {
                            logger.error("Error broadcasting unfreeze notification for ${target.username}", e)
                        }
                    }
                    
                    logger.info("${unfreezer.username} unfroze ${target.username}")
                    Result.success(Unit)
                } finally {
                    processingOperations.remove(targetId)
                }
            }
        } catch (e: Exception) {
            logger.error("Error unfreezing player", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if a player is frozen
     */
    fun isFrozen(playerUuid: UUID): Boolean {
        return frozenPlayers.containsKey(playerUuid)
    }
    
    /**
     * Get freeze data for a player
     */
    fun getFreezeData(playerUuid: UUID): FreezeData? {
        return frozenPlayers[playerUuid]
    }
    
    /**
     * Show freeze message to player
     */
    private fun showFreezeMessage(player: Player) {
        val freezeData = frozenPlayers[player.uuid] ?: return
        
        player.sendMessage("§c§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
        player.sendMessage("§c§lYOU HAVE BEEN FROZEN!")
        player.sendMessage("§7")
        player.sendMessage("§7You have been frozen by: §c${freezeData.freezerName}")
        player.sendMessage("§7Please join our TeamSpeak: §ets.mythicpvp.net")
        player.sendMessage("§7")
        player.sendMessage("§c§lDo not log out or you will be banned!")
        player.sendMessage("§7")
        player.sendMessage("§7If you think this is a mistake, please contact staff")
        player.sendMessage("§7through our Discord or TeamSpeak server.")
        player.sendMessage("§c§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
    }
    
    /**
     * Get all frozen players
     */
    fun getFrozenPlayers(): Map<UUID, FreezeData> {
        return frozenPlayers.toMap()
    }
    
    /**
     * Shutdown the freeze manager
     */
    fun shutdown() {
        try {
            frozenPlayers.clear()
            logger.info("Freeze Manager shut down successfully")
        } catch (e: Exception) {
            logger.error("Error during freeze manager shutdown", e)
        }
    }
}
