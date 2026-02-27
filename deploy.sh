#!/bin/bash
# ============================================================================
# Sell The Old Car - Production Deployment Script
# Usage: ./deploy.sh [command]
# Commands: start, stop, restart, logs, status, build, clean
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="car-selling"

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose is not installed"
        exit 1
    fi
    
    # Check if .env file exists
    if [ ! -f "$SCRIPT_DIR/.env" ]; then
        log_warn ".env file not found!"
        log_info "Creating .env from .env.example..."
        if [ -f "$SCRIPT_DIR/.env.example" ]; then
            cp "$SCRIPT_DIR/.env.example" "$SCRIPT_DIR/.env"
            log_warn "Please edit .env file with your actual values before starting!"
            exit 1
        else
            log_error ".env.example not found!"
            exit 1
        fi
    fi
    
    log_success "Prerequisites check passed"
}

# Build images
build() {
    log_info "Building Docker images..."
    cd "$SCRIPT_DIR"
    docker-compose build --no-cache
    log_success "Build completed"
}

# Start services
start() {
    log_info "Starting services..."
    cd "$SCRIPT_DIR"
    docker-compose up -d
    log_success "Services started"
    
    log_info "Waiting for health checks..."
    sleep 10
    
    # Check service status
    if docker-compose ps | grep -q "healthy"; then
        log_success "All services are healthy!"
    else
        log_warn "Some services may still be starting. Check status with: ./deploy.sh status"
    fi
    
    show_urls
}

# Stop services
stop() {
    log_info "Stopping services..."
    cd "$SCRIPT_DIR"
    docker-compose down
    log_success "Services stopped"
}

# Restart services
restart() {
    log_info "Restarting services..."
    stop
    start
}

# View logs
logs() {
    cd "$SCRIPT_DIR"
    if [ -n "$1" ]; then
        docker-compose logs -f "$1"
    else
        docker-compose logs -f
    fi
}

# Check status
status() {
    cd "$SCRIPT_DIR"
    docker-compose ps
}

# Clean everything
clean() {
    log_warn "This will remove all containers, volumes, and images!"
    read -p "Are you sure? (y/N): " confirm
    if [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]]; then
        cd "$SCRIPT_DIR"
        docker-compose down -v --rmi all
        log_success "Cleanup completed"
    else
        log_info "Cleanup cancelled"
    fi
}

# Show application URLs
show_urls() {
    echo ""
    log_info "Application URLs:"
    echo "  - API:        http://localhost:8080"
    echo "  - Actuator:   http://localhost:8080/actuator/health"
    echo "  - OpenSearch: http://localhost:9200"
    echo "  - PostgreSQL: localhost:5432"
    echo "  - Redis:      localhost:6379"
    echo ""
}

# Backup data
backup() {
    log_info "Creating backup..."
    BACKUP_DIR="$SCRIPT_DIR/backups/$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    # Backup database
    docker exec car-selling-postgres pg_dump -U car_user carselling > "$BACKUP_DIR/database.sql"
    
    # Backup uploads
    docker cp car-selling-app:/app/uploads "$BACKUP_DIR/uploads"
    
    log_success "Backup created at: $BACKUP_DIR"
}

# Show help
show_help() {
    echo "Sell The Old Car - Deployment Script"
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  start     - Start all services"
    echo "  stop      - Stop all services"
    echo "  restart   - Restart all services"
    echo "  build     - Build Docker images"
    echo "  logs      - View logs (optional: service name)"
    echo "  status    - Check service status"
    echo "  clean     - Remove all containers, volumes, and images"
    echo "  backup    - Backup database and uploads"
    echo "  help      - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 start           # Start all services"
    echo "  $0 logs app        # View app logs"
    echo "  $0 logs postgres   # View database logs"
}

# Main
main() {
    case "${1:-help}" in
        start)
            check_prerequisites
            start
            ;;
        stop)
            stop
            ;;
        restart)
            restart
            ;;
        build)
            check_prerequisites
            build
            ;;
        logs)
            logs "$2"
            ;;
        status)
            status
            ;;
        clean)
            clean
            ;;
        backup)
            backup
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "Unknown command: $1"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
