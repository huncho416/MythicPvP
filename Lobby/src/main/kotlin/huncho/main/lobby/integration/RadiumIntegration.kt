package huncho.main.lobby.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import huncho.main.lobby.config.ConfigManager
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Integration with Radium using HTTP API for permissions, ranks, and player data
 */
class RadiumIntegration(
    private val configManager: ConfigManager
) {
    private val logger = LoggerFactory.getLogger(RadiumIntegration::class.java)
    private val playerCache = ConcurrentHashMap<UUID, PlayerData>()
    private val httpClient: OkHttpClient
    private val objectMapper = ObjectMapper()
    private var baseUrl: String = ""
    private var apiKey: String? = null
    
    init {
        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    fun initialize() {
        try {
            // Load Radium API configuration
            baseUrl = configManager.getString(configManager.mainConfig, "radium.api.base_url", "http://localhost:8080")
            apiKey = configManager.getString(configManager.mainConfig, "radium.api.key", "").takeIf { it.isNotEmpty() && it != "null" }
            
            // Ensure baseUrl ends with /api/v1 to match Radium's actual API structure
            baseUrl = baseUrl.trimEnd('/')
            if (!baseUrl.endsWith("/api/v1")) {
                if (baseUrl.endsWith("/api")) {
                    baseUrl = "$baseUrl/v1"
                } else {
                    baseUrl = "$baseUrl/api/v1"
                }
            }
            
            println("Radium HTTP API integration initialized - URL: $baseUrl")
        } catch (e: Exception) {
            println("Failed to initialize Radium integration: ${e.message}")
        }
    }
    
    fun shutdown() {
        playerCache.clear()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        println("Radium integration shut down")
    }
    
    /**
     * Get player data from Radium API with caching
     */
    fun getPlayerData(uuid: UUID): CompletableFuture<PlayerData?> {
        return CompletableFuture.supplyAsync {
            // Check cache first (5 minute TTL)
            val cached = playerCache[uuid]
            if (cached != null && cached.cachedAt.isAfter(Instant.now().minusSeconds(300))) {
                return@supplyAsync cached
            }
            
            try {
                // First try to get by UUID using the profiles endpoint
                val request = buildGetRequest("/profiles/uuid/$uuid")
                logger.debug("Making API request to: $baseUrl/profiles/uuid/$uuid")
                httpClient.newCall(request).execute().use { response ->
                    logger.debug("API response code: ${response.code}")
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync null
                        logger.debug("API response body: $body")
                        val jsonNode = objectMapper.readTree(body)
                        val playerData = parsePlayerDataFromProfile(jsonNode)
                        // Player data parsed successfully
                        playerCache[uuid] = playerData
                        return@supplyAsync playerData
                    } else if (response.code == 404) {
                        logger.debug("Player profile not found in Radium: $uuid")
                        // Player not found - they need to connect to Radium first
                        return@supplyAsync null
                    } else {
                        val errorBody = response.body?.string() ?: "No body"
                        logger.error("Error fetching player profile for $uuid: HTTP ${response.code} - $errorBody")
                        return@supplyAsync null
                    }
                }
            } catch (e: Exception) {
                println("Error fetching player data for $uuid: ${e.message}")
                return@supplyAsync null
            }
        }
    }
    
    /**
     * Get player data by username
     */
    fun getPlayerDataByName(username: String): CompletableFuture<PlayerData?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/profiles/$username")
                logger.debug("Looking up player data for: $username via ${request.url}")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync null
                        logger.debug("Got response for $username: $body")
                        val jsonNode = objectMapper.readTree(body)
                        val playerData = parsePlayerDataFromProfile(jsonNode)
                        
                        // Cache by UUID if available
                        val uuidString = jsonNode.get("uuid")?.asText()
                        if (uuidString != null) {
                            try {
                                val uuid = UUID.fromString(uuidString)
                                playerCache[uuid] = playerData
                                logger.debug("Cached player data for $username (${uuid})")
                            } catch (e: IllegalArgumentException) {
                                logger.warn("Invalid UUID format in response: $uuidString")
                            }
                        }
                        
                        return@supplyAsync playerData
                    } else if (response.code == 404) {
                        logger.debug("Player $username not found in Radium (404)")
                        return@supplyAsync null
                    } else {
                        val errorBody = response.body?.string() ?: "no body"
                        logger.warn("Error fetching player profile for $username: HTTP ${response.code} - $errorBody")
                        return@supplyAsync null
                    }
                }
            } catch (e: Exception) {
                logger.error("Error fetching player data for $username: ${e.message}", e)
                return@supplyAsync null
            }
        }
    }

    /**
     * Test basic API connectivity to Radium
     */
    fun testApiConnectivity(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/health")
                logger.debug("Testing Radium API connectivity: $baseUrl/health")
                httpClient.newCall(request).execute().use { response ->
                    logger.debug("Health check response: ${response.code}")
                    return@supplyAsync response.isSuccessful
                }
            } catch (e: Exception) {
                logger.error("Failed to connect to Radium API: ${e.message}")
                return@supplyAsync false
            }
        }
    }

    /**
     * Check if player has permission via API
     */
    fun hasPermission(uuid: UUID, permission: String): CompletableFuture<Boolean> {
        return getPlayerData(uuid).thenApply { playerData ->
            if (playerData == null) {
                logger.debug("No player data found for UUID: $uuid")
                return@thenApply false
            }
            
            logger.debug("Checking permission '$permission' for player ${playerData.username} (${uuid})")
            // Player permissions loaded
            
            // Check all permissions via the PlayerData hasPermission method
            val hasPermission = playerData.hasPermission(permission)
            logger.debug("Permission check result for '$permission': $hasPermission")
            
            return@thenApply hasPermission
        }
    }
    
    /**
     * Check if player has permission by username (converts to UUID first)
     */
    fun hasPermissionByName(username: String, permission: String): CompletableFuture<Boolean> {
        return getPlayerDataByName(username).thenCompose { playerData ->
            if (playerData?.uuid != null) {
                hasPermission(playerData.uuid, permission)
            } else {
                CompletableFuture.completedFuture(false)
            }
        }
    }
    
    /**
     * Helper method to check if a permission exists in a list of permissions
     */
    private fun checkPermissionInList(permissions: List<String>, permission: String): Boolean {
        // Check direct permissions
        if (permissions.contains(permission)) return true
        
        // Check wildcard permissions
        val parts = permission.split(".")
        for (i in parts.indices) {
            val wildcard = parts.take(i + 1).joinToString(".") + ".*"
            if (permissions.contains(wildcard)) return true
        }
        
        // Check for global permission
        return permissions.contains("*")
    }
    
    /**
     * Get player's highest rank
     */
    fun getPlayerRank(uuid: UUID): CompletableFuture<String?> {
        return getPlayerData(uuid).thenApply { playerData ->
            playerData?.highestRank
        }
    }
    
    /**
     * Get player's display name with rank prefix
     */
    fun getDisplayName(uuid: UUID): CompletableFuture<String> {
        return getPlayerData(uuid).thenApply { playerData ->
            playerData?.displayName ?: "Player"
        }
    }
    
    /**
     * Get all ranks from API
     */
    fun getAllRanks(): CompletableFuture<List<RankData>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/ranks")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyList()
                        val jsonNode = objectMapper.readTree(body)
                        val ranksArray = jsonNode.get("ranks")
                        
                        val ranks = mutableListOf<RankData>()
                        ranksArray?.forEach { rankNode ->
                            ranks.add(parseRankData(rankNode))
                        }
                        
                        return@supplyAsync ranks.sortedByDescending { it.weight }
                    }
                    return@supplyAsync emptyList()
                }
            } catch (e: Exception) {
                println("Error fetching ranks: ${e.message}")
                return@supplyAsync emptyList()
            }
        }
    }
    
    /**
     * Get specific rank data
     */
    fun getRank(rankName: String): CompletableFuture<RankData?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/ranks/$rankName")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync null
                        val jsonNode = objectMapper.readTree(body)
                        return@supplyAsync parseRankData(jsonNode)
                    } else if (response.code == 404) {
                        return@supplyAsync null
                    }
                    return@supplyAsync null
                }
            } catch (e: Exception) {
                println("Error fetching rank $rankName: ${e.message}")
                return@supplyAsync null
            }
        }
    }
    
    /**
     * Send a message to a player via API
     */
    fun sendMessage(playerName: String, message: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val json = objectMapper.writeValueAsString(mapOf(
                    "from" to "lobby",
                    "to" to playerName,
                    "message" to message
                ))
                
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/players/message")
                    .post(requestBody)
                    .let { builder ->
                        apiKey?.let { key ->
                            builder.header("Authorization", "Bearer $key")
                        } ?: builder
                    }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    return@supplyAsync response.isSuccessful
                }
            } catch (e: Exception) {
                println("Error sending message to $playerName: ${e.message}")
                return@supplyAsync false
            }
        }
    }
    
    /**
     * Transfer a player to another server via API
     */
    fun transferPlayer(playerName: String, serverName: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val json = objectMapper.writeValueAsString(mapOf(
                    "player" to playerName,
                    "server" to serverName
                ))
                
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/players/transfer")
                    .post(requestBody)
                    .let { builder ->
                        apiKey?.let { key ->
                            builder.header("Authorization", "Bearer $key")
                        } ?: builder
                    }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    return@supplyAsync response.isSuccessful
                }
            } catch (e: Exception) {
                println("Error transferring player $playerName to $serverName: ${e.message}")
                return@supplyAsync false
            }
        }
    }
    
    /**
     * Get server list and player counts
     */
    fun getServerList(): CompletableFuture<List<ServerData>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/servers")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyList()
                        val jsonNode = objectMapper.readTree(body)
                        val serversArray = jsonNode.get("servers")
                        
                        val servers = mutableListOf<ServerData>()
                        serversArray?.forEach { serverNode ->
                            val name = serverNode.get("name")?.asText() ?: ""
                            val playerCount = serverNode.get("playerCount")?.asInt() ?: 0
                            val isOnline = serverNode.get("isOnline")?.asBoolean() ?: false
                            servers.add(ServerData(name, playerCount, isOnline))
                        }
                        
                        return@supplyAsync servers
                    }
                    return@supplyAsync emptyList()
                }
            } catch (e: Exception) {
                println("Error fetching server list: ${e.message}")
                return@supplyAsync emptyList()
            }
        }
    }
    
    /**
     * Invalidate player cache
     */
    fun invalidatePlayer(uuid: UUID) {
        playerCache.remove(uuid)
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        playerCache.clear()
    }
    
    // Player sync methods for join/leave events
    fun syncPlayerOnJoin(player: Player) {

        
        // Check if player exists in Radium
        getPlayerData(player.uuid).thenAccept { playerData ->
            if (playerData == null) {
                logger.info("Player ${player.username} not found in Radium - they need to connect to Radium proxy first")
                // Player doesn't exist in Radium yet - they need to connect to the Radium proxy to be created
            } else {

            }
        }.exceptionally { throwable ->
            logger.error("Error syncing player ${player.username}: ${throwable.message}")
            null
        }
    }
    
    fun syncPlayerOnLeave(player: Player) {
        // TODO: Implement player sync on leave  

    }
    
    // Chat formatting method
    fun formatChatMessage(player: Player, message: String): String {
        val cachedData = playerCache[player.uuid]
        
        return if (cachedData != null && cachedData.rank != null) {
            // Use rank prefix only (color is included in prefix)
            val prefix = cachedData.rank.prefix.ifEmpty { "" }
            "$prefix${cachedData.username}&7: &f$message"
        } else {
            try {
                // Try to use cached data only - avoid blocking calls
                val cachedPlayerData = playerCache[player.uuid]
                if (cachedPlayerData != null && cachedPlayerData.rank != null) {
                    val prefix = cachedPlayerData.rank.prefix.ifEmpty { "" }
                    "$prefix${cachedPlayerData.username}&7: &f$message"
                } else {
                    // Use fallback from config
                    val fallbackPrefix = configManager.getString(configManager.mainConfig, "radium.fallback.default_prefix", "&7")
                    "$fallbackPrefix${player.username}&7: &f$message"
                }
            } catch (e: Exception) {
                logger.warn("Failed to format chat for ${player.username}, using fallback", e)
                // Ultimate fallback
                "&7${player.username}&7: &f$message"
            }
        }
    }
    
    /**
     * Get formatted tab list name for a player
     */
    fun getTabListName(player: Player): String {
        return try {
            // Use cached data only - avoid blocking calls
            val cachedData = playerCache[player.uuid]
            if (cachedData != null && cachedData.rank != null) {
                val prefix = cachedData.rank.prefix.ifEmpty { "" }
                "$prefix${player.username}"
            } else {
                // Fallback formatting
                val fallbackPrefix = configManager.getString(configManager.mainConfig, "radium.fallback.default_prefix", "&7")
                "$fallbackPrefix${player.username}"
            }
        } catch (e: Exception) {
            logger.warn("Failed to get tab list name for ${player.username}, using fallback", e)
            player.username
        }
    }

    /**
     * Check if a player is currently vanished
     */
    fun isPlayerVanished(playerUuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/players/uuid/${playerUuid}/vanish")
                logger.debug("Checking vanish status for $playerUuid at: $baseUrl/players/uuid/${playerUuid}/vanish")
                httpClient.newCall(request).execute().use { response ->
                    logger.debug("Vanish API response code: ${response.code}")
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        logger.debug("Vanish API response body: $responseBody")
                        val jsonNode = objectMapper.readTree(responseBody)
                        
                        // Parse vanish status from response
                        val vanishStatus = jsonNode.get("vanished")?.asBoolean() ?: false
                            
                        logger.debug("Parsed vanish status for $playerUuid: $vanishStatus")
                        return@supplyAsync vanishStatus
                    } else if (response.code == 404) {
                        // 404 means player is not online on the proxy, so they can't be vanished
                        logger.debug("Player $playerUuid not found on proxy (404) - treating as not vanished")
                        return@supplyAsync false
                    } else {
                        val errorBody = response.body?.string() ?: "No body"
                        logger.warn("Failed to check vanish status for $playerUuid: HTTP ${response.code} - $errorBody")
                        return@supplyAsync false
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to check vanish status for $playerUuid", e)
                return@supplyAsync false
            }
        }
    }


    /**
     * Check if a viewer can see a vanished player
     */
    fun canSeeVanishedPlayer(viewerUuid: UUID, vanishedPlayerUuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/players/uuid/${viewerUuid}/can-see-vanished/${vanishedPlayerUuid}")
                logger.debug("Checking vanish visibility for viewer $viewerUuid -> target $vanishedPlayerUuid")
                httpClient.newCall(request).execute().use { response ->
                    logger.debug("Vanish visibility API response code: ${response.code}")
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        logger.debug("Vanish visibility API response body: $responseBody")
                        val jsonNode = objectMapper.readTree(responseBody)
                        
                        // Parse visibility result from response
                        val canSee = jsonNode.get("canSee")?.asBoolean() ?: false
                            
                        logger.debug("Parsed visibility result for $viewerUuid -> $vanishedPlayerUuid: $canSee")
                        return@supplyAsync canSee
                    } else if (response.code == 404) {
                        // 404 means one or both players are not online on the proxy
                        logger.debug("Player not found on proxy (404) for visibility check $viewerUuid -> $vanishedPlayerUuid - defaulting to false")
                        return@supplyAsync false
                    } else {
                        val errorBody = response.body?.string() ?: "No body"
                        logger.warn("Failed to check vanish visibility for $viewerUuid -> $vanishedPlayerUuid: HTTP ${response.code} - $errorBody")
                        return@supplyAsync false
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to check vanish visibility for $viewerUuid -> $vanishedPlayerUuid", e)
                return@supplyAsync false
            }
        }
    }
    
    private fun buildGetRequest(endpoint: String): Request {
        val requestBuilder = Request.Builder()
            .url("$baseUrl$endpoint")
            .get()
        
        // Add API key if configured
        apiKey?.let { key ->
            requestBuilder.header("Authorization", "Bearer $key")
        }
        
        return requestBuilder.build()
    }
    
    private fun parsePlayerDataFromProfile(jsonNode: JsonNode): PlayerData {
        val username = jsonNode.get("username")?.asText() ?: "Unknown"
        val uuid = jsonNode.get("uuid")?.asText()?.let { 
            try { UUID.fromString(it) } catch (e: Exception) { null }
        }
        
        val rankNode = jsonNode.get("rank")
        val rankData = if (rankNode != null && !rankNode.isNull) {
            parseRankData(rankNode)
        } else null
        
        val permissions = mutableListOf<String>()
        jsonNode.get("permissions")?.forEach { permNode ->
            permissions.add(permNode.asText())
        }
        
        val displayName = if (rankData != null) {
            "${rankData.prefix}$username"
        } else {
            username
        }
        
        val highestRankName: String = (rankData?.name as? String) ?: "Default"
        
        return PlayerData(
            uuid = uuid,
            username = username,
            rank = rankData,
            permissions = permissions,
            highestRank = highestRankName,
            displayName = displayName,
            cachedAt = Instant.now()
        )
    }
    
    private fun parseRankData(jsonNode: JsonNode): RankData {
        val name = jsonNode.get("name")?.asText() ?: ""
        val weight = jsonNode.get("weight")?.asInt() ?: 0
        val prefix = jsonNode.get("prefix")?.asText() ?: ""
        val color = jsonNode.get("color")?.asText() ?: "&f"
        val nametag = jsonNode.get("nameTag")?.asText() // Note: Radium uses "nameTag" in JSON
        
        val permissions = mutableListOf<String>()
        jsonNode.get("permissions")?.forEach { permNode ->
            permissions.add(permNode.asText())
        }
        
        return RankData(
            name = name,
            weight = weight,
            prefix = prefix,
            color = color,
            permissions = permissions,
            nametag = nametag
        )
    }
    
    data class PlayerData(
        val uuid: UUID?,
        val username: String,
        val rank: RankData?,
        val permissions: List<String>,
        val highestRank: String,
        val displayName: String,
        val cachedAt: Instant = Instant.now()
    ) {
        fun hasPermission(permission: String): Boolean {
            // Check rank permissions first
            rank?.let { rankData ->
                if (rankData.hasPermission(permission)) return true
            }
            
            // Check direct permissions
            if (permissions.contains(permission)) return true
            
            // Check wildcard permissions
            val parts = permission.split(".")
            for (i in parts.indices) {
                val wildcard = parts.subList(0, i + 1).joinToString(".") + ".*"
                if (permissions.contains(wildcard)) return true
            }
            
            // Check for admin wildcard
            return permissions.contains("*")
        }
    }
    
    data class RankData(
        val name: String,
        val weight: Int,
        val prefix: String,
        val color: String,
        val permissions: List<String>,
        val nametag: String? = null
    ) {
        fun hasPermission(permission: String): Boolean {
            // Check direct permissions
            if (permissions.contains(permission)) return true
            
            // Check wildcard permissions
            val parts = permission.split(".")
            for (i in parts.indices) {
                val wildcard = parts.subList(0, i + 1).joinToString(".") + ".*"
                if (permissions.contains(wildcard)) return true
            }
            
            // Check for admin wildcard
            return permissions.contains("*")
        }
    }
    
    data class ServerData(
        val name: String,
        val playerCount: Int,
        val isOnline: Boolean
    )

    data class StaffMember(
        val uuid: UUID,
        val username: String,
        val server: String
    )

    /**
     * Make a GET request to the Radium API
     * This method is exposed for use by other services like PunishmentService
     */
    fun makeApiRequest(endpoint: String): Response {
        val request = buildGetRequest(endpoint)
        return httpClient.newCall(request).execute()
    }

    /**
     * Get player's rank weight for punishment validation
     */
    fun getRankWeight(uuid: UUID): CompletableFuture<Int> {
        return getPlayerData(uuid).thenApply { playerData ->
            playerData?.rank?.weight ?: 0
        }
    }

    /**
     * Get player UUID by username from Radium
     */
    fun getPlayerUuidByName(playerName: String): CompletableFuture<UUID?> {
        return getPlayerDataByName(playerName).thenApply { playerData ->
            playerData?.uuid
        }
    }
    
    // Reports and Requests API methods
    
    data class ReportsApiResponse(
        val success: Boolean,
        val message: String?,
        val data: Any? = null
    ) {
        data class RequestData(
            val id: String,
            val playerId: UUID,
            val playerName: String,
            val type: String,
            val subject: String,
            val description: String,
            val timestamp: Instant
        )
    }
    
    /**
     * Create a report via Radium API
     */
    fun createReport(
        reporterId: String,
        reporterName: String,
        targetId: String,
        targetName: String,
        reason: String,
        description: String,
        serverName: String
    ): CompletableFuture<ReportsApiResponse> {
        return CompletableFuture.supplyAsync {
            try {
                val requestData = mapOf(
                    "reporterId" to reporterId,
                    "reporterName" to reporterName,
                    "targetId" to targetId,
                    "targetName" to targetName,
                    "reason" to reason,
                    "description" to description,
                    "serverName" to serverName
                )
                
                val json = objectMapper.writeValueAsString(requestData)
                val request = buildPostRequest("/reports/create", json)
                
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val responseData = objectMapper.readTree(body)
                    
                    if (response.isSuccessful) {
                        ReportsApiResponse(
                            success = responseData.get("success")?.asBoolean() ?: true,
                            message = responseData.get("message")?.asText(),
                            data = responseData.get("data")
                        )
                    } else {
                        ReportsApiResponse(
                            success = false,
                            message = responseData.get("message")?.asText() ?: "API request failed",
                            data = null
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to create report via API", e)
                ReportsApiResponse(
                    success = false,
                    message = "Connection error: ${e.message}",
                    data = null
                )
            }
        }
    }
    
    /**
     * Create a request via Radium API
     */
    fun createRequest(
        playerId: String,
        playerName: String,
        type: String,
        subject: String,
        description: String,
        serverName: String
    ): CompletableFuture<ReportsApiResponse> {
        return CompletableFuture.supplyAsync {
            try {
                val requestData = mapOf(
                    "playerId" to playerId,
                    "playerName" to playerName,
                    "type" to type,
                    "subject" to subject,
                    "description" to description,
                    "serverName" to serverName
                )
                
                val json = objectMapper.writeValueAsString(requestData)
                val request = buildPostRequest("/requests/create", json)
                
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val responseData = objectMapper.readTree(body)
                    
                    if (response.isSuccessful) {
                        ReportsApiResponse(
                            success = responseData.get("success")?.asBoolean() ?: true,
                            message = responseData.get("message")?.asText(),
                            data = responseData.get("data")
                        )
                    } else {
                        ReportsApiResponse(
                            success = false,
                            message = responseData.get("message")?.asText() ?: "API request failed",
                            data = null
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to create request via API", e)
                ReportsApiResponse(
                    success = false,
                    message = "Connection error: ${e.message}",
                    data = null
                )
            }
        }
    }
    
    /**
     * Get reports for a player via Radium API
     */
    fun getReportsForPlayer(playerId: String): CompletableFuture<List<Map<String, Any>>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/reports/player/$playerId")
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val responseData = objectMapper.readTree(body)
                        
                        if (responseData.get("success")?.asBoolean() == true) {
                            val dataNode = responseData.get("data")
                            if (dataNode?.isArray == true) {
                                return@supplyAsync dataNode.map { node ->
                                    objectMapper.convertValue(node, Map::class.java) as Map<String, Any>
                                }
                            }
                        }
                    }
                    emptyList()
                }
            } catch (e: Exception) {
                logger.error("Failed to get reports for player via API", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get requests for a player via Radium API
     */
    fun getRequestsForPlayer(playerId: String): CompletableFuture<List<Map<String, Any>>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/requests/player/$playerId")
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val responseData = objectMapper.readTree(body)
                        
                        if (responseData.get("success")?.asBoolean() == true) {
                            val dataNode = responseData.get("data")
                            if (dataNode?.isArray == true) {
                                return@supplyAsync dataNode.map { node ->
                                    objectMapper.convertValue(node, Map::class.java) as Map<String, Any>
                                }
                            }
                        }
                    }
                    emptyList()
                }
            } catch (e: Exception) {
                logger.error("Failed to get requests for player via API", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get reports by status via Radium API
     */
    fun getReportsByStatus(status: String): CompletableFuture<List<Map<String, Any>>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/reports/status/$status")
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val responseData = objectMapper.readTree(body)
                        
                        if (responseData.get("success")?.asBoolean() == true) {
                            val dataNode = responseData.get("data")
                            if (dataNode?.isArray == true) {
                                return@supplyAsync dataNode.map { node ->
                                    objectMapper.convertValue(node, Map::class.java) as Map<String, Any>
                                }
                            }
                        }
                    }
                    emptyList()
                }
            } catch (e: Exception) {
                logger.error("Failed to get reports by status via API", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get panic players from Radium
     */
    fun getPanicPlayers(): CompletableFuture<Map<UUID, PanicPlayerData>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/panic/players")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyMap()
                        val jsonNode = objectMapper.readTree(body)
                        val playersArray = jsonNode.get("panic_players") ?: jsonNode.get("players") ?: return@supplyAsync emptyMap()
                        
                        val panicMap = mutableMapOf<UUID, PanicPlayerData>()
                        playersArray.forEach { playerNode ->
                            try {
                                val uuid = UUID.fromString(playerNode.get("uuid")?.asText() ?: return@forEach)
                                val username = playerNode.get("username")?.asText() ?: "Unknown"
                                val activationTime = playerNode.get("activation_time")?.asLong() ?: System.currentTimeMillis()
                                val reason = playerNode.get("reason")?.asText()
                                
                                panicMap[uuid] = PanicPlayerData(username, activationTime, reason)
                            } catch (e: Exception) {
                                logger.warn("Failed to parse panic player data: ${e.message}")
                            }
                        }
                        
                        logger.debug("Retrieved ${panicMap.size} panic players from Radium")
                        return@supplyAsync panicMap
                    } else {
                        logger.debug("Failed to get panic players from Radium: HTTP ${response.code}")
                        return@supplyAsync emptyMap()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error fetching panic players from Radium", e)
                return@supplyAsync emptyMap()
            }
        }
    }
    
    /**
     * Data class for panic player information from Radium
     */
    data class PanicPlayerData(
        val username: String,
        val activationTime: Long,
        val reason: String? = null
    )
    
    private fun buildPostRequest(endpoint: String, jsonBody: String): Request {
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url("$baseUrl$endpoint")
            .post(requestBody)
        
        // Add API key if configured
        apiKey?.let { key ->
            requestBuilder.header("Authorization", "Bearer $key")
        }
        
        return requestBuilder.build()
    }

    /**
     * Get online staff members from Radium
     */
    fun getOnlineStaff(): CompletableFuture<List<StaffMember>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/staff/online")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyList()
                        val jsonNode = objectMapper.readTree(body)
                        val staffArray = jsonNode.get("staff")
                        
                        val staffList = mutableListOf<StaffMember>()
                        staffArray?.forEach { staffNode ->
                            val uuid = staffNode.get("uuid")?.asText()?.let { 
                                try { UUID.fromString(it) } catch (e: Exception) { null }
                            }
                            val username = staffNode.get("username")?.asText()
                            val server = staffNode.get("server")?.asText()
                            
                            if (uuid != null && username != null) {
                                staffList.add(StaffMember(uuid, username, server ?: "Unknown"))
                            }
                        }
                        
                        return@supplyAsync staffList
                    }
                    return@supplyAsync emptyList()
                }
            } catch (e: Exception) {
                logger.error("Failed to get online staff via API", e)
                emptyList()
            }
        }
    }
    
    /**
     * Broadcast message to all staff
     */
    fun broadcastStaffMessage(message: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                if (baseUrl.isEmpty()) {
                    logger.warn("Cannot broadcast staff message: baseUrl is not configured")
                    return@supplyAsync false
                }
                
                // For system messages like freeze/unfreeze, send the message directly without "System:" prefix
                // The message already contains the proper format with &b[SC] prefix
                val data = mapOf(
                    "sender_name" to "SYSTEM_DIRECT",  // Special identifier for direct system messages
                    "message" to message
                )
                
                val json = objectMapper.writeValueAsString(data)
                logger.debug("Broadcasting staff message to $baseUrl/chat/staff: $json")
                
                val request = buildPostRequest("/chat/staff", json)
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    
                    if (response.isSuccessful) {
                        logger.debug("Staff message broadcast successful: ${response.code} - Response: $responseBody")
                        
                        // Try to parse recipients count from response
                        try {
                            val responseData = objectMapper.readTree(responseBody)
                            val recipients = responseData.get("recipients")?.asInt() ?: 0
                            logger.info("Staff message delivered to $recipients recipients")
                        } catch (e: Exception) {
                            logger.debug("Could not parse recipients count from response: $responseBody")
                        }
                        
                        true
                    } else {
                        logger.warn("Staff message broadcast failed: ${response.code} - ${response.message} - Response: $responseBody")
                        
                        // Additional debugging for common issues
                        when (response.code) {
                            401 -> logger.warn("API authentication failed - check API key configuration")
                            403 -> logger.warn("API authorization failed - check sender permissions")
                            404 -> logger.warn("API endpoint not found - check URL: $baseUrl/chat/staff")
                            500 -> logger.warn("Radium server error - check Radium server logs")
                        }
                        
                        false
                    }
                }
            } catch (e: java.net.ConnectException) {
                logger.error("Failed to connect to Radium server at $baseUrl - is the server running?", e)
                false
            } catch (e: java.net.SocketTimeoutException) {
                logger.error("Timeout connecting to Radium server at $baseUrl", e)
                false
            } catch (e: Exception) {
                logger.error("Failed to broadcast staff message to $baseUrl/chat/staff", e)
                false
            }
        }
    }
    
    /**
     * Get player credits
     */
    fun getPlayerCredits(playerUuid: UUID): CompletableFuture<Int?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/credits/$playerUuid")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync null
                        val jsonNode = objectMapper.readTree(body)
                        jsonNode.get("credits")?.asInt()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to get player credits", e)
                null
            }
        }
    }
    
    /**
     * Set player credits
     */
    fun setPlayerCredits(playerUuid: UUID, amount: Int): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val data = mapOf("credits" to amount)
                val json = objectMapper.writeValueAsString(data)
                val request = buildPostRequest("/credits/$playerUuid", json)
                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                logger.error("Failed to set player credits", e)
                false
            }
        }
    }
    
    /**
     * Sync player credits across network
     */
    fun syncPlayerCreditsAcrossNetwork(playerUuid: UUID, newBalance: Int): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val data = mapOf(
                    "type" to "credits_sync",
                    "player_uuid" to playerUuid.toString(),
                    "credits" to newBalance,
                    "timestamp" to System.currentTimeMillis()
                )
                
                val json = objectMapper.writeValueAsString(data)
                val request = buildPostRequest("/sync/credits", json)
                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                logger.error("Failed to sync credits across network", e)
                false
            }
        }
    }

    /**
     * Send admin chat message to Radium
     */
    suspend fun sendAdminChatMessage(player: Player, message: String): Boolean {
        return try {
            val data = mapOf(
                "sender_uuid" to player.uuid.toString(),
                "sender_name" to player.username,
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
            
            val json = objectMapper.writeValueAsString(data)
            val request = buildPostRequest("/chat/admin", json)
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    logger.debug("Admin chat message sent successfully")
                    true
                } else {
                    logger.error("Failed to send admin chat message: HTTP ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to send admin chat message", e)
            false
        }
    }

    /**
     * Send vanish toggle request to Radium
     */
    fun sendVanishToggle(playerUuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                // First, get the player's username
                val playerData = getPlayerData(playerUuid).join()
                if (playerData == null) {
                    logger.error("Failed to get player data for vanish toggle: $playerUuid")
                    return@supplyAsync false
                }
                
                val username = playerData.username
                
                // Get current vanish status
                val currentVanishStatus = isPlayerVanished(playerUuid).join()
                val newVanishStatus = !currentVanishStatus
                
                logger.debug("Toggling vanish for $username ($playerUuid): $currentVanishStatus -> $newVanishStatus")
                
                // Prepare vanish request
                val vanishRequest = mapOf(
                    "player" to username,
                    "vanished" to newVanishStatus
                )
                
                val json = objectMapper.writeValueAsString(vanishRequest)
                val request = buildPostRequest("/players/$username/vanish", json)
                
                logger.debug("Sending vanish toggle request to: $baseUrl/players/$username/vanish")
                logger.debug("Request payload: $json")
                
                httpClient.newCall(request).execute().use { response ->
                    logger.debug("Vanish toggle response: HTTP ${response.code}")
                    
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        logger.debug("Vanish toggle response body: $responseBody")
                        
                        // Parse response to confirm success
                        val jsonNode = objectMapper.readTree(responseBody)
                        val success = jsonNode.get("success")?.asBoolean() ?: false
                        val resultVanished = jsonNode.get("vanished")?.asBoolean() ?: false
                        
                        if (success && resultVanished == newVanishStatus) {
                            logger.debug("Vanish toggle successful for $username: now $resultVanished")
                            return@supplyAsync true
                        } else {
                            logger.warn("Vanish toggle failed for $username: success=$success, expected=$newVanishStatus, got=$resultVanished")
                            return@supplyAsync false
                        }
                    } else {
                        val errorBody = response.body?.string() ?: "No response body"
                        logger.error("Vanish toggle failed: HTTP ${response.code} - $errorBody")
                        return@supplyAsync false
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to send vanish toggle request for $playerUuid", e)
                return@supplyAsync false
            }
        }
    }

    /**
     * Get reports for a specific player
     */
    fun getReportsForPlayer(playerId: UUID): CompletableFuture<List<PlayerReport>?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/reports/player/$playerId")
                logger.debug("Fetching reports for player: $playerId")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyList()
                        val jsonNode = objectMapper.readTree(body)
                        
                        if (jsonNode.get("success")?.asBoolean() == true) {
                            val dataNode = jsonNode.get("data")
                            if (dataNode?.isArray == true) {
                                val reports = mutableListOf<PlayerReport>()
                                dataNode.forEach { reportNode ->
                                    try {
                                        val report = PlayerReport(
                                            id = reportNode.get("id")?.asText() ?: "",
                                            reporterName = reportNode.get("reporterName")?.asText() ?: "Unknown",
                                            targetName = reportNode.get("targetName")?.asText() ?: "Unknown",
                                            reason = reportNode.get("reason")?.asText() ?: "",
                                            description = reportNode.get("description")?.asText() ?: "",
                                            serverName = reportNode.get("serverName")?.asText() ?: "",
                                            timestamp = reportNode.get("timestamp")?.asText() ?: "",
                                            status = reportNode.get("status")?.asText() ?: "PENDING",
                                            handlerName = reportNode.get("handlerName")?.asText(),
                                            resolution = reportNode.get("resolution")?.asText()
                                        )
                                        reports.add(report)
                                    } catch (e: Exception) {
                                        logger.warn("Failed to parse report from JSON: ${e.message}")
                                    }
                                }
                                return@supplyAsync reports
                            }
                        }
                        return@supplyAsync emptyList()
                    } else if (response.code == 404) {
                        logger.debug("No reports found for player: $playerId")
                        return@supplyAsync emptyList()
                    } else {
                        val errorBody = response.body?.string() ?: "no body"
                        logger.warn("Error fetching reports for player $playerId: HTTP ${response.code} - $errorBody")
                        return@supplyAsync null
                    }
                }
            } catch (e: Exception) {
                logger.error("Error fetching reports for player $playerId: ${e.message}", e)
                return@supplyAsync null
            }
        }
    }

    /**
     * Update the status of a report
     */
    fun updateReportStatus(
        reportId: String,
        status: String,
        handlerId: String,
        handlerName: String,
        resolution: String? = null
    ): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val requestBody = mapOf(
                    "reportId" to reportId,
                    "status" to status,
                    "handlerId" to handlerId,
                    "handlerName" to handlerName,
                    "resolution" to resolution
                )
                
                val json = objectMapper.writeValueAsString(requestBody)
                val request = Request.Builder()
                    .url("$baseUrl/reports/update")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .apply {
                        apiKey?.let { addHeader("Authorization", "Bearer $it") }
                    }
                    .build()
                
                logger.debug("Updating report status: $reportId to $status")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync false
                        val jsonNode = objectMapper.readTree(body)
                        return@supplyAsync jsonNode.get("success")?.asBoolean() ?: false
                    } else {
                        val errorBody = response.body?.string() ?: "no body"
                        logger.warn("Error updating report status $reportId: HTTP ${response.code} - $errorBody")
                        return@supplyAsync false
                    }
                }
            } catch (e: Exception) {
                logger.error("Error updating report status $reportId: ${e.message}", e)
                return@supplyAsync false
            }
        }
    }

    /**
     * Get requests for a specific player
     */
    fun getRequestsForPlayer(playerId: UUID): CompletableFuture<List<PlayerRequest>?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/requests/player/$playerId")
                logger.debug("Fetching requests for player: $playerId")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyList()
                        val jsonNode = objectMapper.readTree(body)
                        
                        if (jsonNode.get("success")?.asBoolean() == true) {
                            val dataNode = jsonNode.get("data")
                            if (dataNode?.isArray == true) {
                                val requests = mutableListOf<PlayerRequest>()
                                dataNode.forEach { requestNode ->
                                    try {
                                        val request = PlayerRequest(
                                            id = requestNode.get("id")?.asText() ?: "",
                                            playerName = requestNode.get("playerName")?.asText() ?: "Unknown",
                                            type = requestNode.get("type")?.asText() ?: "",
                                            subject = requestNode.get("subject")?.asText() ?: "",
                                            description = requestNode.get("description")?.asText() ?: "",
                                            serverName = requestNode.get("serverName")?.asText() ?: "",
                                            timestamp = requestNode.get("timestamp")?.asText() ?: "",
                                            status = requestNode.get("status")?.asText() ?: "PENDING",
                                            handlerName = requestNode.get("handlerName")?.asText(),
                                            response = requestNode.get("response")?.asText()
                                        )
                                        requests.add(request)
                                    } catch (e: Exception) {
                                        logger.warn("Failed to parse request from JSON: ${e.message}")
                                    }
                                }
                                return@supplyAsync requests
                            }
                        }
                        return@supplyAsync emptyList()
                    } else if (response.code == 404) {
                        logger.debug("No requests found for player: $playerId")
                        return@supplyAsync emptyList()
                    } else {
                        val errorBody = response.body?.string() ?: "no body"
                        logger.warn("Error fetching requests for player $playerId: HTTP ${response.code} - $errorBody")
                        return@supplyAsync null
                    }
                }
            } catch (e: Exception) {
                logger.error("Error fetching requests for player $playerId: ${e.message}", e)
                return@supplyAsync null
            }
        }
    }

    /**
     * Update the status of a request
     */
    fun updateRequestStatus(
        requestId: String,
        status: String,
        handlerId: String,
        handlerName: String,
        response: String? = null
    ): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val requestBody = mapOf(
                    "requestId" to requestId,
                    "status" to status,
                    "handlerId" to handlerId,
                    "handlerName" to handlerName,
                    "response" to response
                )
                
                val json = objectMapper.writeValueAsString(requestBody)
                val request = Request.Builder()
                    .url("$baseUrl/requests/update")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .apply {
                        apiKey?.let { addHeader("Authorization", "Bearer $it") }
                    }
                    .build()
                
                logger.debug("Updating request status: $requestId to $status")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync false
                        val jsonNode = objectMapper.readTree(body)
                        return@supplyAsync jsonNode.get("success")?.asBoolean() ?: false
                    } else {
                        val errorBody = response.body?.string() ?: "no body"
                        logger.warn("Error updating request status $requestId: HTTP ${response.code} - $errorBody")
                        return@supplyAsync false
                    }
                }
            } catch (e: Exception) {
                logger.error("Error updating request status $requestId: ${e.message}", e)
                return@supplyAsync false
            }
        }
    }

    data class PlayerReport(
        val id: String,
        val reporterName: String,
        val targetName: String,
        val reason: String,
        val description: String,
        val serverName: String,
        val timestamp: String,
        val status: String,
        val handlerName: String? = null,
        val resolution: String? = null
    )

    data class PlayerRequest(
        val id: String,
        val playerName: String,
        val type: String,
        val subject: String,
        val description: String,
        val serverName: String,
        val timestamp: String,
        val status: String,
        val handlerName: String? = null,
        val response: String? = null
    )
}
