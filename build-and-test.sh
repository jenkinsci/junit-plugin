#!/bin/bash
# Build and test JUnit plugin with GlobalConfiguration
# This script fixes maven-hpi-plugin annotation bug and optionally deploys to Docker Jenkins

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  JUnit Plugin Build & Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Parse arguments
DEPLOY_TO_DOCKER=false
DOCKER_CONTAINER="jenkins-test"

while [[ $# -gt 0 ]]; do
    case $1 in
        --deploy)
            DEPLOY_TO_DOCKER=true
            shift
            ;;
        --container)
            DOCKER_CONTAINER="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --deploy              Deploy to Docker Jenkins after build"
            echo "  --container NAME      Docker container name (default: jenkins-test)"
            echo "  --help                Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                    Build only"
            echo "  $0 --deploy           Build and deploy to jenkins-test"
            echo "  $0 --deploy --container my-jenkins"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Step 1: Clean and compile
echo -e "${YELLOW}📦 Step 1: Building plugin...${NC}"
mvn clean compile -DskipTests -q
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Compilation complete${NC}"
echo ""

# Step 2: Create JAR with annotations
echo -e "${YELLOW}🔧 Step 2: Creating JAR with annotations...${NC}"
cd target/classes
jar cf ../junit-fixed.jar .
cd ../..

if [ ! -f "target/junit-fixed.jar" ]; then
    echo -e "${RED}❌ Failed to create JAR${NC}"
    exit 1
fi
echo -e "${GREEN}✓ JAR created${NC}"
echo ""

# Step 3: Build HPI
echo -e "${YELLOW}📦 Step 3: Packaging HPI...${NC}"
mvn hpi:hpi -Dmaven.main.skip=true -Dmaven.test.skip=true -q
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ HPI packaging failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ HPI packaged${NC}"
echo ""

# Step 4: Fix HPI with correct annotations
echo -e "${YELLOW}✨ Step 4: Fixing annotation bug...${NC}"
rm -rf target/temp-hpi
mkdir -p target/temp-hpi
cd target/temp-hpi
unzip -q ../junit.hpi
cp ../junit-fixed.jar WEB-INF/lib/junit.jar
zip -qr ../junit-FIXED.hpi .
cd ../..

if [ ! -f "target/junit-FIXED.hpi" ]; then
    echo -e "${RED}❌ Failed to create fixed HPI${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Fixed HPI created${NC}"
echo ""

# Step 5: Verify annotations
echo -e "${YELLOW}🔍 Step 5: Verifying annotations...${NC}"
unzip -q target/junit-FIXED.hpi WEB-INF/lib/junit.jar -d target/verify 2>/dev/null || true
cd target/verify
jar xf WEB-INF/lib/junit.jar META-INF/annotations/hudson.Extension.txt 2>/dev/null
if [ ! -f "META-INF/annotations/hudson.Extension.txt" ]; then
    echo -e "${RED}❌ Annotations not found in HPI${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Found extensions:${NC}"
grep -E "CustomUI|JunitTestResultStorage" META-INF/annotations/hudson.Extension.txt | sed 's/^/  - /'
cd ../..
echo ""

# Clean up temp files
rm -rf target/temp-hpi target/verify target/junit-fixed.jar

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ Build successful!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}📦 Output: target/junit-FIXED.hpi${NC}"
SIZE=$(ls -lh target/junit-FIXED.hpi | awk '{print $5}')
echo -e "${BLUE}   Size: $SIZE${NC}"
echo ""

# Optional: Deploy to Docker
if [ "$DEPLOY_TO_DOCKER" = true ]; then
    echo -e "${YELLOW}🐳 Deploying to Docker Jenkins...${NC}"
    echo ""

    # Check if container exists and is running
    if ! docker ps | grep -q "$DOCKER_CONTAINER"; then
        if docker ps -a | grep -q "$DOCKER_CONTAINER"; then
            echo -e "${YELLOW}⚠️  Container exists but is stopped. Starting...${NC}"
            docker start "$DOCKER_CONTAINER"
            sleep 5
        else
            echo -e "${RED}❌ Docker container '$DOCKER_CONTAINER' not found${NC}"
            echo -e "${YELLOW}💡 Create it with: docker run -d --name $DOCKER_CONTAINER -p 8080:8080 jenkins/jenkins:lts${NC}"
            exit 1
        fi
    fi

    # Upload HPI
    echo -e "${YELLOW}📤 Uploading plugin...${NC}"
    docker cp target/junit-FIXED.hpi "$DOCKER_CONTAINER":/tmp/junit.hpi

    # Install plugin
    echo -e "${YELLOW}💾 Installing plugin...${NC}"
    docker exec "$DOCKER_CONTAINER" bash -c "rm -rf /var/jenkins_home/plugins/junit && mkdir -p /var/jenkins_home/plugins/junit"
    docker exec "$DOCKER_CONTAINER" bash -c "cp /tmp/junit.hpi /var/jenkins_home/plugins/junit.hpi && chown -R jenkins:jenkins /var/jenkins_home/plugins/"

    # Restart Jenkins
    echo -e "${YELLOW}🔄 Restarting Jenkins...${NC}"
    docker restart "$DOCKER_CONTAINER"

    # Wait for Jenkins
    echo -e "${YELLOW}⏳ Waiting for Jenkins to start...${NC}"
    for i in {1..60}; do
        if docker exec "$DOCKER_CONTAINER" curl -s http://localhost:8080 > /dev/null 2>&1; then
            echo ""
            echo -e "${GREEN}✅ Jenkins is ready!${NC}"
            break
        fi
        echo -n "."
        sleep 2
    done

    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}✅ Deployment complete!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${BLUE}🌐 Jenkins URL: http://localhost:8080${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo -e "  1. Open: ${BLUE}http://localhost:8080${NC}"
    echo -e "  2. Go to: ${BLUE}Manage Jenkins → System${NC}"
    echo -e "  3. Find: ${BLUE}JUnit Test Results${NC} section"
    echo -e "  4. Select a Custom UI Provider from the dropdown"
    echo ""
fi

echo -e "${YELLOW}📝 To deploy manually:${NC}"
echo -e "   docker cp target/junit-FIXED.hpi jenkins-test:/tmp/junit.hpi"
echo -e "   docker exec jenkins-test bash -c 'cp /tmp/junit.hpi /var/jenkins_home/plugins/junit.hpi'"
echo -e "   docker restart jenkins-test"
echo ""
