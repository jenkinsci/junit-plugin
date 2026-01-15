package hudson.tasks.junit;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class TestResultSummaryTest {

    @Test
    void testGetFailedTestsInPipeline(JenkinsRule jenkins) throws Exception {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class, "test-project");

        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  def xml = '''<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<testsuite name=\"MyTestSuite\" tests=\"3\" failures=\"1\" skipped=\"1\">\n" +
                        "  <testcase classname=\"com.example.TestClass\" name=\"testPass\" time=\"1.5\"/>\n" +
                        "  <testcase classname=\"com.example.TestClass\" name=\"testFail\" time=\"0.5\">\n" +
                        "    <failure message=\"Expected true but was false\"/>\n" +
                        "  </testcase>\n" +
                        "  <testcase classname=\"com.example.TestClass\" name=\"testSkip\" time=\"0.0\">\n" +
                        "    <skipped/>\n" +
                        "  </testcase>\n" +
                        "</testsuite>'''\n" +
                        "  writeFile file: 'test-results.xml', text: xml\n" +
                        "  def summary = junit 'test-results.xml'\n" +
                        "  \n" +
                        "  def failedTests = summary.getFailedTests()\n" +
                        "  echo \"Failed tests count: ${failedTests.size()}\"\n" +
                        "  assert failedTests.size() == 1\n" +
                        "  echo \"Failed test name: ${failedTests[0].name}\"\n" +
                        "  assert failedTests[0].name == 'testFail'\n" +
                        "  \n" +
                        "  def passedTests = summary.getPassedTests()\n" +
                        "  echo \"Passed tests count: ${passedTests.size()}\"\n" +
                        "  assert passedTests.size() == 1\n" +
                        "  assert passedTests[0].name == 'testPass'\n" +
                        "  \n" +
                        "  def skippedTests = summary.getSkippedTests()\n" +
                        "  echo \"Skipped tests count: ${skippedTests.size()}\"\n" +
                        "  assert skippedTests.size() == 1\n" +
                        "  assert skippedTests[0].name == 'testSkip'\n" +
                        "  \n" +
                        "  def allTests = summary.getAllTests()\n" +
                        "  echo \"All tests count: ${allTests.size()}\"\n" +
                        "  assert allTests.size() == 3\n" +
                        "  \n" +
                        "  echo \"Passed test duration: ${passedTests[0].duration}\"\n" +
                        "  assert passedTests[0].duration > 0\n" +
                        "}\n",
                false
        ));

        WorkflowRun build = jenkins.buildAndAssertStatus(Result.UNSTABLE, project);

        jenkins.assertLogContains("Failed tests count: 1", build);
        jenkins.assertLogContains("Failed test name: testFail", build);
        jenkins.assertLogContains("Passed tests count: 1", build);
        jenkins.assertLogContains("Skipped tests count: 1", build);
        jenkins.assertLogContains("All tests count: 3", build);
    }

    @Test
    void testGetFailedTestsWithNoFailures(JenkinsRule jenkins) throws Exception {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class, "test-no-failures");

        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  def xml = '''<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<testsuite name=\"PassingSuite\" tests=\"1\" failures=\"0\" skipped=\"0\">\n" +
                        "  <testcase classname=\"com.example.Test\" name=\"testPass\" time=\"1.0\"/>\n" +
                        "</testsuite>'''\n" +
                        "  writeFile file: 'test-results.xml', text: xml\n" +
                        "  def summary = junit 'test-results.xml'\n" +
                        "  \n" +
                        "  def failedTests = summary.getFailedTests()\n" +
                        "  assert failedTests.size() == 0\n" +
                        "  echo 'No failed tests found correctly'\n" +
                        "}\n",
                false
        ));

        WorkflowRun build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains("No failed tests found correctly", build);
    }

    @Test
    void testAccessTestDetails(JenkinsRule jenkins) throws Exception {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class, "test-details");

        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  def xml = '''<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<testsuite name=\"DetailedTests\" tests=\"1\" failures=\"1\">\n" +
                        "  <testcase classname=\"com.example.DetailTest\" name=\"testWithError\" time=\"2.5\">\n" +
                        "    <failure message=\"Assertion failed\">Stack trace here</failure>\n" +
                        "  </testcase>\n" +
                        "</testsuite>'''\n" +
                        "  writeFile file: 'test-results.xml', text: xml\n" +
                        "  def summary = junit 'test-results.xml'\n" +
                        "  def failed = summary.getFailedTests()[0]\n" +
                        "  \n" +
                        "  echo \"Class: ${failed.className}\"\n" +
                        "  echo \"Name: ${failed.name}\"\n" +
                        "  echo \"Duration: ${failed.duration}\"\n" +
                        "  echo \"Error message: ${failed.errorDetails}\"\n" +
                        "  \n" +
                        "  assert failed.className == 'com.example.DetailTest'\n" +
                        "  assert failed.name == 'testWithError'\n" +
                        "  assert failed.duration > 2.0\n" +
                        "}\n",
                false
        ));

        WorkflowRun build = jenkins.buildAndAssertStatus(Result.UNSTABLE, project);
        jenkins.assertLogContains("Class: com.example.DetailTest", build);
        jenkins.assertLogContains("Name: testWithError", build);
        jenkins.assertLogContains("Duration: 2.5", build);
    }
}