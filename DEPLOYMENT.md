# TraningApp - Deployment Guide

## Overview

This guide covers deploying TraningApp to an on-premises or custom server environment. It includes setup, configuration, and troubleshooting steps.

---

## Prerequisites

### System Requirements

- **Java Runtime**: Java 11 or higher
- **Database**: MySQL 5.7+ or MySQL 8.0+
- **Server**: Linux/macOS with systemd or manual service management
- **Ports**:
  - Application: 8080 (configurable)
  - Database: 3306 (MySQL)
  - SMTP: 587 (Gmail SMTP)

### Build Requirements

- Maven 3.6+
- Git

---

## Build and Package

### 1. Clone and Build

```bash
git clone <repository-url>
cd TraningApp

# Build with GCP profile (production)
mvn clean package -PGCP -DskipTests

# Or build with local profile (development)
mvn clean package -Plocal -DskipTests
```

This generates `target/trainingapp-*.jar`

### 2. Create Deployment Directory

```bash
sudo mkdir -p /opt/trainingapp
sudo chown $USER:$USER /opt/trainingapp
```

### 3. Deploy JAR

```bash
cp target/trainingapp-*.jar /opt/trainingapp/trainingapp.jar
chmod +x /opt/trainingapp/trainingapp.jar
```

---

## Environment Configuration

### 1. Create Environment Variables File

Create `/opt/trainingapp/.env` or `/etc/trainingapp/env`:

```bash
# Master password for Jasypt encryption
JASYPT_ENCRYPTOR_PASSWORD=your-secure-master-password

# Database Configuration (if using environment variables)
DB_URL=jdbc:mysql://localhost:3306/training_db?serverTimezone=Asia/Tokyo
DB_USERNAME=springuser
DB_PASSWORD=your-secure-db-password

# Gmail Configuration
SPRING_MAIL_USERNAME=your-gmail-address@gmail.com
SPRING_MAIL_PASSWORD_ENC=ENC(encrypted-gmail-password)

# Google OAuth2
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret

# Application Settings
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=GCP
```

**Important**: Protect this file!

```bash
chmod 600 /opt/trainingapp/.env
sudo chown root:root /opt/trainingapp/.env
```

### 2. Encrypt Sensitive Values

Before deployment, encrypt passwords using EncryptTool:

```bash
export JASYPT_ENCRYPTOR_PASSWORD=your-secure-master-password

# On the build machine
mvn clean compile exec:java \
  -Dexec.mainClass="com.example.training.util.EncryptTool" \
  -Dexec.args="your-gmail-app-password"
```

Copy the encrypted output (e.g., `ENC(a8aU+XwSxX0K8e3dR5Z7uQ==)`) to the `.env` file as `SPRING_MAIL_PASSWORD_ENC`.

### 3. MySQL Database Setup

```bash
mysql -u root -p << 'EOF'
CREATE DATABASE training_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'springuser'@'localhost' IDENTIFIED BY 'your-secure-db-password';
GRANT ALL PRIVILEGES ON training_db.* TO 'springuser'@'localhost';
FLUSH PRIVILEGES;
EXIT;
EOF
```

---

## Running the Application

### Option 1: Manual Startup (Testing)

```bash
cd /opt/trainingapp

# Source environment
source .env

# Run the application
java -jar trainingapp.jar \
  --spring.profiles.active=GCP \
  --server.port=8080
```

Access the application: `http://localhost:8080`

### Option 2: systemd Service (Recommended for Production)

#### A. Create systemd Service File

Create `/etc/systemd/system/trainingapp.service`:

```ini
[Unit]
Description=TraningApp Service
After=network.target mysql.service

[Service]
Type=simple
User=trainingapp
WorkingDirectory=/opt/trainingapp

# Load environment from file
EnvironmentFile=/opt/trainingapp/.env

# Startup
ExecStart=/usr/bin/java \
  -Xmx512m \
  -Xms256m \
  -jar /opt/trainingapp/trainingapp.jar \
  --spring.profiles.active=GCP \
  --server.port=8080

# Restart policy
Restart=on-failure
RestartSec=10

# Security
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

#### B. Create Service User

```bash
sudo useradd -r -s /bin/false trainingapp
sudo chown trainingapp:trainingapp /opt/trainingapp/trainingapp.jar
```

#### C. Enable and Start Service

```bash
sudo systemctl daemon-reload
sudo systemctl enable trainingapp
sudo systemctl start trainingapp

# Check status
sudo systemctl status trainingapp

# View logs
sudo journalctl -u trainingapp -f
```

### Option 3: Docker (Optional)

#### A. Create Dockerfile

```dockerfile
FROM openjdk:11-jre-slim

WORKDIR /app
COPY trainingapp.jar .

ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar trainingapp.jar"]
```

#### B. Build and Run

```bash
docker build -t trainingapp:latest .

docker run -d \
  --name trainingapp \
  -p 8080:8080 \
  --env-file /opt/trainingapp/.env \
  trainingapp:latest
```

---

## Post-Deployment Verification

### 1. Application Health Check

```bash
curl http://localhost:8080/
curl -s http://localhost:8080/health | jq
```

Expected response (or similar):

```json
{
  "status": "UP"
}
```

### 2. Database Connectivity Check

Verify the application can connect to MySQL:

```bash
# Check logs
sudo journalctl -u trainingapp -n 50 | grep -i database

