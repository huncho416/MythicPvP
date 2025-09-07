package huncho.main.lobby.utils

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Permission cache system for synchronous permission checks
 * Used primarily for tab completion filtering where async checks are not feasible
 */
object PermissionCache {
    
    private data class CachedPermission(
        val hasPermission: Boolean,
        val timestamp: Long
    )
    
    private val permissionCache = ConcurrentHashMap<String, CachedPermission>()
    private val cacheExpiryTime = TimeUnit.MINUTES.toMillis(5) // 5 minutes cache
    
    /**
     * Check if a player has a permission, using cache if available
     * Falls back to async check and caches the result
     */
    fun hasPermissionCached(player: Player, permission: String): Boolean {
        val cacheKey = "${player.uuid}-$permission"
        val cached = permissionCache[cacheKey]
        
        // Check if cache is valid
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < cacheExpiryTime) {
            return cached.hasPermission
        }
        
        // Cache miss or expired - trigger async update and return conservative default
        updatePermissionCache(player, permission)
        
        // For tab completion, default to false (hide sensitive commands) if not cached
        // This means the first time a player joins, sensitive commands won't show until cache is populated
        return cached?.hasPermission ?: false
    }
    
    /**
     * Asynchronously update the permission cache
     */
    private fun updatePermissionCache(player: Player, permission: String) {
        val cacheKey = "${player.uuid}-$permission"
        
        LobbyPlugin.radiumIntegration.hasPermission(player.uuid, permission).thenAccept { hasPermission ->
            permissionCache[cacheKey] = CachedPermission(hasPermission, System.currentTimeMillis())
        }.exceptionally { throwable ->
            LobbyPlugin.logger.warn("Failed to check permission $permission for player ${player.username}: ${throwable.message}")
            // Don't cache failed checks
            null
        }
    }
    
    /**
     * Pre-populate permission cache for common sensitive permissions
     */
    fun preloadPermissions(player: Player) {
        val sensitivePermissions = listOf(
            // Radium punishment permissions
            "radium.punish.ban",
            "radium.punish.tempban", 
            "radium.punish.unban",
            "radium.punish.mute",
            "radium.punish.unmute",
            "radium.punish.kick",
            "radium.punish.warn",
            "radium.punish.blacklist",
            "radium.punish.unblacklist",
            "radium.punish.check",
            // Legacy hub command permissions
            "hub.command.adminchat",
            "hub.command.freeze",
            "hub.command.unfreeze", 
            "hub.command.panic",
            "hub.command.unpanic",
            "hub.command.staffmode",
            "hub.command.invsee",
            "hub.command.sudo",
            "hub.command.god",
            "hub.command.give",
            "hub.command.heal",
            "hub.command.feed",
            "hub.command.skull",
            "hub.command.teleport",
            "hub.command.teleporthere",
            "hub.command.teleportworld",
            "hub.command.teleportposition",
            "hub.command.ban",
            "hub.command.tempban",
            "hub.command.unban",
            "hub.command.mute",
            "hub.command.unmute",
            "hub.command.kick",
            "hub.command.warn",
            "hub.command.blacklist",
            "hub.command.unblacklist",
            "hub.command.checkpunishments",
            "hub.command.maintenance",
            "hub.command.rename",
            "hub.command.alert",
            "hub.command.broadcast",
            "hub.command.stafflist",
            "hub.command.clear",
            "hub.command.credits",
            "hub.command.more",
            "hub.command.nametag",
            "hub.command.reports",
            "hub.command.schematic",
            "lobby.admin" // Admin bypass permission
        )
        
        for (permission in sensitivePermissions) {
            updatePermissionCache(player, permission)
        }
    }
    
    /**
     * Clear cache for a specific player (called on disconnect)
     */
    fun clearPlayerCache(playerUuid: UUID) {
        val keysToRemove = permissionCache.keys.filter { it.startsWith("$playerUuid-") }
        for (key in keysToRemove) {
            permissionCache.remove(key)
        }
    }
    
    /**
     * Clear expired cache entries
     */
    fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = permissionCache.entries
            .filter { (currentTime - it.value.timestamp) >= cacheExpiryTime }
            .map { it.key }
        
        for (key in expiredKeys) {
            permissionCache.remove(key)
        }
    }
}
