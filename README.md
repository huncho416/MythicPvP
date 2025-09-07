# MythicPvP Network Infrastructure

A modern Minecraft server network infrastructure built with Kotlin, featuring a Velocity proxy backend (Radium) and Minestom lobby server integration.

## 🚀 Project Overview

MythicPvP Network consists of two main components:
- **Radium**: Velocity proxy plugin providing backend services (punishment system, reports, user management)
- **Lobby**: Minestom-based lobby server with advanced features and Radium integration

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│    Players      │────│   Velocity      │────│   Game Servers  │
│                 │    │   + Radium      │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              │
                       ┌─────────────────┐
                       │  Minestom Lobby │
                       │  + Integration  │
                       └─────────────────┘
                              │
                       ┌─────────────────┐
                       │    MongoDB      │
                       │   Database      │
                       └─────────────────┘
```

## 📋 Features

### Radium (Velocity Backend)
- **Advanced Punishment System**: Ban, mute, kick, warn, blacklist with temporary/permanent options
- **Report & Request System**: Player reporting with staff management tools
- **Permission Management**: Comprehensive rank and permission system with inheritance
- **Player Data Management**: UUID resolution, profile management, cross-server data sync
- **Staff Tools**: Vanish system, admin chat, staff notifications
- **API Integration**: RESTful API for cross-server communication
- **Database Integration**: MongoDB for persistent data storage

### Lobby Server (Minestom)
- **Queue System**: Advanced queue management for game servers
- **Staff Management**: Freeze, panic mode, staff mode with inspection tools
- **Schematic System**: World building with schematic loading and management
- **Tab List & Scoreboard**: Dynamic player information display
- **Visibility System**: Advanced player visibility management
- **Nametag System**: Custom nametag formatting with rank integration
- **Protection System**: Comprehensive lobby protection and anti-grief
- **Integration Layer**: Seamless communication with Radium backend

### Staff Tools & Commands
- **Report Management**: `/resolve`, `/dismiss` commands for handling player reports
- **Request Management**: `/completerequest`, `/cancelrequest` for help requests
- **Player Management**: `/invsee`, `/freeze`, `/vanish`, `/staffmode`
- **Administrative**: `/panic`, `/maintenance`, `/broadcast`

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0.20
- **Proxy**: Velocity 3.4.0
- **Game Server**: Minestom 2025.08.29-1.21.8
- **Database**: MongoDB 4.11.1
- **Cache**: Redis 6.3.2
- **HTTP Client**: OkHttp 4.12.0
- **Serialization**: Jackson 2.16.1
- **Async**: Kotlin Coroutines 1.7.3
- **Logging**: Logback 1.4.14
- **Adventure API**: 4.15.0 for modern text components

## 🚀 Quick Start

### Prerequisites
- Java 21+
- MongoDB Server
- Redis Server (optional, for advanced features)

### Building the Project
```bash
# Clone the repository
git clone <repository-url>
cd MythicPvP

# Build both components
./gradlew build

# Build shadow JARs for deployment
./gradlew shadowJar
```

### Deployment

#### 1. Radium (Velocity Plugin)
```bash
# Copy the built JAR to your Velocity plugins directory
cp Radium/build/libs/Radium-1.0-SNAPSHOT.jar /path/to/velocity/plugins/

# Configure database connection in plugins/Radium/database.yml
# Start Velocity proxy
```

#### 2. Lobby Server
```bash
# Run the lobby server
java -jar Lobby/build/libs/lobby.jar

# Or use the provided scripts
cd Lobby/build/scripts
./lobby
```

### Configuration

#### Database Configuration (Radium)
Create `plugins/Radium/database.yml`:
```yaml
database:
  type: "mongodb"
  host: "localhost"
  port: 27017
  database: "radium"
  username: ""
  password: ""
```

#### Lobby Configuration
Configure `config/lobby/config.yml`:
```yaml
server:
  port: 25566
  bind-address: "0.0.0.0"

radium:
  api:
    base_url: "http://localhost:8080"
    
velocity:
  enabled: true
  secret: "your-velocity-secret"
