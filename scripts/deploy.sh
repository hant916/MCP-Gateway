#!/bin/bash

################################################################################
# MCP Gateway Deployment Script
#
# Automates deployment to Kubernetes cluster
#
# Usage:
#   ./deploy.sh [environment] [options]
#
# Environments:
#   staging     - Deploy to staging
#   production  - Deploy to production (requires confirmation)
#
# Options:
#   --skip-build       Skip Docker image build
#   --skip-tests       Skip running tests
#   --skip-migration   Skip database migration
#   --dry-run          Show what would be deployed without deploying
#   --force            Skip confirmation prompts
#
# Examples:
#   ./deploy.sh staging
#   ./deploy.sh production --skip-tests
#   ./deploy.sh staging --dry-run
################################################################################

set -e  # Exit on error
set -u  # Exit on undefined variable

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
APP_NAME="mcp-gateway"
DOCKER_REGISTRY="${DOCKER_REGISTRY:-ghcr.io}"
DOCKER_IMAGE="${DOCKER_REGISTRY}/${GITHUB_REPOSITORY:-hant916/mcp-gateway}"
GIT_COMMIT=$(git rev-parse --short HEAD)
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Flags
SKIP_BUILD=false
SKIP_TESTS=false
SKIP_MIGRATION=false
DRY_RUN=false
FORCE=false

# Functions
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
        log_warning "Deployment cancelled"
        exit 1
    fi
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check required tools
    command -v kubectl >/dev/null 2>&1 || { log_error "kubectl is required but not installed"; exit 1; }
    command -v docker >/dev/null 2>&1 || { log_error "docker is required but not installed"; exit 1; }
    command -v mvn >/dev/null 2>&1 || { log_error "maven is required but not installed"; exit 1; }

    # Check kubectl context
    CURRENT_CONTEXT=$(kubectl config current-context)
    log_info "Current kubectl context: $CURRENT_CONTEXT"

    # Verify cluster connectivity
    if ! kubectl cluster-info &>/dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi

    log_success "Prerequisites check passed"
}

run_tests() {
    if [ "$SKIP_TESTS" = true ]; then
        log_warning "Skipping tests (--skip-tests flag)"
        return 0
    fi

    log_info "Running tests..."
    cd "$PROJECT_ROOT"

    mvn clean test -Dspring.profiles.active=test

    log_success "Tests passed"
}

build_application() {
    if [ "$SKIP_BUILD" = true ]; then
        log_warning "Skipping build (--skip-build flag)"
        return 0
    fi

    log_info "Building application..."
    cd "$PROJECT_ROOT"

    mvn clean package -DskipTests -Dspring.profiles.active="$ENVIRONMENT"

    log_success "Application built successfully"
}

build_docker_image() {
    if [ "$SKIP_BUILD" = true ]; then
        log_warning "Skipping Docker build (--skip-build flag)"
        return 0
    fi

    local IMAGE_TAG="${DOCKER_IMAGE}:${VERSION}"
    local IMAGE_LATEST="${DOCKER_IMAGE}:latest-${ENVIRONMENT}"

    log_info "Building Docker image: $IMAGE_TAG"

    docker build \
        --build-arg VERSION="$VERSION" \
        --build-arg GIT_COMMIT="$GIT_COMMIT" \
        --build-arg BUILD_DATE="$BUILD_DATE" \
        -t "$IMAGE_TAG" \
        -t "$IMAGE_LATEST" \
        "$PROJECT_ROOT"

    log_success "Docker image built: $IMAGE_TAG"

    if [ "$DRY_RUN" = false ]; then
        log_info "Pushing Docker image to registry..."
        docker push "$IMAGE_TAG"
        docker push "$IMAGE_LATEST"
        log_success "Docker image pushed"
    fi
}

run_database_migration() {
    if [ "$SKIP_MIGRATION" = true ]; then
        log_warning "Skipping database migration (--skip-migration flag)"
        return 0
    fi

    log_info "Running database migration..."

    if [ "$DRY_RUN" = false ]; then
        "$SCRIPT_DIR/migrate-db.sh" "$ENVIRONMENT"
    else
        log_info "Would run: $SCRIPT_DIR/migrate-db.sh $ENVIRONMENT"
    fi

    log_success "Database migration completed"
}

