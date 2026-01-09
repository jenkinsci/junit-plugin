pipeline {
    agent any
    
    stages {
        stage('Checkout') {
            steps {
                script {
                    echo "========================================="
                    echo "Checking out branch: fix-720-add-getfailedtests-to-testresultsummary"
                    echo "========================================="
                }
                checkout scm
            }
        }
        
        stage('Generate Test Results') {
            steps {
                script {
                    echo ""
                    echo "========================================="
                    echo "Generating test results..."
                    echo "========================================="
                    echo "Current directory: ${pwd()}"
                    
                    // Check if we're on Windows or Linux
                    def isWindows = isUnix() ? false : true
                    
                    if (isWindows) {
                        // Windows commands
                        bat '''
                            @echo off
                            echo Creating directory...
                            if not exist target\\surefire-reports mkdir target\\surefire-reports
                            
                            echo Generating test XML file...
                            (
                            echo ^<?xml version="1.0" encoding="UTF-8"?^>
                            echo ^<testsuite name="com.example.PipelineTest" tests="5" failures="2" errors="0" skipped="1" time="3.5"^>
                            echo   ^<testcase name="testSuccess1" classname="com.example.PipelineTest" time="0.5"^>^</testcase^>
                            echo   ^<testcase name="testSuccess2" classname="com.example.PipelineTest" time="0.8"^>^</testcase^>
                            echo   ^<testcase name="testFailure1" classname="com.example.PipelineTest" time="1.2"^>
                            echo     ^<failure message="Expected 5 but was 3" type="AssertionError"^>AssertionError: Expected 5 but was 3 at com.example.PipelineTest.testFailure1^(PipelineTest.java:25^)^</failure^>
                            echo   ^</testcase^>
                            echo   ^<testcase name="testFailure2" classname="com.example.PipelineTest" time="0.7"^>
                            echo     ^<failure message="NullPointerException" type="NullPointerException"^>NullPointerException: Cannot invoke method at com.example.PipelineTest.testFailure2^(PipelineTest.java:42^)^</failure^>
                            echo   ^</testcase^>
                            echo   ^<testcase name="testSkipped" classname="com.example.PipelineTest" time="0.0"^>
                            echo     ^<skipped message="Test disabled"^>Test is disabled^</skipped^>
                            echo   ^</testcase^>
                            echo ^</testsuite^>
                            ) > target\\surefire-reports\\TEST-PipelineTest.xml
                            
                            echo Verifying file was created...
                            dir target\\surefire-reports
                        '''
                    } else {
                        // Linux/Unix commands
                        sh '''
                            echo "Creating directory..."
                            mkdir -p target/surefire-reports
                            
                            echo "Generating test XML file..."
                            cat > target/surefire-reports/TEST-PipelineTest.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
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
</testsuite>
EOF
                            
                            echo "Verifying file was created..."
                            ls -la target/surefire-reports/
                            cat target/surefire-reports/TEST-PipelineTest.xml
                        '''
                    }
                    
                    echo "✅ Test results generated successfully!"
                    echo "   Location: target/surefire-reports/TEST-PipelineTest.xml"
                }
            }
        }
        
        stage('Publish JUnit Results') {
            steps {
                script {
                    echo ""
                    echo "========================================="
                    echo "📊 Publishing JUnit test results..."
                    echo "========================================="
                    
                    // This publishes the test results and returns TestResultSummary
                    def testResults = junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                    
                    // ===== CURRENT BEHAVIOR (WORKS) =====
                    echo ""
                    echo "========================================="
                    echo "✅ Current TestResultSummary Information:"
                    echo "========================================="
                    echo "📝 Type: ${testResults.class.name}"
                    echo "📊 Total Tests: ${testResults.totalCount}"
                    echo "❌ Failed Tests: ${testResults.failCount}"
                    echo "✅ Passed Tests: ${testResults.passCount}"
                    echo "⏭️  Skipped Tests: ${testResults.skipCount}"
                    
                    // Check available methods
                    echo ""
                    echo "Available methods on TestResultSummary:"
                    testResults.class.methods.findAll { 
                        it.name.startsWith('get') && it.parameterTypes.length == 0 && !it.name.equals('getClass')
                    }.each { method ->
                        echo "  - ${method.name}() -> ${method.returnType.simpleName}"
                    }
                    
                    // ===== DESIRED BEHAVIOR (DOESN'T WORK YET) =====
                    echo ""
                    echo "========================================="
                    echo "❌ Trying to get failed test details..."
                    echo "========================================="
                    echo "Attempting: testResults.getFailedTests()"
                    echo ""
                    
                    try {
                        // This will FAIL because method doesn't exist!
                        def failedTests = testResults.getFailedTests()
                        
                        echo "✅ SUCCESS! Method getFailedTests() exists!"
                        echo "   Found ${failedTests.size()} failed tests:"
                        echo ""
                        
                        failedTests.each { test ->
                            echo "   📍 ${test.fullName}"
                            echo "      ⏱️  Duration: ${test.duration}s"
                            echo "      💥 Error: ${test.errorDetails}"
                            echo ""
                        }
                        
                        echo "🎉 The feature has been implemented!"
                        
                    } catch (groovy.lang.MissingMethodException e) {
                        echo "❌ ERROR: Method getFailedTests() does NOT exist!"
                        echo ""
                        echo "📋 Error Details:"
                        echo "   ${e.message}"
                        echo ""
                        echo "🔍 This confirms the ISSUE:"
                        echo "   TestResultSummary only provides counts (totalCount, failCount, etc.)"
                        echo "   but does NOT provide access to individual test results."
                        echo ""
                        echo "💡 What we need:"
                        echo "   Add getFailedTests() method to TestResultSummary"
                        echo "   so users can access detailed test information in pipelines."
                        
                    } catch (Exception e) {
                        echo "❌ Unexpected error: ${e.class.name}"
                        echo "   Message: ${e.message}"
                    }
                    
                    // ===== WORKAROUND (Complex but works) =====
                    echo ""
                    echo "========================================="
                    echo "⚙️  Using WORKAROUND to get test details..."
                    echo "========================================="
                    echo "This demonstrates that the data EXISTS,"
                    echo "but is hidden behind a complex workaround."
                    echo ""
                    
                    try {
                        // Complex workaround from StackOverflow
                        // https://stackoverflow.com/questions/39920437/
                        def testResultAction = currentBuild.rawBuild
                            .getAction(hudson.tasks.junit.TestResultAction.class)
                        
                        if (testResultAction != null) {
                            def result = testResultAction.getResult()
                            
                            echo "✅ Workaround successful!"
                            echo "   Accessed: TestResultAction -> TestResult"
                            echo ""
                            
                            // Get failed tests
                            def failedTests = result.getFailedTests()
                            echo "📊 Found ${failedTests.size()} failed tests:"
                            echo ""
                            
                            failedTests.each { test ->
                                echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                echo "❌ Failed Test:"
                                echo "   📦 Class: ${test.className}"
                                echo "   🔧 Method: ${test.name}"
                                echo "   📍 Full Name: ${test.fullName}"
                                echo "   ⏱️  Duration: ${test.duration}s"
                                echo "   📊 Status: ${test.status}"
                                if (test.errorDetails) {
                                    echo "   💥 Error: ${test.errorDetails.take(100)}..."
                                }
                                echo ""
                            }
                            
                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                            echo ""
                            echo "⚠️  PROBLEMS with this workaround:"
                            echo "   1. ❌ Too complex for simple use cases"
                            echo "   2. ❌ Uses internal API (currentBuild.rawBuild)"
                            echo "   3. ❌ Not officially documented"
                            echo "   4. ❌ May break in future Jenkins versions"
                            echo "   5. ❌ Requires deep Jenkins knowledge"
                            echo "   6. ❌ Not discoverable (users won't find it)"
                            echo ""
                            echo "✅ SOLUTION:"
                            echo "   Add getFailedTests() directly to TestResultSummary!"
                            echo "   This makes it simple, official, and maintainable."
                            
                        } else {
                            echo "❌ Could not find TestResultAction"
                            echo "   This shouldn't happen, but confirms the issue."
                        }
                        
                    } catch (Exception e) {
                        echo "❌ Even the workaround failed!"
                        echo "   Error: ${e.message}"
                        echo ""
                        echo "This further proves we need an official API!"
                    }
                }
            }
        }
        
        stage('Demonstrate Use Cases') {
            steps {
                script {
                    echo ""
                    echo "========================================="
                    echo "💡 REAL-WORLD USE CASES"
                    echo "========================================="
                    echo ""
                    echo "If getFailedTests() was available, users could:"
                    echo ""
                    echo "📢 1. SLACK NOTIFICATIONS"
                    echo "   def failedTests = testResults.getFailedTests()"
                    echo "   def message = 'Failed tests:\\n' + failedTests*.fullName.join('\\n')"
                    echo "   slackSend(message: message)"
                    echo ""
                    echo "🎫 2. JIRA TICKET CREATION"
                    echo "   failedTests.each { test ->"
                    echo "     jiraCreateIssue("
                    echo "       summary: \"Test failed: \${test.fullName}\","
                    echo "       description: test.errorDetails"
                    echo "     )"
                    echo "   }"
                    echo ""
                    echo "📧 3. EMAIL DETAILED REPORTS"
                    echo "   def report = failedTests.collect { test ->"
                    echo "     \"\${test.fullName}: \${test.errorDetails}\""
                    echo "   }.join('\\n')"
                    echo "   emailext(body: report, subject: 'Test Failures')"
                    echo ""
                    echo "🔍 4. CONDITIONAL LOGIC"
                    echo "   if (failedTests.any { it.className.contains('Critical') }) {"
                    echo "     // Trigger emergency deployment rollback"
                    echo "   }"
                    echo ""
                    echo "⏱️  5. PERFORMANCE MONITORING"
                    echo "   def slowTests = testResults.getAllTests()"
                    echo "     .findAll { it.duration > 5.0 }"
                    echo "   if (slowTests) {"
                    echo "     echo \"Slow tests found: \${slowTests*.fullName}\""
                    echo "   }"
                    echo ""
                    echo "📊 6. CUSTOM DASHBOARDS"
                    echo "   def metrics = ["
                    echo "     total: testResults.totalCount,"
                    echo "     failed: failedTests.collect { [name: it.fullName, time: it.duration] }"
                    echo "   ]"
                    echo "   writeJSON(file: 'metrics.json', json: metrics)"
                    echo ""
                    echo "🎯 7. FLAKY TEST DETECTION"
                    echo "   def flakyTests = failedTests.findAll { test ->"
                    echo "     test.age < 2 // Failed recently"
                    echo "   }"
                    echo ""
                    echo "========================================="
                }
            }
        }
        
        stage('Summary') {
            steps {
                script {
                    echo ""
                    echo "╔════════════════════════════════════════╗"
                    echo "║         ISSUE REPRODUCTION             ║"
                    echo "╚════════════════════════════════════════╝"
                    echo ""
                    echo "✅ Successfully demonstrated the problem:"
                    echo ""
                    echo "   Current State:"
                    echo "   • TestResultSummary only provides counts"
                    echo "   • getFailedTests() method does NOT exist"
                    echo "   • Users must use complex workarounds"
                    echo ""
                    echo "   Desired State:"
                    echo "   • Add getFailedTests() to TestResultSummary"
                    echo "   • Add getAllTests() for all test access"
                    echo "   • Add getPassedTests() for completeness"
                    echo "   • Simple, discoverable, official API"
                    echo ""
                    echo "   Next Steps:"
                    echo "   • Modify TestResultSummary.java"
                    echo "   • Add new methods with proper serialization"
                    echo "   • Update documentation"
                    echo "   • Add tests"
                    echo "   • Submit PR"
                    echo ""
                    echo "╚════════════════════════════════════════╝"
                }
            }
        }
    }
    
    post {
        always {
            echo ""
            echo "========================================="
            echo "🏁 Build completed!"
            echo "========================================="
            
            // Archive the generated test results for inspection
            archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
        }
        unstable {
            echo "⚠️  Build unstable (tests failed - as expected)"
        }
        success {
            echo "✅ Build succeeded (but tests should have failed)"
        }
        failure {
            echo "❌ Build failed"
        }
    }
}