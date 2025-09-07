package huncho.main.lobby.features.messaging

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for tracking last private message targets for reply functionality
 */
object LastMessageManager {
    
    private val lastMessages = ConcurrentHashMap<UUID, UUID>()
    
    /**
     * Set the last message target for a player
     */
    fun setLastMessage(playerUuid: UUID, targetUuid: UUID) {
        lastMessages[playerUuid] = targetUuid
    }
    
    /**
     * Get the last message target for a player
     */
    fun getLastMessage(playerUuid: UUID): UUID? {
        return lastMessages[playerUuid]
    }
    
    /**
     * Remove last message data for a player (when they disconnect)
     */
    fun removePlayer(playerUuid: UUID) {
        lastMessages.remove(playerUuid)
        // Also remove this player as a target from other players
        lastMessages.entries.removeIf { it.value == playerUuid }
    }
    
    /**
     * Clear all last message data
     */
    fun clear() {
        lastMessages.clear()
    }
}
