#!/bin/bash
# ============================================================================
# Sell The Old Car - VPS Initial Setup Script
# Usage: curl -sSL <raw-url> | bash
# OR: wget -qO- <raw-url> | bash
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# 1. Update system packages
log_info "Updating system packages..."
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg git nano ufw

# 1.1 Configure Firewall (UFW)
log_info "Configuring firewall (UFW)..."
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
echo "y" | sudo ufw enable
log_success "Firewall configured and enabled. Ports 22, 80, 443, 8080 allowed."

# 2. Install Docker
log_info "Checking Docker installation..."
if ! command -v docker &> /dev/null; then
    log_info "Installing Docker..."
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
      "deb [arch=\"$(dpkg --print-architecture)\" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      \"$(. /etc/os-release && echo \"$VERSION_CODENAME\")\" stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    
    log_success "Docker installed successfully."
else
    log_success "Docker is already installed."
fi

# 3. Add user to docker group (optional but recommended to avoid sudo)
log_info "Configuring Docker permissions for the current user..."
if ! groups "$USER" | grep -q '\bdocker\b'; then
    sudo usermod -aG docker "$USER"
    log_warn "Added $USER to docker group. You may need to log out and log back in for this to take effect."
fi

# 4. Clone Repository
log_info "Setting up the project repository..."
REPO_URL="https://github.com/Yashborse4/OldCarBackend.git"
PROJECT_DIR="OldCarBackend"

if [ -d "$PROJECT_DIR" ]; then
    log_warn "Directory $PROJECT_DIR already exists. Pulling latest changes..."
    cd "$PROJECT_DIR"
    git pull origin main
else
    git clone "$REPO_URL" "$PROJECT_DIR"
    cd "$PROJECT_DIR"
    log_success "Repository cloned successfully."
fi

# 5. Environment configuration
log_info "Setting up environment configuration..."
if [ ! -f .env ]; then
    if [ -f .env.example ]; then
        cp .env.example .env
        log_warn "Created .env file. YOU MUST EDIT THIS FILE with actual production secrets before starting."
    else
        log_error ".env.example not found!"
    fi
else
    log_success ".env file already exists."
fi

# 6. Make deployment scripts executable
log_info "Setting script permissions..."
chmod +x deploy.sh
chmod +x gradlew || true

log_success "VPS Initial Setup complete!"
log_info "Next steps:"
log_info "1. Edit the .env file: nano .env"
log_info "2. Start the application: ./deploy.sh start"
