# Testing Custom UI Feature

This guide explains how to test the Custom UI Provider feature.

## Quick Start

Use the provided test script for automated setup:

```bash
# Build and start Jenkins
./test-custom-ui.sh

# Or build only
./test-custom-ui.sh --build-only

# Or verify existing setup
./test-custom-ui.sh --verify-only
```

## Manual Testing

### 1. Build the Plugin

```bash
mvn clean compile hpi:hpi -DskipTests
```

### 2. Start Jenkins

```bash
mvn hpi:run
```

Jenkins will start at: `http://localhost:8080/jenkins`

### 3. Configure Custom UI

1. Go to **Manage Jenkins** → **System**
2. Scroll to **"JUnit Test Results"** section
3. Select **"Simple HTML UI (Test Example)"** from dropdown
4. Click **Save**

### 4. Create Test Job

#### Option A: Use Sample Data

Create a Freestyle job and add this build step (Shell):

```bash
mkdir -p target/surefire-reports
cat > target/surefire-reports/TEST-sample.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="SampleTestSuite" tests="10" failures="2" errors="1" skipped="1" time="5.432">
  <testcase classname="com.example.CalculatorTest" name="testAddition" time="0.123"/>
  <testcase classname="com.example.CalculatorTest" name="testSubtraction" time="0.098"/>
  <testcase classname="com.example.CalculatorTest" name="testMultiplication" time="0.156">
    <failure message="Expected 20 but got 21" type="java.lang.AssertionError">
java.lang.AssertionError: Expected 20 but got 21
    at com.example.CalculatorTest.testMultiplication(CalculatorTest.java:45)
    </failure>
  </testcase>
  <testcase classname="com.example.StringUtilsTest" name="testConcatenation" time="0.089"/>
  <testcase classname="com.example.StringUtilsTest" name="testNullPointer" time="0.234">
    <error message="NullPointerException" type="java.lang.NullPointerException">
java.lang.NullPointerException
    at com.example.StringUtilsTest.testNullPointer(StringUtilsTest.java:23)
    </error>
  </testcase>
  <testcase classname="com.example.ListTest" name="testAddElement" time="0.067"/>
  <testcase classname="com.example.ListTest" name="testRemoveElement" time="0.145"/>
  <testcase classname="com.example.ListTest" name="testSkippedTest" time="0.000">
    <skipped message="Test skipped due to known issue"/>
  </testcase>
  <testcase classname="com.example.MapTest" name="testPutValue" time="0.178"/>
  <testcase classname="com.example.MapTest" name="testGetValue" time="0.089">
    <failure message="Expected 'value' but got 'wrong'" type="java.lang.AssertionError">
java.lang.AssertionError: Expected 'value' but got 'wrong'
    at com.example.MapTest.testGetValue(MapTest.java:67)
    </failure>
  </testcase>
</testsuite>
EOF
```

#### Option B: Use Real Maven Project

```bash
mvn test
```

Add post-build action: **Publish JUnit test result report**
- Test report XMLs: `target/surefire-reports/*.xml` or `**/target/surefire-reports/*.xml`

### 5. View Custom UI

After running the build, access the custom UI:

**Job Level (Latest Build):**
```
http://localhost:8080/jenkins/job/YOUR-JOB/test/renderCustomUI
```

**Specific Build:**
```
http://localhost:8080/jenkins/job/YOUR-JOB/1/testReport/renderCustomUI
```

**Latest Build:**
```
http://localhost:8080/jenkins/job/YOUR-JOB/lastBuild/testReport/renderCustomUI
```

## Expected Result

You should see a beautiful custom UI with:
- 🎨 Purple gradient background
- 📊 Test statistics cards (Total, Passed, Failed, Skipped)
- ℹ️ Test details section
- ✨ Smooth hover effects

## Troubleshooting

### Custom UI Not Showing

1. **Check Configuration:**
   ```bash
   ./test-custom-ui.sh --verify-only
   ```

2. **Verify Extension Loaded:**
   - Go to **Manage Jenkins** → **System Information**
   - Search for "SimpleHTMLUIProvider"
   - Should appear in the extensions list

3. **Check Logs:**
   Look for errors in Jenkins console output

### Provider Not in Dropdown

1. Restart Jenkins
2. Check if extension was indexed during build:
   ```bash
   mvn clean compile -DskipTests 2>&1 | grep "SimpleHTMLUIProvider indexed"
   ```

### 404 Error

Make sure you're using the correct URL:
- ❌ Wrong: `/job/NAME/renderCustomUI`
- ✅ Correct: `/job/NAME/lastBuild/testReport/renderCustomUI`

## Testing Different Scenarios

### Test With No Failures

Create a job that always passes:

```bash
mkdir -p target/surefire-reports
cat > target/surefire-reports/TEST-pass.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="PassingTests" tests="5" failures="0" errors="0" skipped="0" time="0.5">
  <testcase classname="Test" name="test1" time="0.1"/>
  <testcase classname="Test" name="test2" time="0.1"/>
  <testcase classname="Test" name="test3" time="0.1"/>
  <testcase classname="Test" name="test4" time="0.1"/>
  <testcase classname="Test" name="test5" time="0.1"/>
</testsuite>
EOF
```

### Test With Multiple Suites

Publish multiple XML files to see aggregated results.

### Test Provider Fallback

1. Select a custom UI provider
2. Save
3. Remove/disable the provider plugin
4. Verify it falls back to default UI

## Script Options

```bash
# Build only (no Jenkins)
./test-custom-ui.sh --build-only

# Skip build, just start Jenkins
./test-custom-ui.sh --skip-build

# Verify setup only
./test-custom-ui.sh --verify-only

# Full workflow (default)
./test-custom-ui.sh
```

## Automated Testing

For CI/CD pipelines:

```bash
# Build
./test-custom-ui.sh --build-only

# Deploy to test Jenkins
# (your deployment steps here)

# Verify
./test-custom-ui.sh --verify-only
```

## Sample Test Data

The `test-data/` directory contains sample JUnit XML files:
- `sample-junit-results.xml` - Mixed results (pass/fail/error/skip)

## Further Information

See [CUSTOM_UI_PROVIDER.md](CUSTOM_UI_PROVIDER.md) for:
- Implementation guide
- API reference
- Creating custom providers
- Architecture details
