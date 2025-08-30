# MythicPvP - Minecraft Network

A comprehensive Minecraft network built with Kotlin, featuring a Velocity proxy plugin and Minestom lobby server.

## 🏗️ Architecture

This monorepo contains two main components:

### Radium (Velocity Plugin)
- **Location**: `/Radium/`
- **Purpose**: Velocity proxy plugin for user management, permissions, punishment system, and cross-server communication
- **Tech Stack**: Kotlin, Velocity API, MongoDB, Redis

### Lobby (Minestom Server)  
- **Location**: `/Lobby/`
- **Purpose**: Minestom-based lobby server with queue management, protection, and world features
- **Tech Stack**: Kotlin, Minestom, HTTP API integration

## 🚀 Quick Start

### Prerequisites
- Java 21+
- MongoDB (for user data and punishments)
- Redis (for real-time caching and messaging)

### Building
```bash
# Build entire monorepo
./gradlew build

# Build specific projects
./gradlew :radium:build
./gradlew :lobby:build

# Create production JARs
./gradlew :radium:shadowJar
./gradlew :lobby:shadowJar
```

### Running Development Servers
```bash
# Run Radium with Velocity
./gradlew :radium:runVelocity

# Run Lobby server
./gradlew :lobby:run
```

## 📁 Project Structure

```
MythicPvP/
├── Radium/                 # Velocity proxy plugin
│   ├── src/main/kotlin/    # Radium source code
│   ├── src/main/resources/ # Plugin resources & configs
│   ├── run/               # Development server files
│   └── docs/              # Documentation
├── Lobby/                  # Minestom lobby server
│   ├── src/main/kotlin/    # Lobby source code
│   ├── src/main/resources/ # Server resources
│   ├── config/            # Server configurations
│   └── docs/              # Documentation
├── build.gradle.kts        # Root build configuration
├── settings.gradle.kts     # Gradle project settings
└── README.md              # This file
```

## 🔧 Key Features

### Radium Plugin
- **User Management**: MongoDB-backed user profiles and ranks
- **Punishment System**: Advanced banning, muting, and kicking with Redis caching
- **Permission System**: Rank-based permissions with inheritance
- **Vanish System**: Staff vanish with rank-based visibility rules
- **Cross-Server Communication**: HTTP API and plugin messaging
- **Command Framework**: Brigadier-based commands with auto-completion

### Lobby Server
- **Queue Management**: Multi-server queue system with priorities
- **World Protection**: Comprehensive anti-grief protection
- **Schematic System**: Sponge schematic support with async loading
- **Tab List Management**: Custom tab formatting with vanish integration
- **API Integration**: Full integration with Radium for user data

## 🔒 Configuration

### Database Setup
1. **MongoDB**: Configure connection in Radium's config
2. **Redis**: Set up for caching and cross-server messaging

### Player Info Forwarding
- Configure Velocity with modern forwarding
- Set matching secrets in both Velocity and Minestom configs

### API Communication
- Radium runs HTTP API on port 8080
- Lobby connects to Radium API for user data

## 📚 Documentation

Detailed documentation is available in each project's `docs/` directory:
- [Radium Documentation](./Radium/docs/)
- [Lobby Documentation](./Lobby/docs/)

## 🛠️ Development

### Testing
```bash
# Run all tests
./gradlew test

# Test specific projects
./gradlew :radium:test
./gradlew :lobby:test
```

### Code Style
- **Language**: Kotlin with Java 21 target
- **Build System**: Gradle with Kotlin DSL
- **Async**: Kotlin coroutines for non-blocking operations

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests to ensure everything works
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
