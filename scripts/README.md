# Deployment Scripts

Automated deployment and management scripts for MCP Gateway.

## 📋 Available Scripts

### 1. `deploy.sh` - One-Click Deployment

Automates the full deployment process including build, test, Docker image creation, and Kubernetes deployment.

**Usage:**
```bash
./deploy.sh <environment> [options]
```

**Environments:**
- `staging` - Deploy to staging environment
- `production` - Deploy to production (requires confirmation)

**Options:**
- `--skip-build` - Skip Maven build
- `--skip-tests` - Skip running tests
- `--skip-migration` - Skip database migration
- `--dry-run` - Show what would be deployed without deploying
- `--force` - Skip confirmation prompts

**Examples:**
```bash
# Deploy to staging
./deploy.sh staging

# Deploy to production with all safety checks
./deploy.sh production

# Dry run to production
./deploy.sh production --dry-run

# Quick deploy (skip tests)
./deploy.sh staging --skip-tests
```

**Deployment Flow:**
1. ✅ Check prerequisites (kubectl, docker, maven)
2. ✅ Run tests
3. ✅ Build application (Maven)
4. ✅ Build Docker image
5. ✅ Push to Docker registry
6. ✅ Run database migrations
7. ✅ Deploy to Kubernetes
8. ✅ Wait for rollout to complete
9. ✅ Run health checks

---

### 2. `rollback.sh` - Quick Rollback

Rolls back deployment to previous version in case of issues.

**Usage:**
```bash
./rollback.sh <environment> [options]
```

**Options:**
- `--revision N` - Rollback to specific revision (default: previous)
- `--dry-run` - Show what would be rolled back
- `--force` - Skip confirmation prompts

**Examples:**
```bash
# Rollback to previous version
./rollback.sh production

# Rollback to specific revision
./rollback.sh staging --revision 3

# Dry run
./rollback.sh production --dry-run
```

**Process:**
1. Show deployment history
2. Confirm rollback
3. Execute kubectl rollout undo
4. Wait for rollback to complete
5. Verify all pods are ready
6. Run health checks

---

### 3. `migrate-db.sh` - Database Migration

Runs Flyway database migrations safely.

**Usage:**
```bash
./migrate-db.sh <environment> [options]
```

**Options:**
- `--dry-run` - Show pending migrations without applying
- `--repair` - Repair failed migrations
- `--validate` - Validate migration checksums

**Examples:**
```bash
# Run migrations on staging
./migrate-db.sh staging

# Show pending migrations
./migrate-db.sh production --dry-run

# Repair failed migration
./migrate-db.sh staging --repair

# Validate migrations
./migrate-db.sh production --validate
```

**Features:**
- Automatic backup before migration (production only)
- Baseline on migrate for existing databases
- Migration validation
- Failure repair

---

### 4. `health-check.sh` - Comprehensive Health Check

Performs thorough health checks on deployed application.

**Usage:**
```bash
./health-check.sh <environment>
```

**Examples:**
```bash
# Check staging health
./health-check.sh staging

# Check production health
./health-check.sh production
```

**Checks Performed:**
1. ✅ Kubernetes pod status
2. ✅ Actuator health endpoint
3. ✅ Readiness probe
4. ✅ Liveness probe
5. ✅ Database connectivity
6. ✅ Circuit breaker status
7. ✅ API endpoint responsiveness

**Output:**
```
[✓] All pods are ready (3/3)
[✓] Application health: UP
[✓] Application is ready
[✓] Application is alive
[✓] Database: UP
[✓] Circuit breakers: OK
[✓] API is responding

✅ All health checks passed!
```

---

### 5. `backup.sh` - Database Backup

Creates compressed database backups with retention policy.

**Usage:**
```bash
./backup.sh <environment> [options]
```

**Options:**
- `--auto` - Run in non-interactive mode
- `--retention N` - Keep N most recent backups (default: 7)

**Examples:**
```bash
# Create backup
./backup.sh production

# Auto backup with 14-day retention
./backup.sh staging --auto --retention 14
```

