package huncho.main.lobby.features.credits

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for the credits currency system
 * Handles credit transactions and synchronization across the network
 */
class CreditsManager(private val plugin: LobbyPlugin) {
    
    private val logger: Logger = LoggerFactory.getLogger(CreditsManager::class.java)
    private val creditsCache = ConcurrentHashMap<UUID, Int>()
    
    fun initialize() {
        logger.info("Initializing Credits Manager...")
        
        try {
            // Initialize the credits system
            // Credits Manager initialized
        } catch (e: Exception) {
            logger.error("Failed to initialize Credits Manager", e)
        }
    }
    
    /**
     * Get player's credit balance
     */
    suspend fun getBalance(playerUuid: UUID): Int {
        return try {
            // Check cache first
            creditsCache[playerUuid] ?: run {
                // Get from Radium backend
                val balance = withContext(Dispatchers.IO) {
                    plugin.radiumIntegration.getPlayerCredits(playerUuid).await()
                } ?: 0
                creditsCache[playerUuid] = balance
                balance
            }
        } catch (e: Exception) {
            logger.error("Error getting credits balance for $playerUuid", e)
            0
        }
    }
    
    /**
     * Get player's credit balance by username
     */
    suspend fun getBalance(playerName: String): Int {
        return try {
            val playerUuid = getPlayerUuid(playerName)
            if (playerUuid != null) {
                getBalance(playerUuid)
            } else {
                throw Exception("Player $playerName not found!")
            }
        } catch (e: Exception) {
            logger.error("Error getting credits balance for $playerName", e)
            throw e
        }
    }
    
