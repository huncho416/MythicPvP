package huncho.main.lobby.features.admin

import huncho.main.lobby.LobbyPlugin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Manager for administrative commands and functions
 */
class AdminCommandManager(private val plugin: LobbyPlugin) {
    
    private val logger: Logger = LoggerFactory.getLogger(AdminCommandManager::class.java)
    
    fun initialize() {
        logger.info("Initializing Admin Command Manager...")
        try {
            // Admin Command Manager initialized
        } catch (e: Exception) {
            logger.error("Failed to initialize Admin Command Manager", e)
        }
    }
    
    /**
     * Give an item to a player
     */
    suspend fun giveItem(giver: Player, targetName: String, itemName: String, amount: Int): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else {
                // Parse material from string
                val material = Material.values().find { it.name().equals(itemName, ignoreCase = true) }
                    ?: Material.values().find { it.name().contains(itemName, ignoreCase = true) }
                    ?: return Result.failure(Exception("Invalid item: $itemName"))
                
                // Create item stack
                val itemStack = ItemStack.of(material, amount)
                
                // Add to player's inventory
                val added = target.inventory.addItemStack(itemStack)
                if (!added) {
                    return Result.failure(Exception("${target.username}'s inventory is full!"))
                }
                
                // Notify target
                target.sendMessage("§a§lItem Received!")
                target.sendMessage("§7You received §e$amount §f$itemName §7from §f${giver.username}")
                
                logger.info("${giver.username} gave $amount $itemName to ${target.username}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error("Error giving item", e)
            Result.failure(e)
        }
    }
    
    /**
     * Heal a player
     */
    suspend fun healPlayer(healer: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else {
                // Heal the player
                target.heal()
                
                // Notify target if not self
                if (target != healer) {
                    target.sendMessage("§a§lHealed!")
                    target.sendMessage("§7You have been healed by §f${healer.username}")
                }
                
                logger.info("${healer.username} healed ${target.username}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error("Error healing player", e)
            Result.failure(e)
        }
    }
    
    /**
     * Feed a player
     */
    suspend fun feedPlayer(feeder: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else {
                // Feed the player
                target.food = 20
                target.foodSaturation = 20.0f
                
                // Notify target if not self
                if (target != feeder) {
                    target.sendMessage("§a§lFed!")
                    target.sendMessage("§7You have been fed by §f${feeder.username}")
                }
                
                logger.info("${feeder.username} fed ${target.username}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error("Error feeding player", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clear a player's inventory
     */
    suspend fun clearInventory(clearer: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else {
                // Clear inventory
                target.inventory.clear()
                
                // Notify target if not self
                if (target != clearer) {
                    target.sendMessage("§c§lInventory Cleared!")
                    target.sendMessage("§7Your inventory has been cleared by §f${clearer.username}")
                }
                
                logger.info("${clearer.username} cleared ${target.username}'s inventory")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error("Error clearing inventory", e)
            Result.failure(e)
        }
    }
    
    /**
     * Teleport player to another player
     */
    suspend fun teleportToPlayer(teleporter: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else if (target == teleporter) {
                Result.failure(Exception("You cannot teleport to yourself!"))
            } else {
                // Teleport to target
                teleporter.teleport(target.position)
                
                logger.info("${teleporter.username} teleported to ${target.username}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error("Error teleporting player", e)
            Result.failure(e)
        }
    }
    
    /**
     * Teleport player to teleporter
     */
    suspend fun teleportPlayerHere(teleporter: Player, targetName: String): Result<Unit> {
        return try {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(targetName, ignoreCase = true) }
            
            if (target == null) {
                Result.failure(Exception("Player $targetName is not online!"))
            } else if (target == teleporter) {
                Result.failure(Exception("You cannot teleport yourself to yourself!"))
            } else {
                // Teleport target to teleporter
                target.teleport(teleporter.position)
                
                // Notify target
                target.sendMessage("§e§lTeleported!")
                target.sendMessage("§7You have been teleported to §f${teleporter.username}")
                
                logger.info("${teleporter.username} teleported ${target.username} to their location")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error("Error teleporting player here", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get online staff list
     */
    suspend fun getOnlineStaff(): List<StaffInfo> {
        val staffList = mutableListOf<StaffInfo>()
        
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach { player ->
            try {
                val hasStaffPermission: Boolean = withContext(Dispatchers.IO) {
                    plugin.radiumIntegration.hasPermission(player.uuid, "hub.staff").await()
                }
                if (hasStaffPermission) {
                    val rank = plugin.nametagManager.getPlayerNametagInfo(player.uuid)?.prefix ?: "§7"
                    val isVanished: Boolean = withContext(Dispatchers.IO) {
                        plugin.radiumIntegration.isPlayerVanished(player.uuid).await()
                    }
                    
                    staffList.add(StaffInfo(
                        name = player.username,
                        rank = rank,
                        isVanished = isVanished,
                        uuid = player.uuid
                    ))
                }
            } catch (e: Exception) {
                logger.warn("Failed to check staff status for ${player.username}", e)
            }
        }
        
        return staffList.sortedBy { it.name }
    }
    
    data class StaffInfo(
        val name: String,
        val rank: String,
        val isVanished: Boolean,
        val uuid: UUID
    )
    
    /**
     * Shutdown the admin command manager
     */
    fun shutdown() {
        try {
            logger.info("Admin Command Manager shut down successfully")
        } catch (e: Exception) {
            logger.error("Error during admin command manager shutdown", e)
        }
    }
}