deploy_to_kubernetes() {
    local K8S_DIR="$PROJECT_ROOT/k8s"
    local NAMESPACE="${APP_NAME}-${ENVIRONMENT}"

    log_info "Deploying to Kubernetes (namespace: $NAMESPACE)..."

    # Create namespace if it doesn't exist
    if [ "$DRY_RUN" = false ]; then
        kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    else
        log_info "Would create namespace: $NAMESPACE"
    fi

    # Update image in deployment
    cd "$K8S_DIR"

    if [ "$ENVIRONMENT" = "production" ]; then
        K8S_OVERLAY="$K8S_DIR/production"
    elif [ "$ENVIRONMENT" = "staging" ]; then
        K8S_OVERLAY="$K8S_DIR/staging"
    else
        K8S_OVERLAY="$K8S_DIR/base"
    fi

    if [ "$DRY_RUN" = false ]; then
        kubectl apply -k "$K8S_OVERLAY" -n "$NAMESPACE"

        # Update image tag
        kubectl set image deployment/mcp-gateway \
            mcp-gateway="${DOCKER_IMAGE}:${VERSION}" \
            -n "$NAMESPACE"

        log_success "Deployed to Kubernetes"
    else
        log_info "Would deploy from: $K8S_OVERLAY"
        kubectl apply -k "$K8S_OVERLAY" -n "$NAMESPACE" --dry-run=client
    fi
}

wait_for_deployment() {
    if [ "$DRY_RUN" = true ]; then
        log_info "Dry run: skipping deployment wait"
        return 0
    fi

    local NAMESPACE="${APP_NAME}-${ENVIRONMENT}"

    log_info "Waiting for deployment to complete..."

    kubectl rollout status deployment/mcp-gateway -n "$NAMESPACE" --timeout=5m

    log_success "Deployment completed successfully"
}

run_health_check() {
    if [ "$DRY_RUN" = true ]; then
        log_info "Dry run: skipping health check"
        return 0
    fi

    log_info "Running health check..."

    "$SCRIPT_DIR/health-check.sh" "$ENVIRONMENT"

    log_success "Health check passed"
}

print_summary() {
    echo
    echo "======================================"
    echo "  Deployment Summary"
    echo "======================================"
    echo "Environment:      $ENVIRONMENT"
    echo "Version:          $VERSION"
    echo "Git Commit:       $GIT_COMMIT"
    echo "Git Branch:       $GIT_BRANCH"
    echo "Docker Image:     ${DOCKER_IMAGE}:${VERSION}"
    echo "Namespace:        ${APP_NAME}-${ENVIRONMENT}"
    echo "======================================"
    echo
}

# Main deployment flow
main() {
    # Parse arguments
    if [ $# -eq 0 ]; then
        log_error "Usage: $0 <environment> [options]"
        log_error "Example: $0 staging --skip-tests"
        exit 1
    fi

    ENVIRONMENT="$1"
    shift

    # Validate environment
    if [[ ! "$ENVIRONMENT" =~ ^(staging|production)$ ]]; then
        log_error "Invalid environment: $ENVIRONMENT"
        log_error "Valid environments: staging, production"
        exit 1
    fi

    # Parse options
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-build)
                SKIP_BUILD=true
                shift
                ;;
            --skip-tests)
                SKIP_TESTS=true
                shift
                ;;
            --skip-migration)
                SKIP_MIGRATION=true
                shift
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

    # Set version
    VERSION="${GIT_COMMIT}-$(date +%Y%m%d%H%M%S)"

    # Production confirmation
    if [ "$ENVIRONMENT" = "production" ]; then
        log_warning "⚠️  PRODUCTION DEPLOYMENT ⚠️"
        confirm "Are you sure you want to deploy to PRODUCTION?"
    fi

    print_summary

    # Execute deployment steps
    log_info "Starting deployment to $ENVIRONMENT..."

    check_prerequisites
    run_tests
    build_application
    build_docker_image
    run_database_migration
    deploy_to_kubernetes
    wait_for_deployment
    run_health_check

    log_success "🎉 Deployment to $ENVIRONMENT completed successfully!"

    if [ "$ENVIRONMENT" = "production" ]; then
        log_info "💡 Tip: Monitor the deployment at:"
        log_info "   - Grafana: http://grafana.example.com"
        log_info "   - Logs: kubectl logs -f deployment/mcp-gateway -n ${APP_NAME}-${ENVIRONMENT}"
    fi
}

# Run main function
main "$@"
