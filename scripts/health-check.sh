#!/bin/bash

################################################################################
# Health Check Script
#
# Performs comprehensive health checks on deployed application
#
# Usage:
#   ./health-check.sh [environment]
#
# Examples:
#   ./health-check.sh staging
#   ./health-check.sh production
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
APP_NAME="mcp-gateway"
HEALTH_CHECK_TIMEOUT=30

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[✗]${NC} $1"
}

get_service_url() {
    local NAMESPACE="${APP_NAME}-${ENVIRONMENT}"

    if [ "$ENVIRONMENT" = "production" ]; then
        # Production uses ingress
        echo "https://api.mcpgateway.com"
    elif [ "$ENVIRONMENT" = "staging" ]; then
        # Staging uses ingress
        echo "https://staging.mcpgateway.com"
    else
        # Local/dev uses port-forward
        log_info "Using kubectl port-forward for health check"
        kubectl port-forward -n "$NAMESPACE" svc/mcp-gateway 8080:8080 &
        PF_PID=$!
        sleep 3
        echo "http://localhost:8080"
    fi
}

check_pods_ready() {
    local NAMESPACE="${APP_NAME}-${ENVIRONMENT}"

    log_info "Checking pod status..."

    READY_PODS=$(kubectl get pods -n "$NAMESPACE" \
        -l app=mcp-gateway \
        -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' | \
        grep -o "True" | wc -l)

    TOTAL_PODS=$(kubectl get pods -n "$NAMESPACE" \
        -l app=mcp-gateway \
        --no-headers | wc -l)

    if [ "$READY_PODS" -eq "$TOTAL_PODS" ] && [ "$TOTAL_PODS" -gt 0 ]; then
        log_success "All pods are ready ($READY_PODS/$TOTAL_PODS)"
        return 0
    else
        log_error "Some pods are not ready ($READY_PODS/$TOTAL_PODS)"
        kubectl get pods -n "$NAMESPACE" -l app=mcp-gateway
        return 1
    fi
}

check_actuator_health() {
    local BASE_URL="$1"
    local ENDPOINT="${BASE_URL}/actuator/health"

    log_info "Checking actuator health endpoint: $ENDPOINT"

    RESPONSE=$(curl -s -w "\n%{http_code}" --max-time "$HEALTH_CHECK_TIMEOUT" "$ENDPOINT" || echo "000")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" = "200" ]; then
        STATUS=$(echo "$BODY" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
        if [ "$STATUS" = "UP" ]; then
            log_success "Application health: UP"
            return 0
        else
            log_error "Application health: $STATUS"
            echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
            return 1
        fi
    else
        log_error "Health check failed (HTTP $HTTP_CODE)"
        return 1
    fi
}

check_actuator_readiness() {
    local BASE_URL="$1"
    local ENDPOINT="${BASE_URL}/actuator/health/readiness"

    log_info "Checking readiness..."

    RESPONSE=$(curl -s -w "\n%{http_code}" --max-time "$HEALTH_CHECK_TIMEOUT" "$ENDPOINT" || echo "000")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

    if [ "$HTTP_CODE" = "200" ]; then
        log_success "Application is ready"
        return 0
    else
        log_error "Application is not ready (HTTP $HTTP_CODE)"
        return 1
    fi
}

check_actuator_liveness() {
    local BASE_URL="$1"
    local ENDPOINT="${BASE_URL}/actuator/health/liveness"

    log_info "Checking liveness..."

    RESPONSE=$(curl -s -w "\n%{http_code}" --max-time "$HEALTH_CHECK_TIMEOUT" "$ENDPOINT" || echo "000")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

    if [ "$HTTP_CODE" = "200" ]; then
        log_success "Application is alive"
        return 0
    else
        log_error "Application liveness check failed (HTTP $HTTP_CODE)"
        return 1
    fi
}

check_database_health() {
    local BASE_URL="$1"
    local ENDPOINT="${BASE_URL}/actuator/health"

    log_info "Checking database connectivity..."

    RESPONSE=$(curl -s --max-time "$HEALTH_CHECK_TIMEOUT" "$ENDPOINT")
    DB_STATUS=$(echo "$RESPONSE" | jq -r '.components.db.status' 2>/dev/null || echo "UNKNOWN")

    if [ "$DB_STATUS" = "UP" ]; then
        log_success "Database: UP"
        return 0
    else
        log_error "Database: $DB_STATUS"
        return 1
    fi
}

check_circuit_breakers() {
    local BASE_URL="$1"
    local ENDPOINT="${BASE_URL}/actuator/health"

    log_info "Checking circuit breakers..."

    RESPONSE=$(curl -s --max-time "$HEALTH_CHECK_TIMEOUT" "$ENDPOINT")

    # Check if circuit breakers are included in health
    CB_STATUS=$(echo "$RESPONSE" | jq -r '.components.circuitBreakers.status' 2>/dev/null || echo "NOT_CONFIGURED")

    if [ "$CB_STATUS" = "UP" ] || [ "$CB_STATUS" = "NOT_CONFIGURED" ]; then
        log_success "Circuit breakers: OK"
        return 0
    else
        log_warning "Circuit breakers: $CB_STATUS"
        return 0  # Warning, not error
    fi
}

check_api_endpoint() {
    local BASE_URL="$1"
    local ENDPOINT="${BASE_URL}/api/v1/health"

    log_info "Checking API endpoint..."

    RESPONSE=$(curl -s -w "\n%{http_code}" --max-time "$HEALTH_CHECK_TIMEOUT" "$ENDPOINT" || echo "000")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "401" ]; then
        # 200 = OK, 401 = Requires auth (expected for protected endpoints)
        log_success "API is responding"
        return 0
    else
        log_error "API check failed (HTTP $HTTP_CODE)"
        return 1
    fi
}

cleanup() {
    if [ -n "${PF_PID:-}" ]; then
        log_info "Stopping port-forward..."
        kill "$PF_PID" 2>/dev/null || true
    fi
}

main() {
    if [ $# -eq 0 ]; then
        log_error "Usage: $0 <environment>"
        exit 1
    fi

    ENVIRONMENT="$1"

    if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|production)$ ]]; then
        log_error "Invalid environment: $ENVIRONMENT"
        exit 1
    fi

    trap cleanup EXIT

    log_info "Running health checks for $ENVIRONMENT environment..."
    echo

    # Get service URL
    SERVICE_URL=$(get_service_url)

    # Run all health checks
    FAILED=0

    check_pods_ready || FAILED=$((FAILED + 1))
    check_actuator_health "$SERVICE_URL" || FAILED=$((FAILED + 1))
    check_actuator_readiness "$SERVICE_URL" || FAILED=$((FAILED + 1))
    check_actuator_liveness "$SERVICE_URL" || FAILED=$((FAILED + 1))
    check_database_health "$SERVICE_URL" || FAILED=$((FAILED + 1))
    check_circuit_breakers "$SERVICE_URL" || true  # Warning only
    check_api_endpoint "$SERVICE_URL" || FAILED=$((FAILED + 1))

    echo
    if [ $FAILED -eq 0 ]; then
        log_success "✅ All health checks passed!"
        exit 0
    else
        log_error "❌ $FAILED health check(s) failed"
        exit 1
    fi
}

main "$@"
