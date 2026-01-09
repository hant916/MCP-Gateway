#!/bin/bash

################################################################################
# MCP Gateway Rollback Script
#
# Rolls back deployment to previous version
#
# Usage:
#   ./rollback.sh [environment] [options]
#
# Environments:
#   staging     - Rollback staging
#   production  - Rollback production (requires confirmation)
#
# Options:
#   --revision N    Rollback to specific revision (default: previous)
#   --dry-run       Show what would be rolled back
#   --force         Skip confirmation prompts
#
# Examples:
#   ./rollback.sh staging
#   ./rollback.sh production --revision 3
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
APP_NAME="mcp-gateway"

# Flags
DRY_RUN=false
FORCE=false
REVISION=""

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
        log_warning "Rollback cancelled"
        exit 1
    fi
}

show_deployment_history() {
    local NAMESPACE="${APP_NAME}-${ENVIRONMENT}"

    log_info "Deployment history:"
    kubectl rollout history deployment/mcp-gateway -n "$NAMESPACE"
}

rollback_deployment() {
    local NAMESPACE="${APP_NAME}-${ENVIRONMENT}"

    if [ "$DRY_RUN" = true ]; then
        log_info "Dry run: Would rollback deployment in namespace $NAMESPACE"
        if [ -n "$REVISION" ]; then
            log_info "Target revision: $REVISION"
        else
            log_info "Target: Previous revision"
        fi
        return 0
    fi

    log_info "Rolling back deployment..."

    if [ -n "$REVISION" ]; then
        kubectl rollout undo deployment/mcp-gateway \
            --to-revision="$REVISION" \
            -n "$NAMESPACE"
    else
        kubectl rollout undo deployment/mcp-gateway \
            -n "$NAMESPACE"
    fi

    # Wait for rollback to complete
    kubectl rollout status deployment/mcp-gateway \
        -n "$NAMESPACE" \
        --timeout=5m

    log_success "Rollback completed"
}

verify_rollback() {
    if [ "$DRY_RUN" = true ]; then
        return 0
    fi

    local NAMESPACE="${APP_NAME}-${ENVIRONMENT}"

    log_info "Verifying rollback..."

    # Check pod status
    READY_PODS=$(kubectl get pods -n "$NAMESPACE" \
        -l app=mcp-gateway \
        -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' | \
        grep -o "True" | wc -l)

    TOTAL_PODS=$(kubectl get pods -n "$NAMESPACE" \
        -l app=mcp-gateway \
        --no-headers | wc -l)

    log_info "Ready pods: $READY_PODS/$TOTAL_PODS"

    if [ "$READY_PODS" -eq "$TOTAL_PODS" ] && [ "$TOTAL_PODS" -gt 0 ]; then
        log_success "All pods are ready"
    else
        log_error "Some pods are not ready"
        kubectl get pods -n "$NAMESPACE" -l app=mcp-gateway
        exit 1
    fi

    # Run health check
    "$SCRIPT_DIR/health-check.sh" "$ENVIRONMENT"
}

main() {
    if [ $# -eq 0 ]; then
        log_error "Usage: $0 <environment> [options]"
        exit 1
    fi

    ENVIRONMENT="$1"
    shift

    if [[ ! "$ENVIRONMENT" =~ ^(staging|production)$ ]]; then
        log_error "Invalid environment: $ENVIRONMENT"
        exit 1
    fi

    # Parse options
    while [[ $# -gt 0 ]]; do
        case $1 in
            --revision)
                REVISION="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
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

    # Production confirmation
    if [ "$ENVIRONMENT" = "production" ]; then
        log_warning "⚠️  PRODUCTION ROLLBACK ⚠️"
        confirm "Are you sure you want to rollback PRODUCTION?"
    fi

    show_deployment_history
    echo

    confirm "Proceed with rollback?"

    rollback_deployment
    verify_rollback

    log_success "🎉 Rollback completed successfully!"
    log_info "Current deployment status:"
    kubectl get deployment mcp-gateway -n "${APP_NAME}-${ENVIRONMENT}"
}

main "$@"
