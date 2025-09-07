package radium.backend.credits

import com.mongodb.reactivestreams.client.MongoDatabase
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.bson.Document
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the credits currency system
 */
class CreditsManager(
    private val database: MongoDatabase,
    private val logger: ComponentLogger
) {
    private val creditsCache = ConcurrentHashMap<UUID, Long>()
    private val CREDITS_COLLECTION = "credits"
    
    /**
     * Get player's credit balance
     */
    suspend fun getCredits(playerUuid: UUID): Long {
        // Check cache first
        creditsCache[playerUuid]?.let { return it }
        
        try {
            val collection = database.getCollection(CREDITS_COLLECTION)
            val document = collection.find(Document("uuid", playerUuid.toString())).awaitFirstOrNull()
            
            val credits = document?.getLong("credits") ?: 0L
            creditsCache[playerUuid] = credits
            return credits
            
        } catch (e: Exception) {
            logger.error("Failed to get credits for $playerUuid", e)
            return 0L
        }
    }
    
    /**
     * Set player's credit balance
     */
    suspend fun setCredits(playerUuid: UUID, amount: Long): Boolean {
        try {
            val collection = database.getCollection(CREDITS_COLLECTION)
            val filter = Document("uuid", playerUuid.toString())
            val update = Document("\$set", Document()
                .append("credits", amount)
                .append("lastUpdated", System.currentTimeMillis())
            )
            
            collection.updateOne(filter, update, com.mongodb.client.model.UpdateOptions().upsert(true)).awaitSingle()
            creditsCache[playerUuid] = amount
            
            return true
            
        } catch (e: Exception) {
            logger.error("Failed to set credits for $playerUuid to $amount", e)
            return false
        }
    }
    
    /**
     * Add credits to player's balance
     */
    suspend fun addCredits(playerUuid: UUID, amount: Long): Boolean {
        if (amount <= 0) return false
        
        val currentBalance = getCredits(playerUuid)
        val newBalance = currentBalance + amount
        
        return setCredits(playerUuid, newBalance)
    }
    
    /**
     * Remove credits from player's balance
     */
    suspend fun removeCredits(playerUuid: UUID, amount: Long): Boolean {
        if (amount <= 0) return false
        
        val currentBalance = getCredits(playerUuid)
        if (currentBalance < amount) return false
        
        val newBalance = currentBalance - amount
        return setCredits(playerUuid, newBalance)
    }
    
    /**
     * Check if player has enough credits
     */
    suspend fun hasCredits(playerUuid: UUID, amount: Long): Boolean {
        return getCredits(playerUuid) >= amount
    }
    
    /**
     * Transfer credits between players
     */
    suspend fun transferCredits(fromUuid: UUID, toUuid: UUID, amount: Long): Boolean {
        if (amount <= 0) return false
        if (!hasCredits(fromUuid, amount)) return false
        
        return try {
            removeCredits(fromUuid, amount) && addCredits(toUuid, amount)
        } catch (e: Exception) {
            logger.error("Failed to transfer $amount credits from $fromUuid to $toUuid", e)
            false
        }
    }
    
    /**
     * Get top players by credits
     */
    suspend fun getTopCredits(limit: Int = 10): List<Pair<String, Long>> {
        return try {
            val collection = database.getCollection(CREDITS_COLLECTION)
            val results = mutableListOf<Pair<String, Long>>()
            
            collection.find()
                .sort(Document("credits", -1))
                .limit(limit)
                .subscribe(object : org.reactivestreams.Subscriber<Document> {
                    override fun onSubscribe(s: org.reactivestreams.Subscription) {
                        s.request(Long.MAX_VALUE)
                    }
                    
                    override fun onNext(document: Document) {
                        val uuid = document.getString("uuid")
                        val credits = document.getLong("credits")
                        if (uuid != null && credits != null) {
                            results.add(uuid to credits)
                        }
                    }
                    
                    override fun onError(t: Throwable) {
                        logger.error("Error fetching top credits", t)
                    }
                    
                    override fun onComplete() {
                        // Processing complete
                    }
                })
            
            results
            
        } catch (e: Exception) {
            logger.error("Failed to get top credits", e)
            emptyList()
        }
    }
    
    /**
     * Clear cache for a player
     */
    fun clearCache(playerUuid: UUID) {
        creditsCache.remove(playerUuid)
    }
    
    /**
     * Clear all cache
     */
    fun clearAllCache() {
        creditsCache.clear()
    }
}
