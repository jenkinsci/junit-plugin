#!/bin/bash
# Test Custom UI Provider Feature
# This script builds the plugin, starts Jenkins, and helps verify the custom UI

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
JENKINS_URL="http://localhost:8080/jenkins"
JENKINS_PORT=8080
JOB_NAME="junit-custom-ui-test"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  JUnit Custom UI Testing Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to print colored messages
print_info() {
    echo -e "${BLUE}ℹ${NC}  $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC}  $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Function to check if port is in use
check_port() {
    if lsof -Pi :$JENKINS_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Function to wait for Jenkins to be ready
wait_for_jenkins() {
    print_info "Waiting for Jenkins to start..."
    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -s "$JENKINS_URL" > /dev/null 2>&1; then
            print_success "Jenkins is ready!"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    print_error "Jenkins failed to start within timeout"
    return 1
}

# Parse command line arguments
BUILD_ONLY=false
SKIP_BUILD=false
VERIFY_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build-only)
            BUILD_ONLY=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --verify-only)
            VERIFY_ONLY=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --build-only    Build the plugin but don't start Jenkins"
            echo "  --skip-build    Skip build and start Jenkins (assumes plugin is already built)"
            echo "  --verify-only   Only verify the custom UI endpoints (assumes Jenkins is running)"
            echo "  --help          Show this help message"
            echo ""
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Verify only mode
if [ "$VERIFY_ONLY" = true ]; then
    print_info "Verification mode - checking if Jenkins is running..."

    if ! check_port; then
        print_error "Jenkins is not running on port $JENKINS_PORT"
        print_info "Start Jenkins first with: mvn hpi:run"
        exit 1
    fi

    print_success "Jenkins is running"
    print_info "Verifying custom UI setup..."

    # Check if job exists
    if curl -s -o /dev/null -w "%{http_code}" "$JENKINS_URL/job/$JOB_NAME/api/json" | grep -q "200"; then
        print_success "Test job '$JOB_NAME' exists"
    else
        print_warning "Test job '$JOB_NAME' not found"
        print_info "Create a job with JUnit test results first"
    fi

    echo ""
    echo -e "${GREEN}📍 Custom UI URLs:${NC}"
    echo ""
    echo "  Job Level (latest build):"
    echo -e "  ${BLUE}$JENKINS_URL/job/$JOB_NAME/test/renderCustomUI${NC}"
    echo ""
    echo "  Specific Build:"
    echo -e "  ${BLUE}$JENKINS_URL/job/$JOB_NAME/1/testReport/renderCustomUI${NC}"
    echo ""
    echo "  Latest Build:"
    echo -e "  ${BLUE}$JENKINS_URL/job/$JOB_NAME/lastBuild/testReport/renderCustomUI${NC}"
    echo ""
    exit 0
fi

# Step 1: Build the plugin
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo -e "${YELLOW}Step 1: Building Plugin${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    print_info "Running Maven build..."

    if mvn clean compile hpi:hpi -DskipTests; then
        print_success "Plugin built successfully"

        # Verify HPI was created
        if [ -f "target/junit.hpi" ]; then
            HPI_SIZE=$(ls -lh target/junit.hpi | awk '{print $5}')
            print_success "HPI created: target/junit.hpi ($HPI_SIZE)"
        else
            print_error "HPI file not found!"
            exit 1
        fi
    else
        print_error "Build failed!"
        exit 1
    fi
else
    print_info "Skipping build (--skip-build flag)"

    if [ ! -f "target/junit.hpi" ]; then
        print_error "No HPI found in target/. Build first!"
        exit 1
    fi
fi

# Exit if build-only mode
if [ "$BUILD_ONLY" = true ]; then
    echo ""
    print_success "Build complete! HPI ready at: target/junit.hpi"
    echo ""
    print_info "To start Jenkins with the plugin, run:"
    echo "  mvn hpi:run"
    echo ""
    exit 0
fi