**Features:**
- Compressed backups (gzip)
- Automatic retention management
- Backup verification
- Optional S3 upload
- Size verification

**Backup Location:**
```
backups/
├── production/
│   ├── backup_production_20240115_120000.sql.gz
│   └── backup_production_20240116_120000.sql.gz
└── staging/
    └── backup_staging_20240115_120000.sql.gz
```

---

### 6. `restore.sh` - Database Restore

Restores database from backup file.

**Usage:**
```bash
./restore.sh <environment> <backup_file> [options]
```

**Options:**
- `--force` - Skip confirmation prompts

**Examples:**
```bash
# Restore staging database
./restore.sh staging backups/staging/backup_20240115_120000.sql.gz

# Force restore production (dangerous!)
./restore.sh production backups/production/backup_20240115_120000.sql.gz --force
```

**Safety Features:**
- Backup current database before restore
- Confirmation prompts (especially for production)
- Application shutdown during restore
- Verification after restore
- Automatic application restart

**Process:**
1. 🔍 Verify backup file integrity
2. 💾 Create backup of current database
3. 🛑 Stop application
4. 📥 Restore database
5. ✅ Verify restoration
6. ▶️ Start application

---

### 7. `ailuros-v0.2-a-smoke.sh` - AILUROS Governance Smoke (curl set)

Runs an end-to-end smoke flow for AILUROS v0.2-A:
- seed baseline/candidate call events via `/api/ailuros/ingest`
- configure budget policy
- trigger budget evaluation
- configure release baseline
- run offline regression
- verify governance incidents exist (`BUDGET_EXCEEDED`, `RELEASE_BLOCKED`)

**Usage:**
```bash
./scripts/ailuros-v0.2-a-smoke.sh
```

**Optional environment overrides:**
```bash
BASE_URL=http://localhost:8080
APP_ID=clarity
ENV_NAME=prod
ROUTE=/v1/chat/completions
RANGE=7d
```

Windows PowerShell version:
```powershell
.\scripts\ailuros-v0.2-a-smoke.ps1
```

---

### 8. `ailuros-dashboard-demo.ps1` - Windows Demo Launcher

PowerShell demo launcher for investor-style walkthrough:
- checks backend health
- optionally runs the smoke script
- prints live KPI/Budget/Release Gate snapshot
- opens `ailuros-dashboard.html` with aligned query params
- prints a 5-step demo checklist

**Usage (PowerShell):**
```powershell
.\scripts\ailuros-dashboard-demo.ps1
```

**Options:**
```powershell
.\scripts\ailuros-dashboard-demo.ps1 -SkipSmoke
.\scripts\ailuros-dashboard-demo.ps1 -NoBrowser
.\scripts\ailuros-dashboard-demo.ps1 -BaseUrl http://localhost:8080 -AppId clarity -EnvName prod -Route /v1/chat/completions -Range 7d
```

---

## 🚀 Quick Start Guide

### Initial Setup

1. **Make scripts executable:**
```bash
chmod +x scripts/*.sh
```

2. **Configure environment variables:**
```bash
# Copy example
cp .env.example .env.staging
cp .env.example .env.production

# Edit with your values
vim .env.production
```

3. **Required environment variables:**
```bash
# Database
DATABASE_HOST=localhost
DATABASE_PORT=5432
DATABASE_NAME=mcpgateway
DATABASE_USERNAME=mcpgateway
DATABASE_PASSWORD=your_secure_password

# Docker Registry
DOCKER_REGISTRY=ghcr.io
GITHUB_REPOSITORY=your_org/mcp-gateway

# Optional: S3 Backup
S3_BACKUP_BUCKET=mcp-gateway-backups
```

---

### Common Workflows

#### Deploy to Staging
```bash
# Full deployment with tests
./scripts/deploy.sh staging

# Run health check
./scripts/health-check.sh staging
```

#### Deploy to Production
```bash
# Dry run first
./scripts/deploy.sh production --dry-run

# Actual deployment
./scripts/deploy.sh production

# Monitor logs
kubectl logs -f deployment/mcp-gateway -n mcp-gateway-production
```

