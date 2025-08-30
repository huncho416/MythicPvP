# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Structure

This is a multi-project Minecraft network built with Kotlin, consisting of two main components:

- **Radium**: Velocity proxy plugin for user management, permissions, punishment system, and cross-server communication
- **Lobby**: Minestom-based lobby server with queue management, protection, and world features

Both projects use Gradle with Kotlin DSL for build management and require Java 21+.

## Development Commands

### Building Projects (Monorepo)

```bash
# Build entire monorepo
./gradlew build

# Build specific projects
./gradlew :radium:build
./gradlew :lobby:build

# Create shadow JARs (production builds)
./gradlew :radium:shadowJar
./gradlew :lobby:shadowJar

# Build everything with shadow JARs
./gradlew build
```

### Running Development Servers

```bash
# Run Radium with Velocity (from root)
./gradlew :radium:runVelocity

# Run Lobby server (from root)
./gradlew :lobby:run
```

### Testing

```bash
# Run all tests
./gradlew test

# Run tests for specific projects
./gradlew :radium:test
./gradlew :lobby:test
```

## Architecture Overview

### Radium (Velocity Plugin)

- **Entry Point**: `radium.backend.Radium` class with `@Plugin` annotation
- **API Server**: Embedded Ktor HTTP server for cross-server communication
- **Permission System**: Custom rank-based permissions integrated with Velocity
- **Punishment System**: MongoDB-backed with Redis caching for bans, mutes, kicks
- **Command Framework**: Uses Lamp command library with Brigadier integration
- **Vanish System**: Network-wide staff vanish with cross-server synchronization

### Lobby (Minestom Server)

- **Entry Point**: `huncho.main.lobby.main()` function in Main.kt
- **Plugin System**: `LobbyPlugin` singleton for centralized management
- **Queue System**: Multi-server queue management with priority support
- **Protection System**: Comprehensive anti-grief with configurable rules
- **Schematic System**: Sponge schematic loading with caching and async operations
- **Integration**: Radium API client for user data and permissions

### Key Integrations

- **MongoDB**: User data, punishments, and configurations
- **Redis**: Real-time caching and cross-server messaging
- **Velocity**: Proxy forwarding and secret authentication
- **Radium API**: HTTP-based communication between Lobby and Radium

## Configuration Files

- **Lobby Config**: `config/lobby/config.yml` (spawn, queues, protection, schematics)
- **Radium Config**: Auto-generated configurations for ranks, permissions, database
- **Message Files**: Localized in `config/lobby/messages.yml`
- **Schematic Files**: Stored in `config/lobby/schematics/` directory

## Key Patterns and Conventions

### Error Handling
- Use comprehensive try-catch blocks for external API calls
- Log connection resets at debug level to avoid spam
- Global exception handlers for uncaught exceptions

### Async Operations
- Lobby uses Kotlin coroutines for non-blocking I/O
- Radium uses reactive streams for MongoDB operations
- Schematic loading and pasting are fully asynchronous

### Command Implementation
- Radium: Lamp command framework with annotation-based registration
- Lobby: Custom command system with permission checking via Radium API

### Database Patterns
- MongoDB with reactive streams for scalability
- Redis for high-performance caching and pub/sub
- Connection pooling and retry mechanisms

### API Design
- RESTful HTTP APIs for cross-server communication
- JSON serialization using Gson/Jackson
- Authentication via API keys and server secrets

## Development Notes

### Dependencies
- Both projects use compatible versions of shared libraries
- MongoDB driver 4.10.2+ for reactive streams
- Minestom 2025.08.12-1.21.8 for Minecraft 1.21.8 support
- Velocity API 3.4.0-SNAPSHOT for proxy features

### Build Configuration
- Shadow plugin creates fat JARs with all dependencies
- Kotlin toolchain configured for Java 21
- KAPT for Velocity annotation processing in Radium

### Testing Approach
- Unit tests focus on business logic isolation
- Integration tests require running databases
- Live testing documented in project docs/

### Security Considerations
- Velocity secret must be properly configured
- Database credentials stored in configuration files
- API authentication for cross-server communication