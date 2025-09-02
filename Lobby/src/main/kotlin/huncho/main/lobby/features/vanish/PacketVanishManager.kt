package huncho.main.lobby.features.vanish

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.network.packet.server.play.EntityPositionPacket
import net.minestom.server.network.packet.server.play.EntityPositionAndRotationPacket
import net.minestom.server.network.packet.server.play.EntityRotationPacket
import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.listeners.VanishPluginMessageListener
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * Packet-based vanish system that intercepts outgoing packets to prevent vanished players
 * from being visible to unauthorized viewers. This system operates at the network level
 * to override Minestom's internal entity/viewer management.
 */
class PacketVanishManager(private val plugin: LobbyPlugin) : EventListener<PlayerPacketOutEvent> {
    
    private val vanishListener: VanishPluginMessageListener by lazy { 
        plugin.getVanishListener() 
    }
    
    // Track blocked entities per viewer to prevent packet spam
    private val blockedEntities = ConcurrentHashMap<UUID, MutableSet<Int>>()
    
    // Track destroyed entities to avoid duplicate destroy packets
    private val destroyedEntities = ConcurrentHashMap<UUID, MutableSet<Int>>()
    
    init {

    }
    
    override fun eventType(): Class<PlayerPacketOutEvent> = PlayerPacketOutEvent::class.java
    
