package radium.backend.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import radium.backend.Radium
import radium.backend.heads.MinecraftHeadsManager

/**
 * API routes for Minecraft-Heads.com integration
 */
fun Route.headsRoutes(plugin: Radium, logger: ComponentLogger) {
    route("/heads") {
        
        // Get head by name
        get("/name/{name}") {
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing name parameter"))
            
            try {
                val headData = plugin.minecraftHeadsManager.getHeadByName(name).get()
                if (headData != null) {
                    call.respond(HttpStatusCode.OK, headData)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Head not found"))
                }
            } catch (e: Exception) {
                logger.error("Error fetching head by name: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
        
        // Get heads by category
        get("/category/{category}") {
            val category = call.parameters["category"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing category parameter"))
            
            try {
                val heads = plugin.minecraftHeadsManager.getHeadsByCategory(category).get()
                call.respond(HttpStatusCode.OK, heads)
            } catch (e: Exception) {
                logger.error("Error fetching heads by category: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
        
        // Search heads
        get("/search/{query}") {
            val query = call.parameters["query"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing query parameter"))
            
            try {
                val heads = plugin.minecraftHeadsManager.searchHeads(query).get()
                call.respond(HttpStatusCode.OK, heads)
            } catch (e: Exception) {
                logger.error("Error searching heads: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
        
        // Get all categories
        get("/categories") {
            try {
                val categories = plugin.minecraftHeadsManager.getCategories()
                call.respond(HttpStatusCode.OK, categories)
            } catch (e: Exception) {
                logger.error("Error fetching categories: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
        
        // Clear cache (admin only)
        post("/cache/clear") {
            try {
                plugin.minecraftHeadsManager.clearCache()
                call.respond(HttpStatusCode.OK, mapOf("message" to "Cache cleared successfully"))
            } catch (e: Exception) {
                logger.error("Error clearing cache: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}
