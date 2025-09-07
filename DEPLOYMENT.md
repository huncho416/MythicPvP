# MythicPvP Network - Deployment Guide

## Quick Deployment Checklist

### Prerequisites
- [ ] Java 21+ installed
- [ ] MongoDB server running
- [ ] Velocity proxy configured
- [ ] Network ports configured (25565 for proxy, 25566 for lobby)

### Pre-deployment Steps

1. **Build the project**
   ```bash
   ./gradlew clean build shadowJar
   ```

2. **Configure Database**
   - Copy `Radium/src/main/resources/database.yml.example` to `plugins/Radium/database.yml`
   - Update MongoDB connection details
   - Ensure MongoDB is accessible from Radium

3. **Configure Lobby**
   - Copy `Lobby/config/lobby/config.yml.example` to `Lobby/config/lobby/config.yml`
   - Update Radium API URL
   - Set Velocity forwarding secret

### Deployment Process

#### Step 1: Deploy Radium (Velocity Plugin)
```bash
# Copy built JAR to Velocity plugins directory
cp Radium/build/libs/Radium-1.0-SNAPSHOT.jar /path/to/velocity/plugins/

# Ensure configuration exists
mkdir -p /path/to/velocity/plugins/Radium/
cp database.yml.example /path/to/velocity/plugins/Radium/database.yml

# Edit configuration with production values
nano /path/to/velocity/plugins/Radium/database.yml
```

#### Step 2: Deploy Lobby Server
```bash
# Create deployment directory
mkdir -p /opt/mythicpvp/lobby/

# Copy lobby JAR and configurations
cp Lobby/build/libs/lobby.jar /opt/mythicpvp/lobby/
cp -r Lobby/config/ /opt/mythicpvp/lobby/

# Set proper permissions
chmod +x /opt/mythicpvp/lobby/lobby.jar
```

#### Step 3: Start Services
```bash
# Start Velocity (will load Radium plugin)
cd /path/to/velocity/
./velocity

# Start Lobby Server
cd /opt/mythicpvp/lobby/
java -Xmx2G -Xms1G -jar lobby.jar
```

### Production Configuration

#### MongoDB Optimization
```javascript
// Create indexes for better performance
db.players.createIndex({ "uuid": 1 })
db.punishments.createIndex({ "targetId": 1, "type": 1, "active": 1 })
db.reports.createIndex({ "status": 1, "timestamp": -1 })
db.ranks.createIndex({ "name": 1 })
```

#### Firewall Configuration
```bash
# Allow Velocity proxy (public)
ufw allow 25565/tcp

# Allow Lobby server (internal)
ufw allow from 10.0.0.0/8 to any port 25566

# Allow MongoDB (internal only)
ufw allow from 10.0.0.0/8 to any port 27017

# Allow Radium API (internal)
ufw allow from 10.0.0.0/8 to any port 8080
```

### Monitoring & Maintenance

#### Log Locations
- Velocity logs: `/path/to/velocity/logs/`
- Lobby logs: `/opt/mythicpvp/lobby/logs/`
- MongoDB logs: `/var/log/mongodb/`

#### Health Checks
```bash
# Check if services are running
ps aux | grep velocity
ps aux | grep lobby.jar
systemctl status mongod

# Check network connectivity
netstat -tulpn | grep :25565
netstat -tulpn | grep :25566
netstat -tulpn | grep :27017
```

#### Backup Strategy
```bash
# MongoDB backup (daily recommended)
mongodump --host localhost --port 27017 --db radium --out /backup/$(date +%Y%m%d)/

# Configuration backup
tar -czf /backup/configs-$(date +%Y%m%d).tar.gz \
  /path/to/velocity/plugins/Radium/ \
  /opt/mythicpvp/lobby/config/
```

### Troubleshooting

#### Common Issues

1. **"Connection refused" errors**
   - Check if MongoDB is running: `systemctl status mongod`
   - Verify network connectivity: `telnet mongodb-host 27017`
   - Check firewall rules

2. **"Permission denied" errors**
   - Ensure proper file permissions: `chmod +x lobby.jar`
   - Check user permissions for directories

3. **"Plugin failed to load"**
   - Verify Java version compatibility
   - Check Velocity version compatibility
   - Review plugin logs for specific errors

4. **Database connection issues**
   - Verify MongoDB credentials
   - Check database.yml configuration
   - Ensure MongoDB allows connections from the application host

#### Performance Optimization

1. **JVM Tuning for Lobby**
   ```bash
   java -Xmx4G -Xms2G \
        -XX:+UseG1GC \
        -XX:MaxGCPauseMillis=50 \
        -XX:+UnlockExperimentalVMOptions \
        -XX:+UseStringDeduplication \
        -jar lobby.jar
   ```

2. **MongoDB Optimization**
   - Set appropriate connection pool sizes
   - Enable MongoDB compression
   - Configure appropriate read/write concerns

3. **Network Optimization**
   - Use connection pooling
   - Configure appropriate timeouts
   - Enable compression for API calls

### Security Considerations

1. **Change Default Secrets**
   - Update Velocity forwarding secret
   - Use strong MongoDB passwords
   - Rotate secrets regularly

2. **Network Security**
   - Use VPN or private networks for inter-service communication
   - Implement proper firewall rules
   - Consider TLS for database connections

3. **Access Control**
   - Limit database access to application users only
   - Use principle of least privilege
   - Regular security audits

### Scaling Considerations

1. **Horizontal Scaling**
   - Multiple lobby servers can connect to the same Radium backend
   - Load balance lobby servers behind the proxy
   - Consider MongoDB replica sets for high availability

2. **Vertical Scaling**
   - Monitor memory usage and adjust JVM heap sizes
   - Scale MongoDB resources based on usage patterns
   - Consider SSD storage for better I/O performance

---

**For additional support, refer to the main README.md or contact the development team.**
