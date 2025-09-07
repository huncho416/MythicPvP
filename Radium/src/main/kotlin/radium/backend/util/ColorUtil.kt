package radium.backend.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.regex.Pattern

/**
 * Enhanced color utility that supports both legacy color codes and hex colors
 * Handles patterns like &#RRGGBB, #RRGGBB, and legacy &a, §a codes
 */
object ColorUtil {
    
    // Pattern to match hex colors: &#RRGGBB or #RRGGBB
    private val HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})")
    private val HEX_PATTERN_SIMPLE = Pattern.compile("#([A-Fa-f0-9]{6})")
    
    // Pattern to match legacy color codes
    private val LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])")
    
    /**
     * Cleans corrupted color codes and normalizes them to ampersand format
     * Supports both legacy and hex color codes
     */
    fun cleanColorCodes(text: String?): String {
        if (text.isNullOrBlank()) return ""
        
        return text
            .replace("∩┐╜", "&")     // Fix UTF-8 corruption of &
            .replace("§", "&")      // Normalize section signs to ampersand
            .replace("∩", "&")      // Additional corruption patterns
            .replace("┐", "")       // Remove stray corruption characters
            .replace("╜", "")       // Remove stray corruption characters
            .replace("Â", "")       // Remove UTF-8 BOM corruption
            .replace("âž§", "&")    // Another corruption pattern
            .replace(Regex("&([^0-9a-fk-orA-FK-OR#])"), "")  // Remove invalid codes (allow # for hex)
            .replace(Regex("&{2,}"), "&")  // Replace multiple & with single &
    }
    
    /**
     * Converts text with legacy and hex color codes to Adventure Component
     * Supports patterns:
     * - Legacy: &a, &c, &f, etc.
     * - Hex: &#FF5555, &#00AAAA, etc.
     * - Simple hex: #FF5555, #00AAAA, etc.
     */
    fun parseColoredText(text: String?): Component {
        if (text.isNullOrBlank()) return Component.empty()
        
        return try {
            // Clean the text first
            val cleanText = cleanColorCodes(text)
            
            // Process hex colors first (both &#RRGGBB and #RRGGBB patterns)
            val processedText = processHexColors(cleanText)
            
            // Then process with legacy serializer for remaining codes
            LegacyComponentSerializer.legacyAmpersand().deserialize(processedText)
        } catch (e: Exception) {
            // Ultimate fallback: strip all formatting and return white text
            val plainText = text.replace(Regex("[&§∩┐╜âž#][0-9a-fA-F]*[0-9a-fk-orA-FK-OR]?"), "")
                .replace(Regex("[∩┐╜âž]"), "")
            Component.text(plainText, NamedTextColor.WHITE)
        }
    }
    
    /**
     * Process hex color codes and convert them to legacy format for Adventure compatibility
     * Converts &#RRGGBB and #RRGGBB to appropriate color codes
     */
    private fun processHexColors(text: String): String {
        var result = text
        
        // Process &#RRGGBB pattern
        val hexMatcher = HEX_PATTERN.matcher(result)
        val hexBuffer = StringBuffer()
        
        while (hexMatcher.find()) {
            val hexColor = hexMatcher.group(1)
            val replacement = convertHexToLegacy(hexColor)
            hexMatcher.appendReplacement(hexBuffer, replacement)
        }
        hexMatcher.appendTail(hexBuffer)
        result = hexBuffer.toString()
        
        // Process #RRGGBB pattern (simple hex)
        val simpleMatcher = HEX_PATTERN_SIMPLE.matcher(result)
        val simpleBuffer = StringBuffer()
        
        while (simpleMatcher.find()) {
            val hexColor = simpleMatcher.group(1)
            val replacement = convertHexToLegacy(hexColor)
            simpleMatcher.appendReplacement(simpleBuffer, replacement)
        }
        simpleMatcher.appendTail(simpleBuffer)
        
        return simpleBuffer.toString()
    }
    
    /**
     * Convert hex color to a format that Adventure can understand
     * For modern Adventure versions, we can use proper hex colors
     */
    private fun convertHexToLegacy(hexColor: String): String {
        return try {
            // For Adventure 4.x+, we can use the proper hex format
            "\\<#$hexColor>"
        } catch (e: Exception) {
            // Fallback to closest legacy color if hex parsing fails
            getClosestLegacyColor(hexColor)
        }
    }
    
    /**
     * Get the closest legacy color to a hex color
     * Used as fallback when hex colors aren't supported
     */
    private fun getClosestLegacyColor(hexColor: String): String {
        return try {
            val color = java.awt.Color.decode("#$hexColor")
            val red = color.red
            val green = color.green
            val blue = color.blue
            
            // Simple color matching to legacy colors
            when {
                red > 200 && green < 100 && blue < 100 -> "&c" // Red
                red < 100 && green > 200 && blue < 100 -> "&a" // Green  
                red < 100 && green < 100 && blue > 200 -> "&9" // Blue
                red > 200 && green > 200 && blue < 100 -> "&e" // Yellow
                red > 200 && green < 100 && blue > 200 -> "&d" // Magenta
                red < 100 && green > 200 && blue > 200 -> "&b" // Cyan
                red > 150 && green > 150 && blue > 150 -> "&f" // White
                red < 100 && green < 100 && blue < 100 -> "&8" // Dark Gray
                red > 100 && green > 100 && blue > 100 -> "&7" // Gray
                else -> "&f" // Default to white
            }
        } catch (e: Exception) {
            "&f" // Default to white on error
        }
    }
    
    /**
     * Parse a color string to Adventure TextColor
     * Supports both legacy (&a) and hex (&#FF5555, #FF5555) formats
     */
    fun parseTextColor(colorString: String?): TextColor {
        if (colorString.isNullOrBlank()) return NamedTextColor.WHITE
        
        val clean = cleanColorCodes(colorString).trim()
        
        return try {
            when {
                // Handle hex patterns &#RRGGBB
                clean.startsWith("&#") && clean.length == 8 -> {
                    val hex = clean.substring(2)
                    TextColor.fromHexString("#$hex") ?: NamedTextColor.WHITE
                }
                // Handle simple hex patterns #RRGGBB
                clean.startsWith("#") && clean.length == 7 -> {
                    TextColor.fromHexString(clean) ?: NamedTextColor.WHITE
                }
                // Handle legacy color codes &a, &c, etc.
                clean.startsWith("&") && clean.length == 2 -> {
                    val code = clean[1].lowercaseChar()
                    when (code) {
                        '0' -> NamedTextColor.BLACK
                        '1' -> NamedTextColor.DARK_BLUE
                        '2' -> NamedTextColor.DARK_GREEN
                        '3' -> NamedTextColor.DARK_AQUA
                        '4' -> NamedTextColor.DARK_RED
                        '5' -> NamedTextColor.DARK_PURPLE
                        '6' -> NamedTextColor.GOLD
                        '7' -> NamedTextColor.GRAY
                        '8' -> NamedTextColor.DARK_GRAY
                        '9' -> NamedTextColor.BLUE
                        'a' -> NamedTextColor.GREEN
                        'b' -> NamedTextColor.AQUA
                        'c' -> NamedTextColor.RED
                        'd' -> NamedTextColor.LIGHT_PURPLE
                        'e' -> NamedTextColor.YELLOW
                        'f' -> NamedTextColor.WHITE
                        else -> NamedTextColor.WHITE
                    }
                }
                else -> NamedTextColor.WHITE
            }
        } catch (e: Exception) {
            NamedTextColor.WHITE
        }
    }
    
    /**
     * Convert any color code to a clean hex format for storage
     * Converts legacy codes to their hex equivalents
     */
    fun toHexString(colorString: String?): String {
        if (colorString.isNullOrBlank()) return "#FFFFFF"
        
        val clean = cleanColorCodes(colorString).trim()
        
        return when {
            // Already hex format &#RRGGBB
            clean.startsWith("&#") && clean.length == 8 -> "#${clean.substring(2)}"
            // Already simple hex #RRGGBB
            clean.startsWith("#") && clean.length == 7 -> clean
            // Legacy color codes
            clean.startsWith("&") && clean.length == 2 -> {
                val code = clean[1].lowercaseChar()
                when (code) {
                    '0' -> "#000000" // Black
                    '1' -> "#0000AA" // Dark Blue
                    '2' -> "#00AA00" // Dark Green
                    '3' -> "#00AAAA" // Dark Aqua
                    '4' -> "#AA0000" // Dark Red
                    '5' -> "#AA00AA" // Dark Purple
                    '6' -> "#FFAA00" // Gold
                    '7' -> "#AAAAAA" // Gray
                    '8' -> "#555555" // Dark Gray
                    '9' -> "#5555FF" // Blue
                    'a' -> "#55FF55" // Green
                    'b' -> "#55FFFF" // Aqua
                    'c' -> "#FF5555" // Red
                    'd' -> "#FF55FF" // Light Purple
                    'e' -> "#FFFF55" // Yellow
                    'f' -> "#FFFFFF" // White
                    else -> "#FFFFFF"
                }
            }
            else -> "#FFFFFF"
        }
    }
    
    /**
     * Strip all color codes from text, returning plain text
     */
    fun stripColor(text: String?): String {
        if (text.isNullOrBlank()) return ""
        
        return text
            .replace(HEX_PATTERN.toRegex(), "") // Remove &#RRGGBB
            .replace(HEX_PATTERN_SIMPLE.toRegex(), "") // Remove #RRGGBB
            .replace(LEGACY_PATTERN.toRegex(), "") // Remove &a, &c, etc.
            .replace("∩┐╜", "") // Remove corruption
            .replace("§", "") // Remove section signs
            .trim()
    }
    
    /**
     * Validate if a string is a valid color code
     */
    fun isValidColor(colorString: String?): Boolean {
        if (colorString.isNullOrBlank()) return false
        
        val clean = cleanColorCodes(colorString).trim()
        
        return when {
            // Hex patterns
            clean.matches(Regex("&#[A-Fa-f0-9]{6}")) -> true
            clean.matches(Regex("#[A-Fa-f0-9]{6}")) -> true
            // Legacy patterns
            clean.matches(Regex("&[0-9a-fk-orA-FK-OR]")) -> true
            else -> false
        }
    }
}
