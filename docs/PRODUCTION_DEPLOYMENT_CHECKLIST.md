# 🚀 Production Deployment Checklist

## Pre-Deployment Security Checklist

### 🔐 Security Configuration

- [ ] **JWT Secret Key**
  - [ ] Generate a strong random JWT secret key
  - [ ] Use: `openssl rand -hex 32`
  - [ ] Set `JWT_SECRET_KEY` environment variable
  - [ ] NEVER use the default development key

- [ ] **Database Security**
  - [ ] Use strong database password
  - [ ] Set `DATABASE_PASSWORD` environment variable
  - [ ] Restrict database network access
  - [ ] Enable SSL/TLS for database connections

- [ ] **CORS Configuration**
  - [ ] Set `CORS_ALLOWED_ORIGINS` to specific domains
  - [ ] Remove wildcard (`*`) from allowed origins
  - [ ] Example: `https://app.yourdomain.com,https://admin.yourdomain.com`
  - [ ] Set `CORS_ALLOW_CREDENTIALS=true` if needed

- [ ] **Swagger/API Documentation**
  - [ ] Set `SWAGGER_ENABLED=false` in production
  - [ ] API docs should NOT be publicly accessible

- [ ] **H2 Console**
  - [ ] Set `H2_CONSOLE_ENABLED=false`
  - [ ] H2 should ONLY be used in development

### 🗄️ Database Configuration

- [ ] **Database Setup**
  - [ ] Use PostgreSQL or MySQL (NOT H2)
  - [ ] Create production database
  - [ ] Create dedicated database user
  - [ ] Grant minimal required privileges

- [ ] **JPA Configuration**
  - [ ] Set `JPA_DDL_AUTO=validate` (NEVER use `update` in production)
  - [ ] Run Flyway migrations manually first
  - [ ] Set `JPA_SHOW_SQL=false`
  - [ ] Set `JPA_FORMAT_SQL=false`

- [ ] **Connection Pool**
  - [ ] Configure `DB_POOL_SIZE` based on load (default: 20)
  - [ ] Set `DB_POOL_MIN_IDLE` (default: 5)
  - [ ] Configure leak detection threshold

- [ ] **Backup Strategy**
  - [ ] Set up automated database backups
  - [ ] Test backup restoration process
  - [ ] Configure backup retention policy

### 🌐 Application Configuration

- [ ] **Environment**
  - [ ] Set `SPRING_PROFILES_ACTIVE=prod`
  - [ ] Configure all required environment variables
  - [ ] Verify `.env` file is NOT in version control

- [ ] **Server**
  - [ ] Set `SERVER_PORT` (default: 8080)
  - [ ] Configure reverse proxy (Nginx/Apache)
  - [ ] Enable HTTPS/TLS
  - [ ] Configure SSL certificates

- [ ] **Logging**
  - [ ] Set `LOG_LEVEL=INFO` or `WARN`
  - [ ] Set `APP_LOG_LEVEL=INFO`
  - [ ] Configure log rotation
  - [ ] Set up centralized logging (optional)

### 📊 Monitoring & Observability

- [ ] **Application Monitoring**
  - [ ] Enable Spring Boot Actuator endpoints
  - [ ] Set up health checks
  - [ ] Configure metrics export (Prometheus, etc.)
  - [ ] Set up alerting rules

- [ ] **Infrastructure Monitoring**
  - [ ] Monitor server resources (CPU, RAM, Disk)
  - [ ] Monitor database performance
  - [ ] Monitor network connectivity
  - [ ] Set up uptime monitoring

### 🔒 Network & Infrastructure

- [ ] **Firewall Rules**
  - [ ] Restrict database port access
  - [ ] Only expose necessary ports
  - [ ] Configure IP whitelisting if needed

- [ ] **Load Balancer** (if applicable)
  - [ ] Configure health check endpoint
  - [ ] Set up session affinity if needed
  - [ ] Configure SSL termination

