package huncho.main.lobby.models

import java.util.UUID

/**
 * Represents vanish state data for a player in the Lobby
 */
data class VanishData(
    val playerUuid: UUID,
    val playerName: String,
    val vanished: Boolean,
    val level: Int,
    val requiredWeight: Int,
    val timestamp: Long
) {
    companion object {
        fun create(playerUuid: UUID, playerName: String, level: Int, requiredWeight: Int): VanishData {
            return VanishData(
                playerUuid = playerUuid,
                playerName = playerName,
                vanished = true,
                level = level,
                requiredWeight = requiredWeight,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Get duration vanished in milliseconds
     */
    fun getVanishDuration(): Long {
        return System.currentTimeMillis() - timestamp
    }
    
    /**
     * Get formatted vanish duration
     */
    fun getFormattedDuration(): String {
        val duration = getVanishDuration()
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