    /**
     * Add credits to a player
     */
    suspend fun addCredits(playerName: String, amount: Int): Result<Int> {
        return try {
            if (amount <= 0) {
                return Result.failure(Exception("Amount must be positive!"))
            }
            
            val playerUuid = getPlayerUuid(playerName)
                ?: return Result.failure(Exception("Player $playerName not found!"))
            
            val currentBalance = getBalance(playerUuid)
            val newBalance = currentBalance + amount
            
            // Update in Radium backend
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.setPlayerCredits(playerUuid, newBalance).await()
            }
            if (success) {
                creditsCache[playerUuid] = newBalance
                
                // Notify player if online
                notifyPlayerOfTransaction(playerUuid, amount, "added", newBalance)
                
                // Sync across network
                syncCreditsAcrossNetwork(playerUuid, newBalance)
                
                logger.info("Added $amount credits to $playerName (new balance: $newBalance)")
                Result.success(newBalance)
            } else {
                Result.failure(Exception("Failed to update credits in database!"))
            }
        } catch (e: Exception) {
            logger.error("Error adding credits", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove credits from a player
     */
    suspend fun removeCredits(playerName: String, amount: Int): Result<Int> {
        return try {
            if (amount <= 0) {
                return Result.failure(Exception("Amount must be positive!"))
            }
            
            val playerUuid = getPlayerUuid(playerName)
                ?: return Result.failure(Exception("Player $playerName not found!"))
            
            val currentBalance = getBalance(playerUuid)
            if (currentBalance < amount) {
                return Result.failure(Exception("$playerName only has $currentBalance credits!"))
            }
            
            val newBalance = currentBalance - amount
            
            // Update in Radium backend
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.setPlayerCredits(playerUuid, newBalance).await()
            }
            if (success) {
                creditsCache[playerUuid] = newBalance
                
                // Notify player if online
                notifyPlayerOfTransaction(playerUuid, amount, "removed", newBalance)
                
                // Sync across network
                syncCreditsAcrossNetwork(playerUuid, newBalance)
                
                logger.info("Removed $amount credits from $playerName (new balance: $newBalance)")
                Result.success(newBalance)
            } else {
                Result.failure(Exception("Failed to update credits in database!"))
            }
        } catch (e: Exception) {
            logger.error("Error removing credits", e)
            Result.failure(e)
        }
    }
    
    /**
     * Set player's credit balance
     */
    suspend fun setCredits(playerName: String, amount: Int): Result<Int> {
        return try {
            if (amount < 0) {
                return Result.failure(Exception("Amount cannot be negative!"))
            }
            
            val playerUuid = getPlayerUuid(playerName)
                ?: return Result.failure(Exception("Player $playerName not found!"))
            
            // Update in Radium backend
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.setPlayerCredits(playerUuid, amount).await()
            }
            if (success) {
                creditsCache[playerUuid] = amount
                
                // Notify player if online
                notifyPlayerOfTransaction(playerUuid, amount, "set to", amount)
                
                // Sync across network
                syncCreditsAcrossNetwork(playerUuid, amount)
                
                logger.info("Set $playerName's credits to $amount")
                Result.success(amount)
            } else {
                Result.failure(Exception("Failed to update credits in database!"))
            }
        } catch (e: Exception) {
            logger.error("Error setting credits", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if player can afford a purchase
     */
    suspend fun canAfford(playerUuid: UUID, amount: Int): Boolean {
        return getBalance(playerUuid) >= amount
    }
    
    /**
     * Process a purchase (deduct credits)
     */
    suspend fun processPurchase(playerUuid: UUID, amount: Int, description: String): Result<Int> {
        return try {
            val currentBalance = getBalance(playerUuid)
            if (currentBalance < amount) {
                return Result.failure(Exception("Insufficient credits! Need $amount, have $currentBalance."))
            }
            
            val newBalance = currentBalance - amount
            
            // Update in Radium backend
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.setPlayerCredits(playerUuid, newBalance).await()
            }
            if (success) {
                creditsCache[playerUuid] = newBalance
                
                // Notify player if online
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid)
                player?.let {
                    it.sendMessage("§e§lPurchase Complete!")
                    it.sendMessage("§7Item: §f$description")
                    it.sendMessage("§7Cost: §c-$amount credits")
                    it.sendMessage("§7Remaining balance: §e$newBalance credits")
                }
                
                // Sync across network
                syncCreditsAcrossNetwork(playerUuid, newBalance)
                
                logger.info("Player $playerUuid purchased '$description' for $amount credits (new balance: $newBalance)")
                Result.success(newBalance)
            } else {
                Result.failure(Exception("Failed to process purchase in database!"))
            }
        } catch (e: Exception) {
            logger.error("Error processing purchase", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get player UUID from username
     */
    private suspend fun getPlayerUuid(playerName: String): UUID? {
        return try {
            // Check online players first
            MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.username.equals(playerName, ignoreCase = true) }?.uuid
                ?: run {
                    // Get from Radium backend
                    val uuidFuture = plugin.radiumIntegration.getPlayerUuidByName(playerName)
                    uuidFuture.await()
                }
        } catch (e: Exception) {
            logger.error("Error getting UUID for player $playerName", e)
            null
        }
    }
    
    /**
     * Notify player of credit transaction
     */
    private fun notifyPlayerOfTransaction(playerUuid: UUID, amount: Int, action: String, newBalance: Int) {
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid)
        player?.let {
            it.sendMessage("§e§lCredits Updated!")
            it.sendMessage("§7Credits $action: §e$amount")
            it.sendMessage("§7New balance: §e$newBalance credits")
        }
    }
    
    /**
     * Sync credits across network via Redis
     */
    private fun syncCreditsAcrossNetwork(playerUuid: UUID, newBalance: Int) {
        GlobalScope.launch {
            try {
                plugin.radiumIntegration.syncPlayerCreditsAcrossNetwork(playerUuid, newBalance)
            } catch (e: Exception) {
                logger.error("Error syncing credits across network", e)
            }
        }
    }
    
    /**
     * Get credits placeholder for scoreboards
     */
    fun getCreditsPlaceholder(playerUuid: UUID): String {
        return try {
            val balance = creditsCache[playerUuid] ?: 0
            "$balance"
        } catch (e: Exception) {
            "0"
        }
    }
    
    /**
     * Refresh credits cache for a player
     */
    suspend fun refreshCache(playerUuid: UUID) {
        try {
            val balance = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getPlayerCredits(playerUuid).await()
            } ?: 0
            creditsCache[playerUuid] = balance
        } catch (e: Exception) {
            logger.error("Error refreshing credits cache for $playerUuid", e)
        }
    }
    
    /**
     * Shutdown the credits manager
     */
    fun shutdown() {
        try {
            creditsCache.clear()
            logger.info("Credits Manager shut down successfully")
        } catch (e: Exception) {
            logger.error("Error during credits manager shutdown", e)
        }
    }
}
