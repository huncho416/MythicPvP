package huncho.main.lobby.integration

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.kyori.adventure.text.Component
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Minecraft-Heads.com integration client for Lobby
 * Interfaces with Radium's heads API to fetch and create custom heads
 */
class HeadsIntegration(
    private val radiumApiUrl: String = "http://localhost:8080",
    private val apiKey: String? = null
) {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, CachedHead>()
    private val cacheExpiryMinutes = 30 // Cache for 30 minutes in lobby for performance
    
    data class CachedHead(
        val itemStack: ItemStack,
        val timestamp: Long
    )
    
    data class HeadData(
        val name: String,
        val uuid: String,
        val value: String,
        val signature: String? = null,
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val displayName: String? = null
    )
    
    /**
     * Get a custom head by name and create a Minestom ItemStack
     */
    fun getHeadByName(name: String, displayName: String? = null): CompletableFuture<ItemStack?> {
        return CompletableFuture.supplyAsync {
            try {
                val cacheKey = "${name}_${displayName ?: name}"
                
                // Check cache first
                val cached = cache[cacheKey]
                if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheExpiryMinutes * 60 * 1000) {
                    return@supplyAsync cached.itemStack
                }
                
                // Fetch from Radium API
                val request = Request.Builder()
                    .url("$radiumApiUrl/heads/name/$name")
                    .apply { apiKey?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync null
                    
                    val responseBody = response.body?.string() ?: return@supplyAsync null
                    val responseMap = gson.fromJson<Map<String, Any>>(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
                    
                    if (responseMap["success"] as? Boolean != true) return@supplyAsync null
                    
                    val headMap = responseMap["head"] as? Map<String, Any> ?: return@supplyAsync null
                    
                    val headData = HeadData(
                        name = headMap["name"] as String,
                        uuid = headMap["uuid"] as String,
                        value = headMap["value"] as String,
                        signature = headMap["signature"] as? String,
                        category = headMap["category"] as? String,
                        tags = (headMap["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        displayName = displayName
                    )
                    
                    val itemStack = createPlayerHead(headData)
                    
                    // Cache the result
                    cache[cacheKey] = CachedHead(itemStack, System.currentTimeMillis())
                    
                    itemStack
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Search for heads and return a list of ItemStacks
     */
    fun searchHeads(query: String, limit: Int = 10): CompletableFuture<List<ItemStack>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = Request.Builder()
                    .url("$radiumApiUrl/heads/search?q=$query")
                    .apply { apiKey?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync emptyList()
                    
                    val responseBody = response.body?.string() ?: return@supplyAsync emptyList()
                    val responseMap = gson.fromJson<Map<String, Any>>(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
                    
                    if (responseMap["success"] as? Boolean != true) return@supplyAsync emptyList()
                    
                    val headsList = responseMap["heads"] as? List<*> ?: return@supplyAsync emptyList()
                    
                    headsList.take(limit).mapNotNull { headItem ->
                        val headMap = headItem as? Map<String, Any> ?: return@mapNotNull null
                        
                        val headData = HeadData(
                            name = headMap["name"] as String,
                            uuid = headMap["uuid"] as String,
                            value = headMap["value"] as String,
                            signature = headMap["signature"] as? String,
                            category = headMap["category"] as? String,
                            tags = (headMap["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        )
                        
                        createPlayerHead(headData)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Get heads by category
     */
    fun getHeadsByCategory(category: String, limit: Int = 10): CompletableFuture<List<ItemStack>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = Request.Builder()
                    .url("$radiumApiUrl/heads/category/$category")
                    .apply { apiKey?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync emptyList()
                    
                    val responseBody = response.body?.string() ?: return@supplyAsync emptyList()
                    val responseMap = gson.fromJson<Map<String, Any>>(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
                    
                    if (responseMap["success"] as? Boolean != true) return@supplyAsync emptyList()
                    
                    val headsList = responseMap["heads"] as? List<*> ?: return@supplyAsync emptyList()
                    
                    headsList.take(limit).mapNotNull { headItem ->
                        val headMap = headItem as? Map<String, Any> ?: return@mapNotNull null
                        
                        val headData = HeadData(
                            name = headMap["name"] as String,
                            uuid = headMap["uuid"] as String,
                            value = headMap["value"] as String,
                            signature = headMap["signature"] as? String,
                            category = headMap["category"] as? String,
                            tags = (headMap["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        )
                        
                        createPlayerHead(headData)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Get staff heads optimized for staff mode
     */
    fun getStaffHeads(): CompletableFuture<List<ItemStack>> {
        return CompletableFuture.supplyAsync {
            try {
                val cacheKey = "staff_heads"
                val cached = cache[cacheKey]
                if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheExpiryMinutes * 60 * 1000) {
                    // Return the first item from cache, but we need to handle list caching differently
                    // For now, fetch fresh data but this could be optimized
                }
                
                val request = Request.Builder()
                    .url("$radiumApiUrl/heads/staff-heads")
                    .apply { apiKey?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync emptyList()
                    
                    val responseBody = response.body?.string() ?: return@supplyAsync emptyList()
                    val responseMap = gson.fromJson<Map<String, Any>>(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
                    
                    if (responseMap["success"] as? Boolean != true) return@supplyAsync emptyList()
                    
                    val headsList = responseMap["heads"] as? List<*> ?: return@supplyAsync emptyList()
                    
                    headsList.mapNotNull { headItem ->
                        val headMap = headItem as? Map<String, Any> ?: return@mapNotNull null
                        
                        val headData = HeadData(
                            name = headMap["name"] as String,
                            uuid = headMap["uuid"] as String,
                            value = headMap["value"] as String,
                            signature = headMap["signature"] as? String,
                            category = headMap["category"] as? String,
                            tags = (headMap["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        )
                        
                        createPlayerHead(headData)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Create a Minestom ItemStack from head data
     * Uses a simplified approach - full texture support can be enhanced later
     */
    private fun createPlayerHead(headData: HeadData): ItemStack {
        try {
            // Create the basic player head with name and description
            val itemStack = ItemStack.builder(Material.PLAYER_HEAD)
                .customName(Component.text(headData.displayName ?: headData.name))
                .lore(
                    Component.text("Custom Head from Minecraft-Heads.com"),
                    Component.text("§7UUID: ${headData.uuid}"),
                    Component.text("§7Category: ${headData.category ?: "Unknown"}")
                )
                .build()
            
            // TODO: Apply custom texture using proper Minestom NBT API
            // For now, return basic head with metadata
            // The texture value is: ${headData.value}
            // The signature is: ${headData.signature ?: "None"}
            
            return itemStack
        } catch (e: Exception) {
            e.printStackTrace()
            // Return a basic player head if creation fails
            return ItemStack.builder(Material.PLAYER_HEAD)
                .customName(Component.text(headData.displayName ?: headData.name))
                .lore(Component.text("Custom Head (fallback)"))
                .build()
        }
    }
    
    /**
     * Get available categories
     */
    fun getCategories(): CompletableFuture<List<String>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = Request.Builder()
                    .url("$radiumApiUrl/heads/categories")
                    .apply { apiKey?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@supplyAsync emptyList()
                    
                    val responseBody = response.body?.string() ?: return@supplyAsync emptyList()
                    val responseMap = gson.fromJson<Map<String, Any>>(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
                    
                    if (responseMap["success"] as? Boolean != true) return@supplyAsync emptyList()
                    
                    val categories = responseMap["categories"] as? List<*> ?: return@supplyAsync emptyList()
                    categories.mapNotNull { it as? String }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Clear cache (useful for development/testing)
     */
    fun clearCache() {
        cache.clear()
    }
}
