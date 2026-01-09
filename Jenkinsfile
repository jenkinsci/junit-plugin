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
        
        stage('Publish and Access Test Results') {
            steps {
                script {
                    echo "======================================"
                    echo "Publishing JUnit test results..."
                    echo "======================================"
                    echo ""
                    
                    // Publish the test results - this returns TestResultSummary
                    def testResults = junit 'target/surefire-reports/*.xml'
                    
                    echo "======================================"
                    echo "✅ WHAT WORKS (Current API):"
                    echo "======================================"
                    echo ""
                    echo "testResults.totalCount = ${testResults.totalCount}"
                    echo "testResults.failCount = ${testResults.failCount}"
                    echo "testResults.passCount = ${testResults.passCount}"
                    echo "testResults.skipCount = ${testResults.skipCount}"
                    echo ""
                    echo "✅ These basic counts work fine!"
                    echo "   But that's ALL you can access..."
                    echo ""
                    
                    // Now try to access detailed test information
                    echo "======================================"
                    echo "❌ WHAT DOESN'T WORK (Missing API):"
                    echo "======================================"
                    echo ""
                    echo "Attempting: def failedTests = testResults.getFailedTests()"
                    echo ""
                    
                    try {
                        // ❌ THIS WILL FAIL - Method doesn't exist!
                        def failedTests = testResults.getFailedTests()
                        
                        // If it worked, we could do:
                        echo "✅ SUCCESS! Found ${failedTests.size()} failed tests:"
                        failedTests.each { test ->
                            echo "  - ${test.fullName}"
                            echo "    Duration: ${test.duration}s"
                            echo "    Error: ${test.errorDetails}"
                        }
                        
                    } catch (groovy.lang.MissingMethodException e) {
                        echo "❌ ERROR: ${e.message}"
                        echo ""
                        echo "The method getFailedTests() does NOT exist on TestResultSummary!"
                        echo ""
                    }
                    
                    echo "======================================"
                    echo "💡 WHAT USERS WANT TO DO:"
                    echo "======================================"
                    echo ""
                    echo "Example 1: Send Slack notification with test names"
                    echo "---------------------------------------------------"
                    echo "  def failedTests = testResults.getFailedTests()"
                    echo "  def message = 'Tests failed:\\n'"
                    echo "  failedTests.each { test ->"
                    echo "    message += \"- \${test.fullName}\\n\""
                    echo "  }"
                    echo "  slackSend(channel: '#dev', message: message)"
                    echo ""
                    
                    echo "Example 2: Create JIRA ticket for each failure"
                    echo "-----------------------------------------------"
                    echo "  failedTests.each { test ->"
                    echo "    jiraCreateIssue("
                    echo "      project: 'PROJ',"
                    echo "      summary: \"Test failed: \${test.fullName}\","
                    echo "      description: test.errorDetails"
                    echo "    )"
                    echo "  }"
                    echo ""
                    
                    echo "Example 3: Find slow tests"
                    echo "--------------------------"
                    echo "  def allTests = testResults.getAllTests()"
                    echo "  def slowTests = allTests.findAll { it.duration > 5.0 }"
                    echo "  if (slowTests) {"
                    echo "    echo \"Slow tests: \${slowTests*.fullName}\""
                    echo "  }"
                    echo ""
                    
                    echo "Example 4: Conditional deployment based on failures"
                    echo "----------------------------------------------------"
                    echo "  def criticalFailed = failedTests.any { "
                    echo "    it.className.contains('Critical')"
                    echo "  }"
                    echo "  if (criticalFailed) {"
                    echo "    // Rollback deployment"
                    echo "    sh 'kubectl rollout undo deployment/myapp'"
                    echo "  }"
                    echo ""
                    
                    echo "======================================"
                    echo "⚙️  CURRENT WORKAROUND (Complex):"
                    echo "======================================"
                    echo ""
                    echo "Users must do this complicated approach:"
                    echo "  def action = currentBuild.rawBuild"
                    echo "    .getAction(hudson.tasks.junit.TestResultAction)"
                    echo "  def result = action.getResult()"
                    echo "  def failedTests = result.getFailedTests()"
                    echo ""
                    echo "Problems with workaround:"
                    echo "  ❌ Uses internal API (rawBuild)"
                    echo "  ❌ Not documented"
                    echo "  ❌ May break in future"
                    echo "  ❌ Too complex for simple use cases"
                    echo ""
                }
            }
        }
        
        stage('Summary') {
            steps {
                echo ""
                echo "╔════════════════════════════════════════╗"
                echo "║      ISSUE #720 DEMONSTRATION         ║"
                echo "╚════════════════════════════════════════╝"
                echo ""
                echo "PROBLEM:"
                echo "  TestResultSummary only provides counts"
                echo "  No way to access individual test details"
                echo ""
                echo "IMPACT:"
                echo "  Cannot automate notifications"
                echo "  Cannot create tickets for failures"
                echo "  Cannot make decisions based on which tests failed"
                echo "  Cannot find slow/flaky tests"
                echo ""
                echo "SOLUTION NEEDED:"
                echo "  Add these methods to TestResultSummary:"
                echo "  • getFailedTests() -> List<CaseResult>"
                echo "  • getAllTests() -> List<CaseResult>"
                echo "  • getPassedTests() -> List<CaseResult>"
                echo "  • getSkippedTests() -> List<CaseResult>"
                echo ""
                echo "This will enable simple, documented access"
                echo "to test details in pipeline code!"
                echo ""
                echo "╚════════════════════════════════════════╝"
            }
        }
    }
    
    post {
        always {
            echo ""
            echo "Build completed! Check the test results tab."
            archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
        }
        unstable {
            echo "Build is unstable (tests failed - as expected for demo)"
        }
    }
}