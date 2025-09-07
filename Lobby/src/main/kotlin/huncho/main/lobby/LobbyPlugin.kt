package huncho.main.lobby

import huncho.main.lobby.config.ConfigManager
import huncho.main.lobby.api.GamemodeAPI
import huncho.main.lobby.api.RadiumCommandForwarder
import huncho.main.lobby.api.RadiumPunishmentAPI
import huncho.main.lobby.listeners.EventManager
import huncho.main.lobby.commands.CommandManager
import huncho.main.lobby.features.queue.QueueManager
import huncho.main.lobby.features.scoreboard.ScoreboardManager
import huncho.main.lobby.features.visibility.VisibilityManager
import huncho.main.lobby.features.spawn.SpawnManager
import huncho.main.lobby.features.protection.ProtectionManager
import huncho.main.lobby.features.world.WorldLightingManager
import huncho.main.lobby.features.tablist.TabListManager
import huncho.main.lobby.features.nametags.NametagManager
import huncho.main.lobby.features.vanish.VanishStatusMonitor
import huncho.main.lobby.features.vanish.VanishEventListener
import huncho.main.lobby.features.vanish.PacketVanishManager
import huncho.main.lobby.features.reports.ReportsManager
import huncho.main.lobby.listeners.VanishPluginMessageListener
import huncho.main.lobby.listeners.GamemodePluginMessageListener
import huncho.main.lobby.managers.SchematicManager
import huncho.main.lobby.integration.RadiumIntegration
import huncho.main.lobby.redis.RedisManager
import huncho.main.lobby.redis.RedisCache
import huncho.main.lobby.redis.RedisEventListener
import huncho.main.lobby.services.PunishmentService
import kotlinx.coroutines.*
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.coordinate.Pos
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

object LobbyPlugin {
    
    val logger: Logger = LoggerFactory.getLogger(LobbyPlugin::class.java)
    
    @JvmStatic
    fun main(args: Array<String>) {
        initialize()
    }
    
    // Core Managers
    lateinit var configManager: ConfigManager
    lateinit var eventManager: EventManager
    lateinit var commandManager: CommandManager
    
    // HTTP API
    lateinit var gamemodeAPI: GamemodeAPI
    
    // Radium Command Forwarding
    lateinit var radiumCommandForwarder: RadiumCommandForwarder
    
    // Radium Punishment API
    lateinit var radiumPunishmentAPI: RadiumPunishmentAPI
    
    // Feature Managers
    lateinit var queueManager: QueueManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var visibilityManager: VisibilityManager
    lateinit var spawnManager: SpawnManager
    lateinit var protectionManager: ProtectionManager
    lateinit var worldLightingManager: WorldLightingManager
    lateinit var tabListManager: TabListManager
    lateinit var nametagManager: NametagManager
    lateinit var vanishStatusMonitor: VanishStatusMonitor
    lateinit var vanishEventListener: VanishEventListener
    lateinit var vanishPluginMessageListener: VanishPluginMessageListener
    lateinit var gamemodePluginMessageListener: GamemodePluginMessageListener
    lateinit var packetVanishManager: PacketVanishManager
    lateinit var schematicManager: SchematicManager
    lateinit var reportsManager: ReportsManager
    
    // New Feature Managers
    lateinit var freezeManager: huncho.main.lobby.features.freeze.FreezeManager
    lateinit var panicManager: huncho.main.lobby.features.panic.PanicManager
    lateinit var creditsManager: huncho.main.lobby.features.credits.CreditsManager
    lateinit var adminCommandManager: huncho.main.lobby.features.admin.AdminCommandManager
    lateinit var staffModeManager: huncho.main.lobby.features.staffmode.StaffModeManager
    
    // Integration
    lateinit var radiumIntegration: RadiumIntegration
    lateinit var headsIntegration: huncho.main.lobby.integration.HeadsIntegration
    
    // Redis Integration
    var redisManager: RedisManager? = null
    var redisCache: RedisCache? = null
    var redisEventListener: RedisEventListener? = null
    var punishmentService: PunishmentService? = null
    
    // Lobby Instance
    lateinit var lobbyInstance: InstanceContainer
    
    // Coroutine scope for async operations
    val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun initialize() {
        logger.info("Starting MythicPvP Lobby...")
        
        try {
            // Initialize core managers
            initializeCore()
            
            // Setup lobby world first
            setupLobbyWorld()
            
            // Initialize features (now that lobbyInstance is ready)
            initializeFeatures()
            
            // Initialize schematics and paste startup schematics
            coroutineScope.launch {
                try {
                    schematicManager.initialize()
                    schematicManager.pasteStartupSchematics(lobbyInstance)
                } catch (e: Exception) {
                    logger.error("Failed to initialize schematics", e)
                }
            }
            
            // Start permission cache cleanup task
            startPermissionCacheCleanup()
            
            logger.info("Lobby server ready!")
        } catch (e: Exception) {
            logger.error("Failed to initialize Lobby", e)
            throw e
        }
    }
    
