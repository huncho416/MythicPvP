package huncho.main.lobby.features.panic

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.timer.TaskSchedule
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for the panic system
 * Handles panic mode activation, cooldowns, and automatic expiration
 */
class PanicManager(private val plugin: LobbyPlugin) {
    
    private val logger: Logger = LoggerFactory.getLogger(PanicManager::class.java)
    private val panicPlayers = ConcurrentHashMap<UUID, PanicData>()
    private val panicCooldowns = ConcurrentHashMap<UUID, Long>()
    private val processingOperations = Collections.synchronizedSet(mutableSetOf<String>())
    
    // Configuration - these should be in config files
    private val panicDurationMinutes = 10L
    private val panicCooldownMinutes = 15L
    
    data class PanicData(
        val activationTime: Long = System.currentTimeMillis(),
        val activatedBy: String,
        val reason: String? = null
    )
    
    fun initialize() {
        logger.info("Initializing Panic Manager...")
        
        try {
            setupEventListeners()
            startPanicExpirationTask()
            // Panic Manager initialized
        } catch (e: Exception) {
            logger.error("Failed to initialize Panic Manager", e)
        }
    }
    
    private fun setupEventListeners() {
        val eventHandler = MinecraftServer.getGlobalEventHandler()
        
        // Prevent movement when in panic mode (like freeze)
        eventHandler.addListener(EventListener.of(PlayerMoveEvent::class.java) { event ->
            if (isInPanicMode(event.player.uuid)) {
                event.isCancelled = true
                // Don't spam messages like freeze does - just prevent movement
            }
        })
        
        // Prevent block breaking/placing when in panic mode
        eventHandler.addListener(EventListener.of(PlayerBlockBreakEvent::class.java) { event ->
            if (isInPanicMode(event.player.uuid)) {
                event.isCancelled = true
            }
        })
        
        eventHandler.addListener(EventListener.of(PlayerBlockPlaceEvent::class.java) { event ->
            if (isInPanicMode(event.player.uuid)) {
                event.isCancelled = true
            }
        })
        
        // Prevent inventory interactions when in panic mode
        eventHandler.addListener(EventListener.of(InventoryPreClickEvent::class.java) { event ->
            if (event.player != null && isInPanicMode(event.player!!.uuid)) {
                event.isCancelled = true
            }
        })
        
        // Prevent item dropping when in panic mode
        eventHandler.addListener(EventListener.of(ItemDropEvent::class.java) { event ->
            if (isInPanicMode(event.player.uuid)) {
                event.isCancelled = true
            }
        })
        
        // Cleanup on disconnect
        eventHandler.addListener(EventListener.of(PlayerDisconnectEvent::class.java) { event ->
            val removedData = panicPlayers.remove(event.player.uuid)
            if (removedData != null) {
                logger.info("DEBUG: Removed panic mode for disconnecting player ${event.player.username}")
            }
        })
    }
    
    /**
     * Start panic mode expiration task
     */
    private fun startPanicExpirationTask() {
        MinecraftServer.getSchedulerManager().submitTask {
            checkExpiredPanicModes()
            TaskSchedule.tick(20 * 60) // Check every minute
        }
    }
    
