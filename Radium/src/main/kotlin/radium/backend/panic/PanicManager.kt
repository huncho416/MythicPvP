package radium.backend.panic

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the panic system for players during emergencies
 */
class PanicManager(private val radium: Radium) {
    
    companion object {
        private val panicPlayers = ConcurrentHashMap<UUID, PanicData>()
        private val panicCooldowns = ConcurrentHashMap<UUID, Long>()
        
        // Configuration
        private const val PANIC_DURATION_MS = 10 * 60 * 1000L // 10 minutes
        private const val PANIC_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes
        
        fun isInPanic(uuid: UUID): Boolean {
            return panicPlayers.containsKey(uuid)
        }
        
        fun getPanicData(uuid: UUID): PanicData? {
            return panicPlayers[uuid]
        }
        
        fun isOnCooldown(uuid: UUID): Boolean {
            val cooldownEnd = panicCooldowns[uuid] ?: return false
            return System.currentTimeMillis() < cooldownEnd
        }
        
        fun getCooldownRemaining(uuid: UUID): Long {
            val cooldownEnd = panicCooldowns[uuid] ?: return 0L
            val remaining = cooldownEnd - System.currentTimeMillis()
            return if (remaining > 0) remaining else 0L
        }
    }
    
    /**
     * Activate panic mode for a player
     */
    fun activatePanic(playerUuid: UUID, playerName: String): PanicResult {
        return try {
            // Check cooldown
            if (isOnCooldown(playerUuid)) {
                val remainingMs = getCooldownRemaining(playerUuid)
                val remainingMinutes = (remainingMs / 60000).toInt()
                val remainingSeconds = ((remainingMs % 60000) / 1000).toInt()
                return PanicResult.OnCooldown(remainingMinutes, remainingSeconds)
            }
            
            // Check if already in panic
            if (isInPanic(playerUuid)) {
                return PanicResult.AlreadyInPanic
            }
            
            val panicData = PanicData(
                playerUuid = playerUuid,
                playerName = playerName,
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis() + PANIC_DURATION_MS
            )
            
            panicPlayers[playerUuid] = panicData
            
            // Send panic message to player
            val targetPlayer = radium.server.getPlayer(playerUuid).orElse(null)
            if (targetPlayer != null) {
                sendPanicMessage(targetPlayer)
                
                // Send panic status to their current server (freeze but allow commands)
                val currentServer = targetPlayer.currentServer.orElse(null)
                if (currentServer != null) {
                    val message = "panic:${playerUuid}:true"
                    currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:panic"), message.toByteArray())
                }
            }
            
            // Alert all staff
            alertStaff(playerName)
            
            // Schedule panic expiration
            schedulePanicExpiration(playerUuid, playerName)
            
            radium.logger.info("$playerName activated panic mode")
            PanicResult.Success
            
        } catch (e: Exception) {
            radium.logger.error("Failed to activate panic for $playerName", e)
            PanicResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Remove panic mode from a player (staff command)
     */
    fun removePanic(targetUuid: UUID, targetName: String, staffName: String): Boolean {
        return try {
            val panicData = panicPlayers.remove(targetUuid)
            if (panicData == null) {
                return false // Player wasn't in panic
            }
            
            // Send unpanic message to target player
            val targetPlayer = radium.server.getPlayer(targetUuid).orElse(null)
            if (targetPlayer != null) {
                sendUnpanicMessage(targetPlayer, staffName)
                
                // Send unpanic status to their current server
                val currentServer = targetPlayer.currentServer.orElse(null)
                if (currentServer != null) {
                    val message = "panic:${targetUuid}:false"
                    currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:panic"), message.toByteArray())
                }
            }
            
            // Set cooldown
            panicCooldowns[targetUuid] = System.currentTimeMillis() + PANIC_COOLDOWN_MS
            
            // Broadcast to staff
            broadcastPanicRemoval(targetName, staffName)
            
            radium.logger.info("$staffName removed panic mode from $targetName")
            true
            
        } catch (e: Exception) {
            radium.logger.error("Failed to remove panic from $targetName", e)
            false
        }
    }
    
    /**
     * Schedule panic expiration
     */
    private fun schedulePanicExpiration(playerUuid: UUID, playerName: String) {
        radium.scope.launch {
            delay(PANIC_DURATION_MS)
            
            // Check if player is still in panic (might have been removed by staff)
            val panicData = panicPlayers.remove(playerUuid)
            if (panicData != null) {
                // Send expiration message to player
                val targetPlayer = radium.server.getPlayer(playerUuid).orElse(null)
                if (targetPlayer != null) {
                    sendPanicExpiredMessage(targetPlayer)
                    
                    // Send unpanic status to their current server
                    val currentServer = targetPlayer.currentServer.orElse(null)
                    if (currentServer != null) {
                        val message = "panic:${playerUuid}:false"
                        currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:panic"), message.toByteArray())
                    }
                }
                
                // Set cooldown
                panicCooldowns[playerUuid] = System.currentTimeMillis() + PANIC_COOLDOWN_MS
                
                // Broadcast to staff
                broadcastPanicExpiration(playerName)
                
                radium.logger.info("Panic mode expired for $playerName")
            }
        }
    }
    
    /**
     * Send panic activation message to player
     */
    private fun sendPanicMessage(player: com.velocitypowered.api.proxy.Player) {
        radium.scope.launch {
            val messages = listOf(
                "",
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "&c&lPANIC MODE ACTIVATED",
                "",
                "&c&lYou are now in panic mode.",
                "&c&lMovement is disabled for your safety.",
                "&c&lYou can still execute commands.",
                "",
                "&c&lStaff have been alerted network-wide.",
                "&c&lJoin our TeamSpeak: &fts.mythicpvp.com",
                "",
                "&c&lPanic mode will expire in 10 minutes.",
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                ""
            )
            
            messages.forEach { line ->
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(line))
            }
        }
    }
    
    /**
     * Send unpanic message to player
     */
    private fun sendUnpanicMessage(player: com.velocitypowered.api.proxy.Player, staffName: String) {
        radium.scope.launch {
            val messages = listOf(
                "",
                "&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "&a&lPANIC MODE REMOVED",
                "",
                "&a&lYou are no longer in panic mode.",
                "&a&lYou may now move and continue playing.",
                "&a&lRemoved by: &f$staffName",
                "",
                "&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                ""
            )
            
            messages.forEach { line ->
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(line))
            }
        }
    }
    