    fun terminate() {
        logger.info("Terminating Lobby Plugin...")
        
        try {
            // Save all data
            queueManager.saveAllQueues()
            
            // Shutdown Redis integration
            redisManager?.shutdown()
            
            // Shutdown schematic manager
            schematicManager.shutdown()
            
            // Shutdown vanish status monitor
            if (::vanishStatusMonitor.isInitialized) {
                vanishStatusMonitor.shutdown()
            }
            
            // Shutdown reports manager
            if (::reportsManager.isInitialized) {
                reportsManager.shutdown()
            }
            
            // Clear vanish plugin message listener data
            if (::vanishPluginMessageListener.isInitialized) {
                vanishPluginMessageListener.clear()
            }
            
            // Shutdown integrations
            radiumIntegration.shutdown()
            
            // Shutdown HTTP API
            gamemodeAPI.shutdown()
            
            // Cancel coroutine scope
            coroutineScope.cancel()
            
            logger.info("Lobby Plugin successfully terminated!")
        } catch (e: Exception) {
            logger.error("Error during plugin termination", e)
        }
    }
    
    private fun initializeCore() {
        // Configuration
        configManager = ConfigManager(this)
        configManager.loadAllConfigs()
        
        // HTTP API for gamemode synchronization
        gamemodeAPI = GamemodeAPI(configManager)
        gamemodeAPI.initialize()
        
        // Radium integration
        radiumIntegration = RadiumIntegration(configManager)
        radiumIntegration.initialize()
        
        // Get Radium API URL for integrations
        val radiumApiUrl = configManager.getString(
            configManager.mainConfig,
            "radium.api.base_url",
            "http://localhost:8080"
        )
        
        // Heads integration for Minecraft-Heads.com
        headsIntegration = huncho.main.lobby.integration.HeadsIntegration(radiumApiUrl)
        
        // Radium command forwarding
        radiumCommandForwarder = RadiumCommandForwarder(radiumApiUrl)
        
        // Radium punishment API (proper API endpoints)
        radiumPunishmentAPI = RadiumPunishmentAPI(radiumApiUrl)
        
        // Initialize Redis integration
        initializeRedis()
        
        // Event and command management
        eventManager = EventManager(this)
        commandManager = CommandManager(this)
    }
    
    private fun initializeFeatures() {
        // Initializing feature managers
        
        // Core features
        spawnManager = SpawnManager(this)
        protectionManager = ProtectionManager(this)
        
        // Schematic manager (requires data folder)
        val dataFolder = File("config/lobby") // Same directory as config
        schematicManager = SchematicManager(configManager, dataFolder)
        
        // Advanced features
        queueManager = QueueManager(this)
        scoreboardManager = ScoreboardManager(this)
        visibilityManager = VisibilityManager(this)
        
        // MythicHub style features
        worldLightingManager = WorldLightingManager(this)
        tabListManager = TabListManager(this)
        nametagManager = NametagManager(this)
        reportsManager = ReportsManager(this)
        vanishStatusMonitor = VanishStatusMonitor(this)
        vanishEventListener = VanishEventListener(this)
        vanishPluginMessageListener = VanishPluginMessageListener(this)
        gamemodePluginMessageListener = GamemodePluginMessageListener(this)
        packetVanishManager = PacketVanishManager(this)
        
        // Initialize the new managers
        worldLightingManager.initialize()
        tabListManager.initialize()
        nametagManager.initialize()
        reportsManager.initialize()
        vanishStatusMonitor.initialize()
        
        // Initialize new feature managers
        freezeManager = huncho.main.lobby.features.freeze.FreezeManager(this)
        panicManager = huncho.main.lobby.features.panic.PanicManager(this)
        creditsManager = huncho.main.lobby.features.credits.CreditsManager(this)
        adminCommandManager = huncho.main.lobby.features.admin.AdminCommandManager(this)
        staffModeManager = huncho.main.lobby.features.staffmode.StaffModeManager(this)
        
        freezeManager.initialize()
        panicManager.initialize()
        creditsManager.initialize()
        adminCommandManager.initialize()
        staffModeManager.initialize()
        
        // Register plugin message listener for hybrid vanish system
        val eventHandler = MinecraftServer.getGlobalEventHandler()
        eventHandler.addListener(vanishPluginMessageListener)
        eventHandler.addListener(gamemodePluginMessageListener)
        eventHandler.addListener(packetVanishManager)
        
        // Start vanish enforcement task
        vanishPluginMessageListener.startVanishEnforcementTask()
        
        // Register vanish event handlers in visibility manager
        visibilityManager.registerEvents(eventHandler)
        
        // Register all event listeners
        eventManager.registerAllListeners()
        
        // Register all commands
        commandManager.registerAllCommands()
    }
    
    private fun setupLobbyWorld() {
        // Setting up lobby world
        
        val instanceManager = MinecraftServer.getInstanceManager()
        lobbyInstance = instanceManager.createInstanceContainer()
        
        // Enable automatic lighting updates
        lobbyInstance.setChunkSupplier { instance, x, z -> LightingChunk(instance, x, z) }
        
        // Set the chunk generator
        lobbyInstance.setGenerator { unit ->
            unit.modifier().fillHeight(0, 60, Block.STONE)
            unit.modifier().fillHeight(60, 61, Block.GRASS_BLOCK)
        }
        
        // Set as the default spawn instance
        MinecraftServer.getInstanceManager().instances.forEach { instance ->
            if (instance != lobbyInstance) {
                instanceManager.unregisterInstance(instance)
            }
        }
    }
    