    /**
     * Check for expired panic modes and remove them
     */
    private fun checkExpiredPanicModes() {
        // Check for expired panic modes
        
        val currentTime = System.currentTimeMillis()
        val expiredPlayers = mutableListOf<UUID>()
        
        panicPlayers.forEach { (uuid, panicData) ->
            val elapsedMinutes = (currentTime - panicData.activationTime) / 60000
            logger.info("DEBUG: Player ${panicData.activatedBy} has been in panic for $elapsedMinutes minutes")
            
            if (elapsedMinutes >= panicDurationMinutes) {
                logger.info("DEBUG: Player ${panicData.activatedBy} panic mode expired ($elapsedMinutes >= $panicDurationMinutes)")
                expiredPlayers.add(uuid)
            }
        }
        
        // Remove expired panic players
        
        expiredPlayers.forEach { uuid ->
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player != null) {
                logger.info("DEBUG: Removing expired panic mode for online player ${player.username}")
                removePanicMode(player, "Automatic expiration after $panicDurationMinutes minutes")
            } else {
                logger.info("DEBUG: Removing expired panic mode for offline player")
                panicPlayers.remove(uuid)
            }
        }
    }
    
    /**
     * Enter panic mode
     */
    suspend fun enterPanicMode(player: Player): Result<Unit> {
        return try {
            // Prevent concurrent panic operations
            val playerId = player.uuid.toString()
            if (processingOperations.contains(playerId)) {
                return Result.failure(Exception("A panic operation is already in progress!"))
            }
            
            processingOperations.add(playerId)
            
            try {
                val currentTime = System.currentTimeMillis()
                
                // Check cooldown
                val cooldownExpiry = panicCooldowns[player.uuid]
                if (cooldownExpiry != null && currentTime < cooldownExpiry) {
                    val remainingMinutes = (cooldownExpiry - currentTime) / 60000
                    return Result.failure(Exception("You must wait $remainingMinutes more minutes before using panic mode again!"))
                }
                
                // Check if already in panic mode
                if (isInPanicMode(player.uuid)) {
                    return Result.failure(Exception("You are already in panic mode!"))
                }
                
                // Add to panic mode
                val panicData = PanicData(activatedBy = player.username)
                panicPlayers[player.uuid] = panicData
                
                // Debug logging
                logger.info("DEBUG: Added ${player.username} (${player.uuid}) to panic mode. Total panic players: ${panicPlayers.size}")
                logger.info("DEBUG: Panic data stored: activatedBy=${panicData.activatedBy}, time=${panicData.activationTime}")
                
                // Verify storage immediately
                val storedData = panicPlayers[player.uuid]
                if (storedData != null) {
                    logger.info("DEBUG: Verification successful - player is in panic map")
                } else {
                    logger.error("DEBUG: CRITICAL - player was NOT stored in panic map!")
                }
                
                // Alert all staff network-wide
                GlobalScope.launch {
                    try {
                        // Get player's rank information for proper formatting
                        val playerData = plugin.radiumIntegration.getPlayerData(player.uuid).await()
                        val playerPrefix = playerData?.rank?.prefix ?: ""
                        val playerColor = if (playerPrefix.isNotEmpty() && playerPrefix.startsWith("&")) {
                            playerPrefix.substring(0, 2) // Extract first color code like "&4"
                        } else {
                            "&f" // Default to white if no color found
                        }
                        
                        val serverName = plugin.configManager.getString(plugin.configManager.mainConfig, "server.name", "Lobby")
                        val panicMessage = "&c&l[PANIC] $playerPrefix$playerColor${player.username} &7has activated panic mode! Server: &c$serverName"
                        
                        plugin.radiumIntegration.broadcastStaffMessage(panicMessage)
                            .thenAccept { success ->
                                if (success) {
                                    logger.info("Successfully broadcast panic notification for ${player.username}")
                                } else {
                                    logger.warn("Failed to broadcast panic notification to staff for ${player.username}")
                                }
                            }
                            .exceptionally { throwable ->
                                logger.error("Exception while broadcasting panic notification for ${player.username}", throwable)
                                null
                            }
                    } catch (e: Exception) {
                        logger.error("Error broadcasting panic notification for ${player.username}", e)
                    }
                }
                
                logger.info("${player.username} activated panic mode")
                Result.success(Unit)
            } finally {
                processingOperations.remove(playerId)
            }
        } catch (e: Exception) {
            logger.error("Error entering panic mode", e)
            Result.failure(e)
        }
    }
    
    /**
     * Exit panic mode (staff command)
     */
    suspend fun exitPanicMode(staff: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else if (!isInPanicMode(target.uuid)) {
                Result.failure(Exception("$targetName is not in panic mode!"))
            } else {
                removePanicMode(target, "Removed by ${staff.username}")
                
                // Notify staff
                GlobalScope.launch {
                    try {
                        val unpanicMessage = "§a§l[UNPANIC] §f${staff.username} §7has removed panic mode from §a${target.username}"
                        plugin.radiumIntegration.broadcastStaffMessage(unpanicMessage)
                            .thenAccept { success ->
                                if (success) {
                                    logger.info("Successfully broadcast unpanic notification for ${target.username}")
                                } else {
                                    logger.warn("Failed to broadcast unpanic notification to staff for ${target.username}")
                                }
                            }
                            .exceptionally { throwable ->
                                logger.error("Exception while broadcasting unpanic notification for ${target.username}", throwable)
                                null
                            }
                    } catch (e: Exception) {
                        logger.error("Error broadcasting unpanic notification for ${target.username}", e)
                    }
                }
                
                logger.info("${staff.username} removed panic mode from ${target.username}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error("Error exiting panic mode", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove panic mode and set cooldown
     */
    private fun removePanicMode(player: Player, reason: String) {
        logger.info("DEBUG: removePanicMode called for ${player.username} - reason: $reason")
        logger.info("DEBUG: Current panic players before removal: ${panicPlayers.size}")
        
        val removed = panicPlayers.remove(player.uuid)
        if (removed != null) {
            logger.info("DEBUG: Successfully removed ${player.username} from panic mode")
        } else {
            logger.warn("DEBUG: Attempted to remove ${player.username} but they were not in panic mode!")
        }
        
        logger.info("DEBUG: Current panic players after removal: ${panicPlayers.size}")
        
        // Set cooldown
        val cooldownExpiry = System.currentTimeMillis() + (panicCooldownMinutes * 60 * 1000)
        panicCooldowns[player.uuid] = cooldownExpiry
        
        // Send removal message
        player.sendMessage("§a§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
        player.sendMessage("§a§lPanic mode has been removed!")
        player.sendMessage("§7Reason: §f$reason")
        player.sendMessage("§7You may now move and interact normally.")
        player.sendMessage("§7")
        player.sendMessage("§7Panic mode cooldown: §c$panicCooldownMinutes minutes")
        player.sendMessage("§a§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
    }
    
    /**
     * Check if a player is in panic mode
     */
    fun isInPanicMode(playerUuid: UUID): Boolean {
        return panicPlayers.containsKey(playerUuid)
    }
    
    /**
     * Get panic data for a player
     */
    fun getPanicData(playerUuid: UUID): PanicData? {
        return panicPlayers[playerUuid]
    }
    
    /**
     * Get all players in panic mode
     */
    fun getPanicPlayers(): Map<UUID, PanicData> {
        // Debug logging to see current state
        logger.info("DEBUG: getPanicPlayers() called - current size: ${panicPlayers.size}")
        panicPlayers.forEach { (uuid, data) ->
            logger.info("DEBUG: Found panic player: ${data.activatedBy} (${uuid})")
        }
        return panicPlayers.toMap()
    }
    
    /**
     * Check cooldown for a player
     */
    fun getRemainingCooldown(playerUuid: UUID): Long {
        val cooldownExpiry = panicCooldowns[playerUuid] ?: return 0
        val remaining = cooldownExpiry - System.currentTimeMillis()
        return if (remaining > 0) remaining / 60000 else 0 // Return minutes
    }
    
    /**
     * Shutdown the panic manager
     */
    fun shutdown() {
        try {
            panicPlayers.clear()
            panicCooldowns.clear()
            logger.info("Panic Manager shut down successfully")
        } catch (e: Exception) {
            logger.error("Error during panic manager shutdown", e)
        }
    }
}
