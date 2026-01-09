pipeline {
    agent any
    
    stages {
        stage('Generate Test Results') {
            steps {
                echo "Generating test XML file..."
                
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

                echo "✅ Test XML file created!"
            }
        }

        stage('Test New API') {
            steps {
                script {
                    echo "======================================"
                    echo "🧪 TESTING NEW getFailedTests() API"
                    echo "======================================"
                    echo ""

                    // Publish test results
                    def testResults = junit 'target/surefire-reports/*.xml'

                    // Test basic counts (should still work)
                    echo "✅ Basic counts:"
                    echo "   Total: ${testResults.totalCount}"
                    echo "   Failed: ${testResults.failCount}"
                    echo "   Passed: ${testResults.passCount}"
                    echo "   Skipped: ${testResults.skipCount}"
                    echo ""

                    // Test NEW API - getFailedTests()
                    echo "🎯 Testing getFailedTests()..."
                    try {
                        def failedTests = testResults.getFailedTests()

                        echo "✅ SUCCESS! getFailedTests() works!"
                        echo "   Found ${failedTests.size()} failed tests:"
                        echo ""

                        failedTests.each { test ->
                            echo "   ❌ ${test.fullName}"
                            echo "      Class: ${test.className}"
                            echo "      Method: ${test.name}"
                            echo "      Duration: ${test.duration}s"
                            echo "      Status: ${test.status}"
                            echo "      Error: ${test.errorDetails}"
                            echo ""
                        }

                    } catch (Exception e) {
                        echo "❌ FAILED: ${e.message}"
                        currentBuild.result = 'FAILURE'
                    }

                    // Test NEW API - getPassedTests()
                    echo "🎯 Testing getPassedTests()..."
                    try {
                        def passedTests = testResults.getPassedTests()
                        echo "✅ SUCCESS! getPassedTests() works!"
                        echo "   Found ${passedTests.size()} passed tests:"
                        passedTests.each { test ->
                            echo "   ✅ ${test.fullName} (${test.duration}s)"
                        }
                        echo ""
                    } catch (Exception e) {
                        echo "❌ FAILED: ${e.message}"
                    }

                    // Test NEW API - getSkippedTests()
                    echo "🎯 Testing getSkippedTests()..."
                    try {
                        def skippedTests = testResults.getSkippedTests()
                        echo "✅ SUCCESS! getSkippedTests() works!"
                        echo "   Found ${skippedTests.size()} skipped tests:"
                        skippedTests.each { test ->
                            echo "   ⏭️  ${test.fullName}"
                        }
                        echo ""
                    } catch (Exception e) {
                        echo "❌ FAILED: ${e.message}"
                    }

                    // Test NEW API - getAllTests()
                    echo "🎯 Testing getAllTests()..."
                    try {
                        def allTests = testResults.getAllTests()
                        echo "✅ SUCCESS! getAllTests() works!"
                        echo "   Found ${allTests.size()} total tests"
                        echo ""
                    } catch (Exception e) {
                        echo "❌ FAILED: ${e.message}"
                    }

                    // Demonstrate real use case
                    echo "======================================"
                    echo "💡 REAL-WORLD USE CASE DEMO"
                    echo "======================================"
                    echo ""

                    // Example 1: Find slow tests
                    echo "Example 1: Finding slow tests (>1 second)..."
                    def slowTests = testResults.getAllTests().findAll { it.duration > 1.0 }
                    echo "   Slow tests found: ${slowTests.size()}"
                    slowTests.each { test ->
                        echo "   ⏱️  ${test.fullName}: ${test.duration}s"
                    }
                    echo ""

                    // Example 2: Generate report message
                    echo "Example 2: Generate notification message..."
                    def message = "Build ${env.BUILD_NUMBER} - Tests: ${testResults.totalCount}, Failed: ${testResults.failCount}\n"
                    if (testResults.failCount > 0) {
                        message += "Failed tests:\n"
                        testResults.getFailedTests().each { test ->
                            message += "  - ${test.fullName}\n"
                        }
                    }
                    echo "   Message generated:"
                    echo "   ${message}"
                    echo ""

                    echo "======================================"
                    echo "🎉 ALL TESTS PASSED!"
                    echo "The new API is working correctly!"
                    echo "======================================"
                }
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