    override fun run(event: PlayerPacketOutEvent): EventListener.Result {
        try {
            val packet = event.packet
            val viewer = event.player
            
            // Handle different packet types that could reveal vanished players
            when (packet) {
                // Temporarily disabled due to API compatibility issues
                // is net.minestom.server.network.packet.server.play.SpawnPlayerPacket -> handleSpawnPlayerPacket(event, packet, viewer)
                is PlayerInfoUpdatePacket -> handlePlayerInfoUpdatePacket(event, packet, viewer)
                is EntityPositionPacket -> handleEntityMovementPacket(event, packet.entityId, viewer)
                is EntityPositionAndRotationPacket -> handleEntityMovementPacket(event, packet.entityId, viewer)
                is EntityRotationPacket -> handleEntityMovementPacket(event, packet.entityId, viewer)
                is EntityMetaDataPacket -> handleEntityMetaDataPacket(event, packet.entityId, viewer)
                // Don't interfere with destroy packets - let them through
                else -> {
                    // Other packets - no action needed
                }
            }
            
        } catch (e: Exception) {
            plugin.logger.warn("Error in packet vanish processing", e)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    /**
     * Handle player spawn packets - block if the spawning player is vanished and viewer can't see them
     * TEMPORARILY DISABLED due to API compatibility issues
     */
    /*
    private fun handleSpawnPlayerPacket(event: PlayerPacketOutEvent, packet: net.minestom.server.network.packet.server.play.SpawnPlayerPacket, viewer: Player) {
        try {
            val entityId = packet.entityId
            val targetPlayer = findPlayerByEntityId(entityId)
            
            if (targetPlayer != null && targetPlayer.uuid != viewer.uuid) {
                val isVanished = plugin.radiumIntegration.isPlayerVanished(targetPlayer.uuid).join()
                
                if (isVanished) {
                    // Check if viewer can see this vanished player
                    checkAndBlockVanishedPlayer(event, targetPlayer, viewer, entityId, "SPAWN")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error handling spawn player packet", e)
        }
    }
    */
    
    /**
     * Handle player info update packets (tab list) - modify to add [V] indicator for staff or block for defaults
     */
    private fun handlePlayerInfoUpdatePacket(event: PlayerPacketOutEvent, packet: PlayerInfoUpdatePacket, viewer: Player) {
        try {
            // Let the tab system handle this - we just focus on entity visibility
            // The TabListManager already handles vanish indicators properly
            
            // Check if any of the players in this update are vanished and should be hidden from tab
            val entriesToBlock = mutableListOf<PlayerInfoUpdatePacket.Entry>()
            
            for (entry in packet.entries) {
                val targetUuid = entry.uuid
                val isVanished = vanishListener.isPlayerVanished(targetUuid)
                
                if (isVanished && targetUuid != viewer.uuid) {
                    // Check if viewer can see this vanished player in tab
                    try {
                        val canSee = runBlocking { vanishListener.canSeeVanished(viewer.uuid, targetUuid) }
                        if (!canSee) {
                            entriesToBlock.add(entry)

                        }
                    } catch (e: Exception) {
                        // If we can't determine, default to blocking for safety
                        entriesToBlock.add(entry)

                    }
                }
            }
            
            // If we need to block some entries, cancel the entire packet for safety
            if (entriesToBlock.isNotEmpty()) {
                // Block the entire packet if it contains vanished players
                event.isCancelled = true

            }
        } catch (e: Exception) {
            plugin.logger.warn("Error handling player info update packet", e)
        }
    }
    
    /**
     * Handle entity movement packets - block if the entity is a vanished player
     */
    private fun handleEntityMovementPacket(event: PlayerPacketOutEvent, entityId: Int, viewer: Player) {
        try {
            val targetPlayer = findPlayerByEntityId(entityId)
            
            if (targetPlayer != null && targetPlayer.uuid != viewer.uuid) {
                val isVanished = vanishListener.isPlayerVanished(targetPlayer.uuid)
                
                if (isVanished) {
                    checkAndBlockVanishedPlayer(event, targetPlayer, viewer, entityId, "MOVEMENT")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error handling entity movement packet", e)
        }
    }
    
    /**
     * Handle entity metadata packets - block if the entity is a vanished player
     */
    private fun handleEntityMetaDataPacket(event: PlayerPacketOutEvent, entityId: Int, viewer: Player) {
        try {
            val targetPlayer = findPlayerByEntityId(entityId)
            
            if (targetPlayer != null && targetPlayer.uuid != viewer.uuid) {
                val isVanished = vanishListener.isPlayerVanished(targetPlayer.uuid)
                
                if (isVanished) {
                    checkAndBlockVanishedPlayer(event, targetPlayer, viewer, entityId, "METADATA")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error handling entity metadata packet", e)
        }
    }
    
    /**
     * Check if a vanished player should be blocked from a viewer and take action
     */
    private fun checkAndBlockVanishedPlayer(
        event: PlayerPacketOutEvent, 
        targetPlayer: Player, 
        viewer: Player, 
        entityId: Int, 
        packetType: String
    ) {
        try {
            val canSee = runBlocking { vanishListener.canSeeVanished(viewer, targetPlayer.uuid) }
            
            if (!canSee) {
                // Block this packet from unauthorized viewer
                event.isCancelled = true
                
                // Track blocked entity
                blockedEntities.computeIfAbsent(viewer.uuid) { mutableSetOf() }.add(entityId)
                
                // Send destroy packet if this is the first time we're blocking this entity
                val destroyedSet = destroyedEntities.computeIfAbsent(viewer.uuid) { mutableSetOf() }
                if (!destroyedSet.contains(entityId)) {
                    sendDestroyEntityPacket(viewer, entityId)
                    destroyedSet.add(entityId)

                } else {

                }
            } else {
                // Viewer can see this vanished player - ensure they're not in the blocked list
                val blockedSet = blockedEntities[viewer.uuid]
                if (blockedSet?.contains(entityId) == true) {
                    blockedSet.remove(entityId)
                    destroyedEntities[viewer.uuid]?.remove(entityId)

                }
            }
        } catch (e: Exception) {
            // If we can't determine permissions, default to blocking for safety
            event.isCancelled = true
            plugin.logger.warn("🚫 PACKET BLOCK: Blocked ${packetType} for ${targetPlayer.username} from ${viewer.username} (error check)", e)
        }
    }
    
    /**
     * Send a destroy entity packet to remove a vanished player from viewer's client
     */
    private fun sendDestroyEntityPacket(viewer: Player, entityId: Int) {
        try {
            val destroyPacket = DestroyEntitiesPacket(entityId)
            viewer.sendPacket(destroyPacket)

        } catch (e: Exception) {
            plugin.logger.warn("Error sending destroy entity packet", e)
        }
    }
    
    /**
     * Send a player info remove packet to remove vanished player from viewer's tab list
     */
    private fun sendPlayerInfoRemovePacket(viewer: Player, targetUuid: UUID) {
        try {
            val removePacket = PlayerInfoRemovePacket(listOf(targetUuid))
            viewer.sendPacket(removePacket)

        } catch (e: Exception) {
            plugin.logger.warn("Error sending player info remove packet", e)
        }
    }
    
    /**
     * Find a player by their entity ID
     */
    private fun findPlayerByEntityId(entityId: Int): Player? {
        return MinecraftServer.getConnectionManager().onlinePlayers.find { it.entityId == entityId }
    }
    
    /**
     * Handle player unvanish - ensure they become visible again
     */
    fun handlePlayerUnvanish(playerUuid: UUID) {
        try {
            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
            if (player == null) return
            

            
            // Clear blocked/destroyed tracking for this player
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
                if (viewer.uuid != playerUuid) {
                    val blockedSet = blockedEntities[viewer.uuid]
                    val destroyedSet = destroyedEntities[viewer.uuid]
                    
                    if (blockedSet?.contains(player.entityId) == true || destroyedSet?.contains(player.entityId) == true) {
                        blockedSet?.remove(player.entityId)
                        destroyedSet?.remove(player.entityId)
                        
                        // Force respawn the player for this viewer
                        respawnPlayerForViewer(player, viewer)

                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error handling packet unvanish", e)
        }
    }
    
    /**
     * Force respawn a player for a specific viewer (used when unvanishing)
     */
    private fun respawnPlayerForViewer(target: Player, viewer: Player) {
        try {
            // First remove viewer if present, then re-add to force a fresh spawn
            if (target.viewers.contains(viewer)) {
                target.removeViewer(viewer)

            }
            
            // Add viewer back to target's viewer list
            target.addViewer(viewer)

            
            // Also ensure reverse visibility
            if (!viewer.viewers.contains(target)) {
                viewer.addViewer(target)

            }
            
        } catch (e: Exception) {
            plugin.logger.warn("Error respawning player for viewer", e)
        }
    }
    
    /**
     * Handle player vanish - ensure they become hidden
     */
    fun handlePlayerVanish(playerUuid: UUID) {
        try {
            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
            if (player == null) return
            

            
            // For each online viewer, check if they should see this vanished player
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
                if (viewer.uuid != playerUuid) {
                    try {
                        val canSee = runBlocking { vanishListener.canSeeVanished(viewer.uuid, playerUuid) }
                        
                        if (!canSee) {
                            // Hide from unauthorized viewer
                            sendDestroyEntityPacket(viewer, player.entityId)
                            sendPlayerInfoRemovePacket(viewer, playerUuid)
                            
                            // Track as blocked/destroyed
                            blockedEntities.computeIfAbsent(viewer.uuid) { mutableSetOf() }.add(player.entityId)
                            destroyedEntities.computeIfAbsent(viewer.uuid) { mutableSetOf() }.add(player.entityId)
                            

                        }
                    } catch (e: Exception) {
                        plugin.logger.warn("Error checking vanish visibility for ${viewer.username}", e)
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error handling packet vanish", e)
        }
    }
    
    /**
     * Clean up tracking data for a disconnected player
     */
    fun handlePlayerDisconnect(playerUuid: UUID) {
        blockedEntities.remove(playerUuid)
        destroyedEntities.remove(playerUuid)
        
        // Also clean up references to this player's entity ID from other players' tracking
        val disconnectedPlayer = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
        if (disconnectedPlayer != null) {
            val entityId = disconnectedPlayer.entityId
            blockedEntities.values.forEach { it.remove(entityId) }
            destroyedEntities.values.forEach { it.remove(entityId) }
        }
    }
    
    /**
     * Get debug information about blocked entities
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "blockedEntities" to blockedEntities.size,
            "destroyedEntities" to destroyedEntities.size,
            "totalBlockedCount" to blockedEntities.values.sumOf { it.size },
            "totalDestroyedCount" to destroyedEntities.values.sumOf { it.size }
        )
    }
}