#### Emergency Rollback
```bash
# Rollback production immediately
./scripts/rollback.sh production --force

# Verify health
./scripts/health-check.sh production
```

#### Database Maintenance
```bash
# Backup before migration
./scripts/backup.sh production --auto

# Run migration
./scripts/migrate-db.sh production

# If migration fails, restore
./scripts/restore.sh production backups/production/latest.sql.gz
```

---

## 📊 Monitoring Deployments

### View Deployment Status
```bash
# Kubernetes deployment
kubectl get deployments -n mcp-gateway-production

# Pod status
kubectl get pods -n mcp-gateway-production

# Deployment history
kubectl rollout history deployment/mcp-gateway -n mcp-gateway-production
```

### View Logs
```bash
# Live logs
kubectl logs -f deployment/mcp-gateway -n mcp-gateway-production

# Previous pod logs
kubectl logs -p <pod-name> -n mcp-gateway-production

# All pods
kubectl logs -l app=mcp-gateway -n mcp-gateway-production --tail=100
```

### Grafana Dashboards
After deployment, monitor at:
- System Health: http://grafana.example.com/d/mcp-system-health
- Business Metrics: http://grafana.example.com/d/mcp-business-metrics
- Circuit Breakers: http://grafana.example.com/d/mcp-circuit-breaker

---

## 🔧 Troubleshooting

### Deployment Fails

**Check prerequisites:**
```bash
kubectl cluster-info
docker info
mvn --version
```

**View deployment logs:**
```bash
kubectl describe deployment mcp-gateway -n mcp-gateway-production
kubectl logs deployment/mcp-gateway -n mcp-gateway-production
```

### Health Check Fails

**Manual health check:**
```bash
# Port-forward to pod
kubectl port-forward -n mcp-gateway-production svc/mcp-gateway 8080:8080

# Check health endpoint
curl http://localhost:8080/actuator/health | jq
```

### Database Migration Fails

**Check migration status:**
```bash
# Connect to database
psql -h $DB_HOST -U $DB_USER -d mcpgateway

# View migration history
SELECT * FROM flyway_schema_history ORDER BY installed_on DESC;
```

**Repair and retry:**
```bash
./scripts/migrate-db.sh production --repair
./scripts/migrate-db.sh production
```

### Rollback Issues

**Force rollback:**
```bash
# Manual rollback
kubectl rollout undo deployment/mcp-gateway -n mcp-gateway-production

# To specific revision
kubectl rollout undo deployment/mcp-gateway --to-revision=2 -n mcp-gateway-production
```

---

## 📋 Pre-Deployment Checklist

### Before Production Deployment

- [ ] All tests pass locally
- [ ] Staging deployment successful
- [ ] Database migrations tested on staging
- [ ] Backup created
- [ ] Team notified
- [ ] Maintenance window scheduled (if needed)
- [ ] Rollback plan ready
- [ ] Monitoring alerts configured

### After Deployment

- [ ] Health checks pass
- [ ] No errors in logs
- [ ] Grafana dashboards show normal metrics
- [ ] Test critical user flows
- [ ] Monitor for 15-30 minutes
- [ ] Update deployment documentation

---

## 🔐 Security Notes

1. **Never commit `.env` files** - Use `.env.example` as template
2. **Rotate credentials regularly** - Database passwords, JWT secrets
3. **Use separate credentials** for each environment
4. **Enable RBAC** in Kubernetes
5. **Restrict backup access** - Backups contain sensitive data
6. **Use secrets management** - Consider HashiCorp Vault or AWS Secrets Manager

---

## 📚 Additional Resources

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Docker Documentation](https://docs.docker.com/)
- [Grafana Monitoring Guide](../grafana/README.md)
- [Production Deployment Checklist](../docs/PRODUCTION_DEPLOYMENT_CHECKLIST.md)

---

**Need Help?** Contact the DevOps team or check the [Troubleshooting Guide](../docs/TROUBLESHOOTING.md).
