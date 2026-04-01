# Test Data

This directory contains sample JUnit test results for testing the Custom UI feature.

## Files

- `sample-junit-results.xml` - Sample JUnit XML test results with:
  - 10 total tests
  - 6 passed
  - 2 failures
  - 1 error
  - 1 skipped

## Usage

Use this sample file to test the Custom UI without needing to run actual tests:

1. Create a Jenkins job
2. Add a build step to copy this file:
   ```bash
   cp test-data/sample-junit-results.xml target/surefire-reports/TEST-sample.xml
   ```
3. Add "Publish JUnit test result report" post-build action
4. Set pattern: `**/target/surefire-reports/*.xml`
5. Run the build
6. View the custom UI

## Test Script

Use the provided test script for automated setup:

```bash
./test-custom-ui.sh
```

See the main script for available options.
