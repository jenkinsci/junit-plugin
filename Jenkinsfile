pipeline {
    agent any
    
    stages {
        stage('Generate Test Results') {
            steps {
                echo "Generating test XML file..."
                
                // Simple file creation - no complex scripting
                writeFile file: 'target/surefire-reports/TEST-PipelineTest.xml', text: '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.PipelineTest" tests="5" failures="2" errors="0" skipped="1" time="3.5">
  <testcase name="testSuccess1" classname="com.example.PipelineTest" time="0.5"></testcase>
  <testcase name="testSuccess2" classname="com.example.PipelineTest" time="0.8"></testcase>
  <testcase name="testFailure1" classname="com.example.PipelineTest" time="1.2">
    <failure message="Expected 5 but was 3" type="AssertionError">AssertionError: Expected 5 but was 3
    at com.example.PipelineTest.testFailure1(PipelineTest.java:25)</failure>
  </testcase>
  <testcase name="testFailure2" classname="com.example.PipelineTest" time="0.7">
    <failure message="NullPointerException" type="NullPointerException">NullPointerException: Cannot invoke method
    at com.example.PipelineTest.testFailure2(PipelineTest.java:42)</failure>
  </testcase>
  <testcase name="testSkipped" classname="com.example.PipelineTest" time="0.0">
    <skipped message="Test disabled">Test is disabled</skipped>
  </testcase>
</testsuite>'''
                
                echo "Test XML file created successfully!"
            }
        }
        
        stage('Publish JUnit Results') {
            steps {
                echo "Publishing JUnit test results..."
                
                // Publish the test results - this returns TestResultSummary
                junit 'target/surefire-reports/*.xml'
                
                echo "Test results published!"
                echo ""
                echo "======================================"
                echo "ISSUE DEMONSTRATION:"
                echo "======================================"
                echo "The junit step returns a TestResultSummary object."
                echo "This object ONLY provides:"
                echo "  - totalCount"
                echo "  - failCount"
                echo "  - passCount"
                echo "  - skipCount"
                echo ""
                echo "It does NOT provide:"
                echo "  - getFailedTests() - to get list of failed tests"
                echo "  - getAllTests() - to get all test details"
                echo "  - getPassedTests() - to get passed test details"
                echo ""
                echo "Users need these methods to:"
                echo "  1. Send Slack notifications with test names"
                echo "  2. Create JIRA tickets for failures"
                echo "  3. Send detailed email reports"
                echo "  4. Make decisions based on which tests failed"
                echo "  5. Find slow tests (duration > 5s)"
                echo "  6. Build custom dashboards"
                echo ""
                echo "Currently, users must use complex workarounds"
                echo "to access this information."
                echo "======================================"
            }
        }
    }
    
    post {
        always {
            echo "Build completed!"
            archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
        }
    }
}