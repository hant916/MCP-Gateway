#!/bin/bash

################################################################################
# Database Migration Script
#
# Runs Flyway database migrations
#
# Usage:
#   ./migrate-db.sh [environment] [options]
#
# Options:
#   --dry-run       Show pending migrations without applying
#   --repair        Repair failed migrations
#   --validate      Validate migration checksums
#
# Examples:
#   ./migrate-db.sh staging
#   ./migrate-db.sh production --dry-run
#   ./migrate-db.sh staging --repair
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

# Flags
DRY_RUN=false
REPAIR=false
VALIDATE=false

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
        log_info "Loading database config from $ENV_FILE"
        # shellcheck source=/dev/null
        source "$ENV_FILE"
    else
        log_warning "Environment file not found: $ENV_FILE"
        log_info "Using default configuration"
    fi

    # Set defaults if not provided
    DB_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/mcpgateway}"
    DB_USER="${DATABASE_USERNAME:-mcpgateway}"
    DB_PASSWORD="${DATABASE_PASSWORD:-}"

    if [ -z "$DB_PASSWORD" ]; then
        log_error "DATABASE_PASSWORD is not set"
        exit 1
    fi
}

show_migration_info() {
    log_info "Database Migration Info"
    echo "  Environment: $ENVIRONMENT"
    echo "  Database URL: $DB_URL"
    echo "  Database User: $DB_USER"
    echo
}

run_flyway_info() {
    log_info "Getting migration status..."

    mvn -f "$PROJECT_ROOT/pom.xml" flyway:info \
        -Dflyway.url="$DB_URL" \
        -Dflyway.user="$DB_USER" \
        -Dflyway.password="$DB_PASSWORD" \
        -Dflyway.locations="classpath:db/migration"
}

run_flyway_validate() {
    log_info "Validating migrations..."

    mvn -f "$PROJECT_ROOT/pom.xml" flyway:validate \
        -Dflyway.url="$DB_URL" \
        -Dflyway.user="$DB_USER" \
        -Dflyway.password="$DB_PASSWORD" \
        -Dflyway.locations="classpath:db/migration"

    log_success "Migration validation passed"
}

run_flyway_migrate() {
    if [ "$DRY_RUN" = true ]; then
        log_info "Dry run: Would apply the following migrations:"
        run_flyway_info
        return 0
    fi

    log_info "Applying database migrations..."

    mvn -f "$PROJECT_ROOT/pom.xml" flyway:migrate \
        -Dflyway.url="$DB_URL" \
        -Dflyway.user="$DB_USER" \
        -Dflyway.password="$DB_PASSWORD" \
        -Dflyway.locations="classpath:db/migration" \
        -Dflyway.baselineOnMigrate=true

    log_success "Database migrations completed successfully"
}

run_flyway_repair() {
    log_warning "Repairing failed migrations..."

    mvn -f "$PROJECT_ROOT/pom.xml" flyway:repair \
        -Dflyway.url="$DB_URL" \
        -Dflyway.user="$DB_USER" \
        -Dflyway.password="$DB_PASSWORD" \
        -Dflyway.locations="classpath:db/migration"

    log_success "Migration repair completed"
}

backup_database() {
    if [ "$ENVIRONMENT" = "production" ]; then
        log_info "Creating database backup before migration..."

        "$SCRIPT_DIR/backup.sh" "$ENVIRONMENT" --auto

        log_success "Database backup created"
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
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --repair)
                REPAIR=true
                shift
                ;;
            --validate)
                VALIDATE=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    load_database_config
    show_migration_info

    # Backup before production migration
    if [ "$DRY_RUN" = false ] && [ "$REPAIR" = false ]; then
        backup_database
    fi

    # Execute requested operation
    if [ "$REPAIR" = true ]; then
        run_flyway_repair
    elif [ "$VALIDATE" = true ]; then
        run_flyway_validate
    else
        run_flyway_info
        echo
        run_flyway_migrate
    fi

    log_success "✅ Database migration task completed"
}

main "$@"
