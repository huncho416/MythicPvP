package radium.backend.heads

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Minecraft-Heads.com Integration for Radium Backend
 * Provides API to fetch custom player heads from the Minecraft-Heads.com database
 * This is used by the Radium backend to provide head data to Minestom servers via API
 */
class MinecraftHeadsManager {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, CachedHead>()
    private val categoryCache = ConcurrentHashMap<String, List<HeadData>>()
    
    private val baseUrl = "https://minecraft-heads.com/scripts/api.php"
    private val cacheExpiryMinutes = 60 // Cache for 1 hour to respect ToS
    
    data class CachedHead(
        val headData: HeadData,
        val timestamp: Long
    )
    
    data class HeadData(
        val name: String,
        val uuid: String,
        val value: String, // Base64 encoded skin texture
        val signature: String? = null,
        val category: String? = null,
        val tags: List<String> = emptyList()
    )
    
    /**
     * Get a head by exact name
     */
    fun getHeadByName(name: String): CompletableFuture<HeadData?> {
        return CompletableFuture.supplyAsync {
            try {
                // Check cache first
                val cached = cache[name.lowercase()]
                if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheExpiryMinutes * 60 * 1000) {
                    return@supplyAsync cached.headData
                }
                
                // Fetch from API
                val request = Request.Builder()
                    .url("$baseUrl?cat=new-heads&tags=$name")
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync null
                    
                    val jsonResponse = gson.fromJson(response.body?.string(), JsonArray::class.java)
                    if (jsonResponse.size() == 0) return@supplyAsync null
                    
                    val firstHead = jsonResponse[0] as JsonObject
                    val headData = HeadData(
                        name = firstHead.get("name")?.asString ?: name,
                        uuid = firstHead.get("uuid")?.asString ?: UUID.randomUUID().toString(),
                        value = firstHead.get("value")?.asString ?: "",
                        signature = firstHead.get("signature")?.asString,
                        category = firstHead.get("category")?.asString,
                        tags = firstHead.get("tags")?.asJsonArray?.map { it.asString } ?: emptyList()
                    )
                    
                    // Cache the result
                    cache[name.lowercase()] = CachedHead(headData, System.currentTimeMillis())
                    
                    headData
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Get heads by category
     */
    fun getHeadsByCategory(category: String): CompletableFuture<List<HeadData>> {
        return CompletableFuture.supplyAsync {
            try {
                // Check cache first
                val cached = categoryCache[category]
                if (cached != null) {
                    return@supplyAsync cached
                }
                
                val request = Request.Builder()
                    .url("$baseUrl?cat=$category")
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync emptyList()
                    
                    val jsonResponse = gson.fromJson(response.body?.string(), JsonArray::class.java)
                    val heads = jsonResponse.map { element ->
                        val obj = element as JsonObject
                        HeadData(
                            name = obj.get("name")?.asString ?: "",
                            uuid = obj.get("uuid")?.asString ?: UUID.randomUUID().toString(),
                            value = obj.get("value")?.asString ?: "",
                            signature = obj.get("signature")?.asString,
                            category = category,
                            tags = obj.get("tags")?.asJsonArray?.map { it.asString } ?: emptyList()
                        )
                    }
                    
                    // Cache the result
                    categoryCache[category] = heads
                    
                    heads
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Search heads by tags
     */
    fun searchHeads(query: String): CompletableFuture<List<HeadData>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = Request.Builder()
                    .url("$baseUrl?cat=new-heads&tags=$query")
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync emptyList()
                    
                    val jsonResponse = gson.fromJson(response.body?.string(), JsonArray::class.java)
                    jsonResponse.map { element ->
                        val obj = element as JsonObject
                        HeadData(
                            name = obj.get("name")?.asString ?: "",
                            uuid = obj.get("uuid")?.asString ?: UUID.randomUUID().toString(),
                            value = obj.get("value")?.asString ?: "",
                            signature = obj.get("signature")?.asString,
                            category = obj.get("category")?.asString,
                            tags = obj.get("tags")?.asJsonArray?.map { it.asString } ?: emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get popular categories
     */
    fun getCategories(): List<String> {
        return listOf(
            "alphabet", "animals", "blocks", "decoration", "food-drinks",
            "humans", "humanoid", "miscellaneous", "monsters", "plants"
        )
    }
    
    /**
     * Clear cache (useful for development/testing)
     */
    fun clearCache() {
        cache.clear()
        categoryCache.clear()
    }
}