# Look for successful connection messages
sudo journalctl -u trainingapp | grep -i "initialized"
```

### 3. Jasypt Decryption Check

Verify encrypted values are decrypted correctly:

```bash
sudo journalctl -u trainingapp -n 100 | grep -i -E "encrypt|decrypt|password"
```

**Expected**: No decryption errors in logs.

### 4. Email Configuration Test

Check if mail properties are loaded correctly:

```bash
sudo journalctl -u trainingapp | grep -i "mail"
```

---

## Monitoring and Logging

### 1. Application Logs

View real-time logs:

```bash
sudo journalctl -u trainingapp -f
```

### 2. MySQL Logs

```bash
tail -f /var/log/mysql/error.log
```

### 3. System Resource Monitoring

```bash
# CPU and Memory
top -p $(pgrep -f trainingapp.jar)

# Disk I/O
iostat -x 1

# Network connections
netstat -an | grep 8080
```

### 4. Enable Debug Logging

To increase logging verbosity, add to `/opt/trainingapp/.env`:

```bash
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_EXAMPLE_TRAINING=DEBUG
```

---

## Maintenance

### 1. Regular Backups

```bash
# Database backup
mysqldump -u springuser -p training_db | gzip > /backup/training_db_$(date +%Y%m%d).sql.gz

# Configuration backup
tar -czf /backup/trainingapp_config_$(date +%Y%m%d).tar.gz /opt/trainingapp/.env
```

### 2. Update Application

```bash
# Build new version
mvn clean package -PGCP -DskipTests

# Stop old version
sudo systemctl stop trainingapp

# Backup old jar
sudo cp /opt/trainingapp/trainingapp.jar /opt/trainingapp/trainingapp.jar.backup

# Deploy new version
sudo cp target/trainingapp-*.jar /opt/trainingapp/trainingapp.jar

# Restart
sudo systemctl start trainingapp

# Verify
sudo systemctl status trainingapp
```

### 3. Rotate Encryption Master Password

1. Generate new encrypted values with new master password
2. Update `JASYPT_ENCRYPTOR_PASSWORD` in `.env`
3. Update encrypted values in `application.properties` (rebuild if needed)
4. Restart application

---

## Troubleshooting

### Issue: Application fails to start with "Decryption failed"

**Cause**: Master password mismatch

**Solution**:

```bash
# Verify JASYPT_ENCRYPTOR_PASSWORD
echo $JASYPT_ENCRYPTOR_PASSWORD

# Check encrypted values in application.properties
grep "ENC(" src/main/resources/application.properties

# Re-encrypt with correct master password
export JASYPT_ENCRYPTOR_PASSWORD=correct-password
mvn clean compile exec:java \
  -Dexec.mainClass="com.example.training.util.EncryptTool" \
  -Dexec.args="plaintext-value"
```

### Issue: MySQL connection error

**Cause**: Database not running or credentials incorrect

**Solution**:

```bash
# Check MySQL status
systemctl status mysql

# Test connection
mysql -h localhost -u springuser -p -e "SELECT 1"

# Check credentials in .env
cat /opt/trainingapp/.env | grep DB_
```

### Issue: Email sending fails

**Cause**: Gmail credentials or encryption issue

**Solution**:

```bash
# Use app password, not account password
# 1. Create app password: https://myaccount.google.com/apppasswords
# 2. Encrypt it:
export JASYPT_ENCRYPTOR_PASSWORD=your-master-password
mvn exec:java -Dexec.mainClass="com.example.training.util.EncryptTool" \
  -Dexec.args="your-app-password"
# 3. Update .env with new ENC(...) value
# 4. Restart application
```

### Issue: High memory usage

**Cause**: Insufficient heap allocation

**Solution**:
Update `ExecStart` in `/etc/systemd/system/trainingapp.service`:

```ini
ExecStart=/usr/bin/java \
  -Xmx1024m \
  -Xms512m \
  -jar /opt/trainingapp/trainingapp.jar
```

Then reload and restart:

```bash
sudo systemctl daemon-reload
sudo systemctl restart trainingapp
```

---

## Firewall Configuration

If using `ufw` or `iptables`:

```bash
# UFW
sudo ufw allow 8080/tcp

# iptables
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

---

## SSL/TLS Configuration (Optional)

For HTTPS:

1. Obtain certificate (Let's Encrypt recommended)
2. Add to `.env`:
   ```
   SERVER_SSL_ENABLED=true
   SERVER_SSL_KEY_STORE=/path/to/keystore.p12
   SERVER_SSL_KEY_STORE_PASSWORD=keystore-password
   SERVER_SSL_KEY_STORE_TYPE=PKCS12
   ```
3. Restart application

---

## Production Checklist

- [ ] MySQL database initialized with correct schema
- [ ] Database backups configured
- [ ] `JASYPT_ENCRYPTOR_PASSWORD` set via secure channel
- [ ] All encrypted values (`ENC(...)`) are correct
- [ ] `.env` file permissions set to 600
- [ ] Application starts and logs show no errors
- [ ] Health check endpoint responds (if available)
- [ ] Firewall allows port 8080 (or configured port)
- [ ] systemd service configured and enabled
- [ ] Log rotation configured
- [ ] Monitoring/alerting set up
- [ ] Backup strategy documented and tested