```

## 📚 System Components

### Permission System
The network uses a hierarchical permission system with:
- **Ranks**: Configurable rank system with inheritance
- **Permissions**: Node-based permission system
- **Temporary Grants**: Time-based permission assignments
- **Cross-Server Sync**: Real-time permission updates across servers

### Punishment System
Comprehensive moderation tools:
- **Warnings**: Escalating warning system with configurable thresholds
- **Temporary/Permanent Bans**: IP and player bans with appeal system
- **Mute System**: Chat restrictions with bypass permissions
- **Blacklist**: Permanent network exclusions
- **Audit Trail**: Complete punishment history and tracking

### Report System
Player-to-staff communication:
- **Player Reports**: Easy reporting system with reason categorization
- **Staff Tools**: Efficient report management with resolution tracking
- **Help Requests**: Player assistance system with categorized request types
- **Broadcast System**: Real-time staff notifications without ID clutter

## 🔧 Development

### Project Structure
```
MythicPvP/
├── Radium/                 # Velocity proxy plugin
│   ├── src/main/kotlin/    # Kotlin source code
│   ├── src/main/resources/ # Configuration templates
│   └── build.gradle.kts    # Build configuration
├── Lobby/                  # Minestom lobby server
│   ├── src/main/kotlin/    # Kotlin source code
│   ├── config/             # Runtime configuration
│   └── build.gradle.kts    # Build configuration
└── build.gradle.kts        # Root project configuration
```

### Key APIs

#### Radium API Endpoints
- `POST /reports/create` - Create player report
- `POST /requests/create` - Create help request
- `GET /players/{uuid}` - Get player data
- `POST /punishments/ban` - Issue ban
- `GET /ranks` - List all ranks

#### Integration Layer
The lobby server communicates with Radium through:
- **HTTP API**: RESTful API for data operations
- **Plugin Messages**: Real-time event synchronization
- **WebSocket**: Live staff notifications (future enhancement)

### Adding New Features

1. **Backend (Radium)**: Add new API endpoints and database models
2. **Frontend (Lobby)**: Implement UI/UX and integration calls
3. **Commands**: Register commands in respective CommandManagers
4. **Permissions**: Add permission nodes to the rank system

## 📝 Commands Reference

### Player Commands
- `/report <player> <reason> <description>` - Report a player
- `/request <type> <subject> <description>` - Request help from staff

### Staff Commands
- `/resolve <player> [resolution]` - Resolve player report
- `/dismiss <player> [reason]` - Dismiss player report
- `/completerequest <player> [response]` - Complete help request
- `/cancelrequest <player> [reason]` - Cancel help request
- `/freeze <player>` - Freeze player in place
- `/vanish [player]` - Toggle vanish mode
- `/staffmode` - Enter staff inspection mode
- `/panic` - Enable emergency panic mode

### Administrative Commands
- `/ban <player> [duration] <reason>` - Ban player
- `/mute <player> [duration] <reason>` - Mute player
- `/warn <player> <reason>` - Warn player
- `/grant <player> <rank> [duration]` - Grant rank to player
- `/maintenance` - Toggle maintenance mode

## 🔒 Security Features

- **Permission Validation**: All commands validate permissions before execution
- **Rate Limiting**: Cooldowns on report/request creation to prevent spam
- **Audit Logging**: Complete action logging for administrative oversight
- **Secure Communication**: All API calls use secure channels
- **Input Validation**: Comprehensive input sanitization and validation

## 🎯 Production Considerations

### Performance
- **Async Operations**: All database operations are non-blocking
- **Connection Pooling**: Optimized database connection management
- **Caching**: Redis integration for frequently accessed data
- **Resource Management**: Proper cleanup and resource disposal

### Monitoring
- **Structured Logging**: Comprehensive logging with proper levels
- **Error Handling**: Graceful error handling with user-friendly messages
- **Health Checks**: Built-in system health monitoring
- **Metrics**: Performance metrics collection (ready for Prometheus)

### Scalability
- **Microservice Architecture**: Modular design for easy scaling
- **Database Optimization**: Indexed queries and efficient data models
- **Stateless Design**: Components can be horizontally scaled
- **Event-Driven**: Loose coupling through event-based communication

## 🤝 Contributing

This is a private project for MythicPvP Network. Development follows these principles:
- **Clean Architecture**: Separation of concerns and modular design
- **Type Safety**: Leverage Kotlin's type system for robust code
- **Testing**: Comprehensive unit and integration testing
- **Documentation**: Inline documentation and external API docs

## 📄 License

Private project for MythicPvP Network. All rights reserved.

## 🆘 Support

For technical support or questions about the MythicPvP Network infrastructure:
- Internal documentation available in `/docs` directory
- System architecture diagrams in project documentation
- API documentation generated from inline docs

---

**Built with ❤️ for MythicPvP Network**