    /**
     * Send panic expiration message to player
     */
    private fun sendPanicExpiredMessage(player: com.velocitypowered.api.proxy.Player) {
        radium.scope.launch {
            val messages = listOf(
                "",
                "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "&e&lPANIC MODE EXPIRED",
                "",
                "&e&lYour panic mode has expired.",
                "&e&lYou may now move and continue playing.",
                "",
                "&e&lYou cannot use /panic again for 15 minutes.",
                "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                ""
            )
            
            messages.forEach { line ->
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(line))
            }
        }
    }
    
    /**
     * Alert all staff about panic activation
     */
    private fun alertStaff(playerName: String) {
        radium.scope.launch {
            val alertMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&c&l[PANIC] &c$playerName has activated panic mode! &7(Join TS: ts.mythicpvp.com)"
            )
            
            radium.server.allPlayers.forEach { player ->
                if (player.hasPermission("radium.staff")) {
                    player.sendMessage(alertMessage)
                }
            }
        }
    }
    
    /**
     * Broadcast panic removal to staff
     */
    private fun broadcastPanicRemoval(targetName: String, staffName: String) {
        radium.scope.launch {
            val message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&e$staffName removed panic mode from $targetName"
            )
            
            radium.server.allPlayers.forEach { player ->
                if (player.hasPermission("radium.staff")) {
                    player.sendMessage(message)
                }
            }
        }
    }
    
    /**
     * Broadcast panic expiration to staff
     */
    private fun broadcastPanicExpiration(playerName: String) {
        radium.scope.launch {
            val message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&e$playerName's panic mode has expired"
            )
            
            radium.server.allPlayers.forEach { player ->
                if (player.hasPermission("radium.staff")) {
                    player.sendMessage(message)
                }
            }
        }
    }
}

/**
 * Data class representing a player in panic mode
 */
data class PanicData(
    val playerUuid: UUID,
    val playerName: String,
    val startTime: Long,
    val endTime: Long
)

/**
 * Result of panic activation
 */
sealed class PanicResult {
    object Success : PanicResult()
    object AlreadyInPanic : PanicResult()
    data class OnCooldown(val minutes: Int, val seconds: Int) : PanicResult()
    data class Error(val message: String) : PanicResult()
}
