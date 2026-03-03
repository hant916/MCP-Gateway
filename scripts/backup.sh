#!/bin/bash

################################################################################
# Database Backup Script
#
# Creates compressed database backups with retention policy
#
# Usage:
#   ./backup.sh [environment] [options]
#
# Options:
#   --auto          Run in non-interactive mode
#   --retention N   Keep N most recent backups (default: 7)
#
# Examples:
#   ./backup.sh production
#   ./backup.sh staging --auto --retention 14
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
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
RETENTION_DAYS=7
AUTO_MODE=false

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

create_backup_directory() {
    local ENV_BACKUP_DIR="${BACKUP_DIR}/${ENVIRONMENT}"

    if [ ! -d "$ENV_BACKUP_DIR" ]; then
        log_info "Creating backup directory: $ENV_BACKUP_DIR"
        mkdir -p "$ENV_BACKUP_DIR"
    fi

    echo "$ENV_BACKUP_DIR"
}

create_backup() {
    local ENV_BACKUP_DIR="$1"
    local TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    local BACKUP_FILE="${ENV_BACKUP_DIR}/backup_${ENVIRONMENT}_${TIMESTAMP}.sql.gz"

    log_info "Creating database backup..."
    log_info "Database: $DB_NAME@$DB_HOST:$DB_PORT"
    log_info "Backup file: $BACKUP_FILE"

    # Create backup using pg_dump
    pg_dump \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        --no-password \
        --verbose \
        --clean \
        --if-exists \
        --create \
        | gzip > "$BACKUP_FILE"

    # Verify backup was created
    if [ -f "$BACKUP_FILE" ]; then
        BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
        log_success "Backup created: $BACKUP_FILE ($BACKUP_SIZE)"
        echo "$BACKUP_FILE"
    else
        log_error "Backup failed"
        exit 1
    fi
}

cleanup_old_backups() {
    local ENV_BACKUP_DIR="$1"

    log_info "Cleaning up old backups (retention: $RETENTION_DAYS days)..."

    # Find and delete backups older than retention period
    find "$ENV_BACKUP_DIR" \
        -name "backup_${ENVIRONMENT}_*.sql.gz" \
        -type f \
        -mtime +$RETENTION_DAYS \
        -delete

    # List remaining backups
    REMAINING=$(find "$ENV_BACKUP_DIR" -name "backup_${ENVIRONMENT}_*.sql.gz" | wc -l)
    log_info "Remaining backups: $REMAINING"
}

upload_to_s3() {
    local BACKUP_FILE="$1"

    if [ -z "${S3_BACKUP_BUCKET:-}" ]; then
        log_info "S3_BACKUP_BUCKET not configured, skipping S3 upload"
        return 0
    fi

    log_info "Uploading backup to S3..."

    aws s3 cp "$BACKUP_FILE" \
        "s3://${S3_BACKUP_BUCKET}/${ENVIRONMENT}/$(basename "$BACKUP_FILE")" \
        --storage-class STANDARD_IA

    log_success "Backup uploaded to S3"
}

verify_backup() {
    local BACKUP_FILE="$1"

    log_info "Verifying backup integrity..."

    # Check if file can be decompressed
    if gzip -t "$BACKUP_FILE" 2>/dev/null; then
        log_success "Backup file integrity verified"
    else
        log_error "Backup file is corrupted"
        exit 1
    fi

    # Check minimum size (should be at least 1KB)
    MIN_SIZE=1024
    ACTUAL_SIZE=$(stat -f%z "$BACKUP_FILE" 2>/dev/null || stat -c%s "$BACKUP_FILE")

    if [ "$ACTUAL_SIZE" -gt "$MIN_SIZE" ]; then
        log_success "Backup size verified ($ACTUAL_SIZE bytes)"
    else
        log_error "Backup file is too small ($ACTUAL_SIZE bytes)"
        exit 1
    fi
}

main() {
    if [ $# -eq 0 ]; then
        log_error "Usage: $0 <environment> [options]"
        exit 1
    fi

    ENVIRONMENT="$1"
    shift

    if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|production)$ ]]; then
        log_error "Invalid environment: $ENVIRONMENT"
        exit 1
    fi

    # Parse options
    while [[ $# -gt 0 ]]; do
        case $1 in
            --auto)
                AUTO_MODE=true
                shift
                ;;
            --retention)
                RETENTION_DAYS="$2"
                shift 2
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    log_info "Starting database backup for $ENVIRONMENT environment..."

    load_database_config

    ENV_BACKUP_DIR=$(create_backup_directory)
    BACKUP_FILE=$(create_backup "$ENV_BACKUP_DIR")
    verify_backup "$BACKUP_FILE"
    upload_to_s3 "$BACKUP_FILE"
    cleanup_old_backups "$ENV_BACKUP_DIR"

    log_success "✅ Backup completed successfully!"
    log_info "Backup location: $BACKUP_FILE"
}

main "$@"