    fun shutdown() {
        logger.info("Shutting down Lobby Plugin...")
        
        try {
            // Shutdown event manager and join item monitor
            if (::eventManager.isInitialized) {
                eventManager.shutdown()
            }
            
            // Shutdown vanish status monitor
            if (::vanishStatusMonitor.isInitialized) {
                vanishStatusMonitor.shutdown()
            }
            
            // Shutdown nametag manager
            if (::nametagManager.isInitialized) {
                nametagManager.shutdown()
            }
            
            // Shutdown new feature managers
            if (::freezeManager.isInitialized) {
                freezeManager.shutdown()
            }
            
            if (::panicManager.isInitialized) {
                panicManager.shutdown()
            }
            
            if (::creditsManager.isInitialized) {
                creditsManager.shutdown()
            }
            
            if (::adminCommandManager.isInitialized) {
                adminCommandManager.shutdown()
            }
            
            if (::staffModeManager.isInitialized) {
                staffModeManager.shutdown()
            }
            
            // Cancel all coroutines
            coroutineScope.cancel()
            
            logger.info("Lobby Plugin shutdown complete!")
        } catch (e: Exception) {
            logger.error("Error during plugin shutdown", e)
        }
    }
    
    fun reload() {
        logger.info("Reloading Lobby Plugin...")
        
        try {
            // Reload configurations
            configManager.loadAllConfigs()
            
            // Reload managers
            queueManager.reload()
            scoreboardManager.reload()
            
            // Reload schematics
            coroutineScope.launch {
                try {
                    schematicManager.reload()
                } catch (e: Exception) {
                    logger.error("Failed to reload schematic manager", e)
                }
            }
            
            logger.info("Lobby Plugin successfully reloaded!")
        } catch (e: Exception) {
            logger.error("Error during plugin reload", e)
            throw e
        }
    }
    
    private fun initializeRedis() {
        logger.info("Initializing Redis integration...")
        
        try {
            // Initialize Redis manager
            redisManager = RedisManager(this)
            
            // Attempt to connect to Redis
            val isConnected = redisManager!!.initialize().join()
            
            if (isConnected) {
                logger.info("Redis connection established successfully")
                
                // Initialize Redis cache
                redisCache = RedisCache(this, redisManager!!)
                
                // Initialize Redis event listener
                redisEventListener = RedisEventListener(this, redisCache!!)
                
                // Set up pub/sub subscriptions
                val pubSubConnection = redisManager!!.getPubSubConnection()
                if (pubSubConnection != null) {
                    pubSubConnection.addListener(redisEventListener!!)
                    
                    // Subscribe to punishment and mute update channels
                    val punishmentChannel = configManager.getString(configManager.mainConfig, "redis.channels.punishment_updates", "radium:punishment:updates")
                    val muteChannel = configManager.getString(configManager.mainConfig, "redis.channels.mute_updates", "radium:mute:updates")
                    
                    pubSubConnection.async().subscribe(punishmentChannel, muteChannel)
                    logger.info("Subscribed to Redis channels: $punishmentChannel, $muteChannel")
                }
                
                // Initialize punishment service with Redis support
                punishmentService = PunishmentService(this, radiumIntegration, redisManager!!, redisCache!!)
                
                // Redis integration initialized
            } else {
                logger.warn("Failed to connect to Redis - punishment system will use HTTP API only")
                
                // Initialize punishment service without Redis
                punishmentService = PunishmentService(this, radiumIntegration, 
                    RedisManager(this), RedisCache(this, RedisManager(this)))
            }
            
        } catch (e: Exception) {
            logger.error("Error initializing Redis integration", e)
            logger.warn("Punishment system will use HTTP API only")
            
            // Initialize punishment service without Redis in case of error
            try {
                punishmentService = PunishmentService(this, radiumIntegration,
                    RedisManager(this), RedisCache(this, RedisManager(this)))
            } catch (fallbackError: Exception) {
                logger.error("Failed to initialize punishment service fallback", fallbackError)
            }
        }
    }
    
    /**
     * Starts the permission cache cleanup task
     */
    private fun startPermissionCacheCleanup() {
        // Start permission cache cleanup every 10 minutes
        coroutineScope.launch {
            while (true) {
                try {
                    kotlinx.coroutines.delay(10 * 60 * 1000L) // 10 minutes
                    huncho.main.lobby.utils.PermissionCache.cleanupExpiredCache()
                    // Permission cache cleanup completed
                } catch (e: Exception) {
                    logger.error("Error during permission cache cleanup", e)
                }
            }
        }
    }

    /**
     * Get the vanish plugin message listener
     */
    fun getVanishListener(): VanishPluginMessageListener {
        return vanishPluginMessageListener
    }
}
