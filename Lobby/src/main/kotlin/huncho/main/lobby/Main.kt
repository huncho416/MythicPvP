package huncho.main.lobby

import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.coordinate.Pos
import net.minestom.server.extras.velocity.VelocityProxy

fun main() {
    // Set up global exception handler for uncaught exceptions
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        when (exception) {
            is java.io.IOException -> {
                if (exception.message?.contains("Connection reset") == true) {
                    // Log connection resets at debug level to reduce spam when proxy goes offline
                    LobbyPlugin.logger.debug("Connection reset (proxy disconnect): ${exception.message}")
                } else {
                    LobbyPlugin.logger.warn("IO Exception in thread ${thread.name}: ${exception.message}")
                }
            }
            else -> {
                LobbyPlugin.logger.error("Uncaught exception in thread ${thread.name}", exception)
            }
        }
    }
    
    try {
        // Configure Velocity support FIRST (before anything else)
        // Load basic configuration first
        val configManager = huncho.main.lobby.config.ConfigManager(LobbyPlugin)
        configManager.loadAllConfigs()
        
        val velocityEnabled = configManager.getBoolean(configManager.mainConfig, "server.velocity.enabled", true)
        if (velocityEnabled) {
            val velocitySecret = configManager.getString(configManager.mainConfig, "server.velocity.secret", "")
            if (velocitySecret.isNotEmpty() && velocitySecret != "your-velocity-secret-here" && velocitySecret.length >= 8) {
                VelocityProxy.enable(velocitySecret)
                println("✅ Velocity proxy support enabled with secret: ${velocitySecret.take(8)}...")
            } else {
                println("⚠️ Velocity secret not configured properly: '$velocitySecret'")
            }
        }
        
        // Initialize Minestom server AFTER Velocity configuration
        val minecraftServer = MinecraftServer.init()
        
        // Now initialize our lobby plugin (after MinecraftServer.init())
        LobbyPlugin.initialize()
        
        // Handle player login - set them to spawn in the lobby instance
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player
            
            // Set the spawning instance to our lobby
            event.spawningInstance = LobbyPlugin.lobbyInstance
            
            // Set spawn position from config
            val spawnX = LobbyPlugin.configManager.getDouble(LobbyPlugin.configManager.mainConfig, "spawn.x", 0.5)
            val spawnY = LobbyPlugin.configManager.getDouble(LobbyPlugin.configManager.mainConfig, "spawn.y", 100.0)
            val spawnZ = LobbyPlugin.configManager.getDouble(LobbyPlugin.configManager.mainConfig, "spawn.z", 0.5)
            player.respawnPoint = Pos(spawnX, spawnY, spawnZ)
            
            // Log player join for debugging texture issues
            // Note: Player textures should be automatically handled by Velocity proxy
            LobbyPlugin.logger.debug("Player ${player.username} (${player.uuid}) joining lobby")
        }
        
        // Get server configuration
        val port = LobbyPlugin.configManager.getInt(LobbyPlugin.configManager.mainConfig, "server.port", 25566)
        val bindAddress = LobbyPlugin.configManager.getString(LobbyPlugin.configManager.mainConfig, "server.bind_address", "0.0.0.0")
        
        // Start the server
        minecraftServer.start(bindAddress, port)
        
        println("✅ Lobby server started successfully!")
        println("🌐 Server running on $bindAddress:$port")
        println("🔌 Velocity proxy support: ${if (velocityEnabled) "enabled" else "disabled"}")
        println("🎮 Players can now connect through the Radium proxy")
        
    } catch (e: Exception) {
        LobbyPlugin.logger.error("Failed to start lobby server", e)
        System.exit(1)
    }
}
