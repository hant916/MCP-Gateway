#!/bin/bash

################################################################################
# Database Restore Script
#
# Restores database from backup file
#
# Usage:
#   ./restore.sh [environment] [backup_file] [options]
#
# Options:
#   --force         Skip confirmation prompts
#
# Examples:
#   ./restore.sh staging backups/staging/backup_20240115_120000.sql.gz
#   ./restore.sh production backups/production/backup_20240115_120000.sql.gz --force
################################################################################

set -e
set -u

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
FORCE=false

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

confirm() {
    if [ "$FORCE" = true ]; then
        return 0
    fi

    read -p "$1 (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_warning "Restore cancelled"
        exit 1
    fi
}

load_database_config() {
    local ENV_FILE="$PROJECT_ROOT/.env.${ENVIRONMENT}"

    if [ -f "$ENV_FILE" ]; then
        # shellcheck source=/dev/null
        source "$ENV_FILE"
    fi

    DB_HOST="${DATABASE_HOST:-localhost}"
    DB_PORT="${DATABASE_PORT:-5432}"
    DB_NAME="${DATABASE_NAME:-mcpgateway}"
    DB_USER="${DATABASE_USERNAME:-mcpgateway}"
    DB_PASSWORD="${DATABASE_PASSWORD:-}"

    if [ -z "$DB_PASSWORD" ]; then
        log_error "DATABASE_PASSWORD is not set"
        exit 1
    fi

    export PGPASSWORD="$DB_PASSWORD"
}

verify_backup_file() {
    local BACKUP_FILE="$1"

    if [ ! -f "$BACKUP_FILE" ]; then
        log_error "Backup file not found: $BACKUP_FILE"
        exit 1
    fi

    log_info "Verifying backup file..."

    # Check if file can be decompressed
    if gzip -t "$BACKUP_FILE" 2>/dev/null; then
        log_success "Backup file is valid"
    else
        log_error "Backup file is corrupted"
        exit 1
    fi

    # Show file info
    BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    BACKUP_DATE=$(stat -f%Sm "$BACKUP_FILE" 2>/dev/null || stat -c%y "$BACKUP_FILE")
    log_info "Backup size: $BACKUP_SIZE"
    log_info "Backup date: $BACKUP_DATE"
}

create_pre_restore_backup() {
    log_warning "Creating backup of current database before restore..."

    "$SCRIPT_DIR/backup.sh" "$ENVIRONMENT" --auto

    log_success "Pre-restore backup created"
}

stop_application() {
    local NAMESPACE="${APP_NAME:-mcp-gateway}-${ENVIRONMENT}"

    log_warning "Scaling down application..."

    kubectl scale deployment/mcp-gateway \
        --replicas=0 \
        -n "$NAMESPACE" || log_warning "Could not scale down application (may not be in Kubernetes)"
}

restore_database() {
    local BACKUP_FILE="$1"

    log_info "Restoring database from backup..."
    log_info "Database: $DB_NAME@$DB_HOST:$DB_PORT"

    # Restore using psql
    gunzip -c "$BACKUP_FILE" | \
        psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            --no-password \
            -d postgres \
            -v ON_ERROR_STOP=1

    log_success "Database restored successfully"
}

start_application() {
    local NAMESPACE="${APP_NAME:-mcp-gateway}-${ENVIRONMENT}"
    local REPLICAS="${REPLICAS:-3}"

    log_info "Scaling up application to $REPLICAS replicas..."

    kubectl scale deployment/mcp-gateway \
        --replicas="$REPLICAS" \
        -n "$NAMESPACE" || log_warning "Could not scale up application (may not be in Kubernetes)"
}

verify_restore() {
    log_info "Verifying database restore..."

    # Simple connection test
    psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        --no-password \
        -c "SELECT COUNT(*) FROM flyway_schema_history;" > /dev/null

    log_success "Database connection verified"
}

main() {
    if [ $# -lt 2 ]; then
        log_error "Usage: $0 <environment> <backup_file> [options]"
        exit 1
    fi

    ENVIRONMENT="$1"
    BACKUP_FILE="$2"
    shift 2

    if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|production)$ ]]; then
        log_error "Invalid environment: $ENVIRONMENT"
        exit 1
    fi

    # Parse options
    while [[ $# -gt 0 ]]; do
        case $1 in
            --force)
                FORCE=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    # Production extra confirmation
    if [ "$ENVIRONMENT" = "production" ]; then
        log_warning "⚠️  PRODUCTION DATABASE RESTORE ⚠️"
        log_warning "This will OVERWRITE the production database!"
        confirm "Are you ABSOLUTELY SURE you want to proceed?"
    fi

    log_info "Starting database restore for $ENVIRONMENT environment..."

    load_database_config
    verify_backup_file "$BACKUP_FILE"

    confirm "Proceed with database restore?"

    create_pre_restore_backup
    stop_application
    restore_database "$BACKUP_FILE"
    verify_restore
    start_application

    log_success "✅ Database restore completed successfully!"
    log_info "Run health checks to verify application:"
    log_info "  ./scripts/health-check.sh $ENVIRONMENT"
}

main "$@"