# Step 2: Check if Jenkins is already running
echo ""
echo -e "${YELLOW}Step 2: Starting Jenkins${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if check_port; then
    print_warning "Jenkins is already running on port $JENKINS_PORT"
    read -p "Stop and restart Jenkins? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Please stop the existing Jenkins process (Ctrl+C in the terminal) and run this script again"
        exit 0
    else
        print_info "Using existing Jenkins instance"
    fi
else
    print_info "Starting Jenkins with mvn hpi:run..."
    print_info "This will run in the background. Press Ctrl+C in the Jenkins terminal to stop."
    echo ""
    print_warning "Starting Jenkins now. The script will open a new terminal window."
    print_info "Wait for Jenkins to fully start before testing..."
    echo ""

    # Detect OS and open new terminal
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        osascript <<EOF
tell application "Terminal"
    do script "cd $(pwd) && echo 'Starting Jenkins with Custom UI Plugin...' && mvn hpi:run"
    activate
end tell
EOF
        print_success "Jenkins starting in new Terminal window"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        if command -v gnome-terminal &> /dev/null; then
            gnome-terminal -- bash -c "cd $(pwd) && echo 'Starting Jenkins with Custom UI Plugin...' && mvn hpi:run; exec bash"
        elif command -v xterm &> /dev/null; then
            xterm -e "cd $(pwd) && echo 'Starting Jenkins with Custom UI Plugin...' && mvn hpi:run" &
        else
            print_warning "Could not detect terminal. Start Jenkins manually:"
            echo "  mvn hpi:run"
            exit 0
        fi
        print_success "Jenkins starting in new terminal window"
    else
        print_warning "Unknown OS. Start Jenkins manually:"
        echo "  mvn hpi:run"
        exit 0
    fi

    # Wait for Jenkins to be ready
    sleep 5
    if ! wait_for_jenkins; then
        print_error "Jenkins startup verification failed"
        exit 1
    fi
fi

# Step 3: Setup instructions
echo ""
echo -e "${YELLOW}Step 3: Setup & Testing${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

print_info "Jenkins is ready at: $JENKINS_URL"
echo ""

echo -e "${GREEN}📋 Setup Instructions:${NC}"
echo ""
echo "  1. Configure Custom UI Provider:"
echo -e "     ${BLUE}$JENKINS_URL/manage/configure${NC}"
echo "     → Scroll to 'JUnit Test Results'"
echo "     → Select 'Simple HTML UI (Test Example)'"
echo "     → Click 'Save'"
echo ""

echo "  2. Create a test job:"
echo "     • Create a Freestyle project: '$JOB_NAME'"
echo "     • Add build step to generate test results"
echo "     • Add post-build action: 'Publish JUnit test result report'"
echo "     • Configure test results pattern (e.g., **/target/surefire-reports/*.xml)"
echo "     • Run the build"
echo ""

echo "  3. View Custom UI:"
echo ""
echo "     Job Level (latest):"
echo -e "     ${BLUE}$JENKINS_URL/job/$JOB_NAME/test/renderCustomUI${NC}"
echo ""
echo "     Specific Build:"
echo -e "     ${BLUE}$JENKINS_URL/job/$JOB_NAME/1/testReport/renderCustomUI${NC}"
echo ""
echo "     Latest Build:"
echo -e "     ${BLUE}$JENKINS_URL/job/$JOB_NAME/lastBuild/testReport/renderCustomUI${NC}"
echo ""

echo -e "${GREEN}📦 Sample Test Results:${NC}"
echo ""
echo "  You can use the sample test results in test-data/ for testing"
echo "  Or generate your own with a Maven/Gradle project"
echo ""

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✓ Setup complete!${NC}"
echo ""
print_info "Jenkins is running. Stop it with Ctrl+C in the Jenkins terminal."
echo ""

# Keep script running to show instructions
read -p "Press Enter to exit this script (Jenkins will keep running)..."