- [ ] **CDN** (if applicable)
  - [ ] Configure static asset caching
  - [ ] Set up cache invalidation

### 🧪 Testing

- [ ] **Pre-Deployment Testing**
  - [ ] Run all unit tests: `mvn test`
  - [ ] Run integration tests
  - [ ] Perform load testing
  - [ ] Test database migrations

- [ ] **Smoke Tests**
  - [ ] Test authentication endpoint
  - [ ] Test session creation
  - [ ] Test all transport protocols
  - [ ] Test billing system

### 📦 Deployment Process

- [ ] **Build**
  - [ ] Clean build: `mvn clean package -DskipTests`
  - [ ] Verify JAR file created
  - [ ] Check JAR file size

- [ ] **Database Migration**
  - [ ] Review pending Flyway migrations
  - [ ] Run migrations manually: `mvn flyway:migrate`
  - [ ] Verify migration success

- [ ] **Deployment**
  - [ ] Stop old application instance
  - [ ] Deploy new JAR file
  - [ ] Start application
  - [ ] Verify application startup
  - [ ] Check logs for errors

- [ ] **Post-Deployment Verification**
  - [ ] Verify health check endpoint: `/actuator/health`
  - [ ] Test critical API endpoints
  - [ ] Monitor error logs
  - [ ] Verify database connectivity

### 🔄 Rollback Plan

- [ ] **Rollback Procedure**
  - [ ] Keep previous JAR file version
  - [ ] Document database migration rollback steps
  - [ ] Test rollback procedure
  - [ ] Set maximum rollback time

### 📋 Environment Variables Checklist

```bash
# Required Variables
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL=jdbc:postgresql://prod-db:5432/mcpgateway
export DATABASE_USERNAME=mcpgateway_user
export DATABASE_PASSWORD=<strong-password>
export JWT_SECRET_KEY=<strong-random-key>

# Security
export CORS_ALLOWED_ORIGINS=https://app.yourdomain.com
export SWAGGER_ENABLED=false
export H2_CONSOLE_ENABLED=false

# Database
export JPA_DDL_AUTO=validate
export JPA_SHOW_SQL=false

# Optional
export SERVER_PORT=8080
export LOG_LEVEL=INFO
export DB_POOL_SIZE=20
```

### 🚨 Critical Production Rules

1. **NEVER** use `ddl-auto=update` in production
2. **NEVER** commit `.env` file to version control
3. **NEVER** use default JWT secret key
4. **NEVER** enable H2 console in production
5. **NEVER** enable Swagger in production
6. **ALWAYS** use HTTPS in production
7. **ALWAYS** use strong database passwords
8. **ALWAYS** restrict CORS to specific domains
9. **ALWAYS** test database backups regularly
10. **ALWAYS** monitor application logs

### 📞 Emergency Contacts

- [ ] Database Admin: _______________
- [ ] DevOps Team: _______________
- [ ] On-Call Engineer: _______________

### 📝 Deployment Sign-Off

- [ ] Security Review Completed
- [ ] Database Configuration Verified
- [ ] Environment Variables Set
- [ ] Monitoring Configured
- [ ] Rollback Plan Tested
- [ ] Team Notified

**Deployed By:** _______________
**Date:** _______________
**Version:** _______________

---

## Quick Deployment Commands

### Development
```bash
# Set profile
export SPRING_PROFILES_ACTIVE=dev

# Run application
mvn spring-boot:run
```

### Production
```bash
# Set production profile
export SPRING_PROFILES_ACTIVE=prod

# Build
mvn clean package -DskipTests

# Run
java -jar target/mcpgateway-spring-1.0.0.jar
```

### With Docker
```bash
# Build Docker image
docker build -t mcpgateway:1.0.0 .

# Run with environment file
docker run -d \
  --env-file .env.prod \
  -p 8080:8080 \
  mcpgateway:1.0.0
```

---

**Document Version:** 1.0
**Last Updated:** 2026-01-03
