pipeline {
    agent any
    
    stages {
        stage('Generate Test Results') {
            steps {
                script {
                    echo "Generating test results..."
                    
                    // Create directory
                    bat 'if not exist target\\surefire-reports mkdir target\\surefire-reports'
                    
                    // Create test XML file inline
                    bat '''
                        @echo off
                        (
                        echo ^<?xml version="1.0" encoding="UTF-8"?^>
                        echo ^<testsuite name="com.example.PipelineTest" tests="5" failures="2" errors="0" skipped="1" time="3.5"^>
                        echo   ^<testcase name="testSuccess1" classname="com.example.PipelineTest" time="0.5"^>^</testcase^>
                        echo   ^<testcase name="testSuccess2" classname="com.example.PipelineTest" time="0.8"^>^</testcase^>
                        echo   ^<testcase name="testFailure1" classname="com.example.PipelineTest" time="1.2"^>
                        echo     ^<failure message="Expected 5 but was 3" type="AssertionError"^>
                        echo       AssertionError: Expected 5 but was 3
                        echo       at com.example.PipelineTest.testFailure1(PipelineTest.java:25)
                        echo     ^</failure^>
                        echo   ^</testcase^>
                        echo   ^<testcase name="testFailure2" classname="com.example.PipelineTest" time="0.7"^>
                        echo     ^<failure message="NullPointerException" type="NullPointerException"^>
                        echo       NullPointerException: Cannot invoke method
                        echo       at com.example.PipelineTest.testFailure2(PipelineTest.java:42)
                        echo     ^</failure^>
                        echo   ^</testcase^>
                        echo   ^<testcase name="testSkipped" classname="com.example.PipelineTest" time="0.0"^>
                        echo     ^<skipped message="Test disabled"^>Test is disabled^</skipped^>
                        echo   ^</testcase^>
                        echo ^</testsuite^>
                        ) > target\\surefire-reports\\TEST-PipelineTest.xml
                    '''
                    
                    echo "Test results generated successfully!"
                }
            }
        }
        
        stage('Publish JUnit Results') {
            steps {
                script {
                    echo "========================================="
                    echo "Publishing JUnit test results..."
                    echo "========================================="
                    
                    // This publishes the test results and returns TestResultSummary
                    def testResults = junit testResults: 'target/surefire-reports/*.xml'
                    
                    // ===== CURRENT BEHAVIOR (WORKS) =====
                    echo ""
                    echo "========================================="
                    echo "✅ Current TestResultSummary Information:"
                    echo "========================================="
                    echo "Total Tests: ${testResults.totalCount}"
                    echo "Failed Tests: ${testResults.failCount}"
                    echo "Passed Tests: ${testResults.passCount}"
                    echo "Skipped Tests: ${testResults.skipCount}"
                    echo "Class Name: ${testResults.class.name}"
                    
                    // ===== DESIRED BEHAVIOR (DOESN'T WORK YET) =====
                    echo ""
                    echo "========================================="
                    echo "❌ Trying to get failed test details..."
                    echo "========================================="
                    
                    try {
                        // This will FAIL because method doesn't exist!
                        def failedTests = testResults.getFailedTests()
                        echo "✅ SUCCESS! Failed tests: ${failedTests}"
                        
                        failedTests.each { test ->
                            echo "  - ${test.fullName}"
                            echo "    Duration: ${test.duration}s"
                        }
                    } catch (MissingMethodException e) {
                        echo "❌ ERROR: Method getFailedTests() does not exist!"
                        echo "Error: ${e.message}"
                        echo ""
                        echo "This is the ISSUE we need to fix!"
                        echo "TestResultSummary only provides counts, not test details."
                    } catch (Exception e) {
                        echo "❌ ERROR: ${e.message}"
                    }
                    
                    // ===== WORKAROUND (Complex but works) =====
                    echo ""
                    echo "========================================="
                    echo "⚙️  Using WORKAROUND to get test details..."
                    echo "========================================="
                    
                    try {
                        // Complex workaround from StackOverflow
                        def testResultAction = currentBuild.rawBuild.getAction(hudson.tasks.junit.TestResultAction.class)
                        
                        if (testResultAction != null) {
                            def result = testResultAction.getResult()
                            def failedTests = result.getFailedTests()
                            
                            echo "✅ Found ${failedTests.size()} failed tests using workaround:"
                            echo ""
                            
                            failedTests.each { test ->
                                echo "Failed Test:"
                                echo "  Class: ${test.className}"
                                echo "  Method: ${test.name}"
                                echo "  Full Name: ${test.fullName}"
                                echo "  Duration: ${test.duration}s"
                                echo "  Status: ${test.status}"
                                echo "  Error: ${test.errorDetails}"
                                echo "  ---"
                            }
                            
                            echo ""
                            echo "⚠️  This workaround is:"
                            echo "  - Too complex"
                            echo "  - Uses internal APIs (rawBuild)"
                            echo "  - Not officially supported"
                            echo "  - May break in future versions"
                            echo ""
                            echo "��� We should add getFailedTests() to TestResultSummary!"
                        } else {
                            echo "❌ Could not find TestResultAction"
                        }
                    } catch (Exception e) {
                        echo "❌ Workaround also failed: ${e.message}"
                        e.printStackTrace()
                    }
                }
            }
        }
        
        stage('Demonstrate Use Case') {
            steps {
                script {
                    echo ""
                    echo "========================================="
                    echo "��� EXAMPLE USE CASE"
                    echo "========================================="
                    echo "If getFailedTests() was available, we could:"
                    echo ""
                    echo "1. Send Slack notification with failed test names"
                    echo "2. Create Jira tickets for each failure"
                    echo "3. Email detailed failure report to team"
                    echo "4. Trigger different actions based on which tests failed"
                    echo "5. Find slow tests (duration > 5s)"
                    echo "6. Custom dashboards and metrics"
                    echo ""
                    echo "Example desired code:"
                    echo "  def failedTests = testResults.getFailedTests()"
                    echo "  failedTests.each { test ->"
                    echo "    slackSend(message: \"Test failed: \${test.fullName}\")"
                    echo "  }"
                }
            }
        }
    }
    
    post {
        always {
            echo ""
            echo "========================================="
            echo "Build completed!"
            echo "========================================="
        }
        failure {
            echo "❌ Build failed"
        }
        unstable {
            echo "⚠️  Build unstable (tests failed)"
        }
        success {
            echo "✅ Build succeeded"
        }
    }
}
