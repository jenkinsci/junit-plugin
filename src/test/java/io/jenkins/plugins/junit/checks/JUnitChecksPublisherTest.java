package io.jenkins.plugins.junit.checks;

import hudson.FilePath;
import hudson.model.Result;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultSummary;
import hudson.tasks.junit.TestResultTest;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

public class JUnitChecksPublisherTest {

    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void extractChecksDetailsPassingTestResults() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junit(testResults: '*.xml')\n" + // node id 7
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResultSummary summary = new TestResultSummary(0, 0, 6, 6);
        JUnitChecksPublisher publisher = new JUnitChecksPublisher(action, summary);
        ChecksDetails checksDetails = publisher.extractChecksDetails();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.SUCCESS));
        assertThat(checksDetails.getName().get(), is("Tests"));

        ChecksOutput output = checksDetails.getOutput().get();

        assertThat(output.getTitle().get(), is("passed: 6"));
        assertThat(output.getText().get(), is(""));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void extractChecksDetailsFailingSingleTestResult() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junit(testResults: '*.xml')\n" + // node id 7
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-errror-details.xml"));

        WorkflowRun r = rule.buildAndAssertStatus(Result.FAILURE, j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResultSummary summary = new TestResultSummary(1, 0, 1, 2);
        JUnitChecksPublisher publisher = new JUnitChecksPublisher(action, summary);
        ChecksDetails checksDetails = publisher.extractChecksDetails();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.FAILURE));

        ChecksOutput output = checksDetails.getOutput().get();

        assertThat(output.getTitle().get(), is("some.package.somewhere.WhooHoo.testHudsonReporting failed"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void extractChecksDetailsFailingMultipleTests() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junit(testResults: '*.xml')\n" + // node id 7
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-20090516.xml"));

        WorkflowRun r = rule.buildAndAssertStatus(Result.FAILURE, j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResultSummary summary = new TestResultSummary(3, 0, 5, 8);
        JUnitChecksPublisher publisher = new JUnitChecksPublisher(action, summary);
        ChecksDetails checksDetails = publisher.extractChecksDetails();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.FAILURE));

        ChecksOutput output = checksDetails.getOutput().get();

        assertThat(output.getTitle().get(), is("failed: 3, passed: 5"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void setCustomCheckName() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
              "  node {\n" +
              "    def results = junit(testResults: '*.xml', checksName: 'Custom Checks Name')\n" + // node id 7
              "    assert results.totalCount == 6\n" +
              "  }\n" +
              "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResultSummary summary = new TestResultSummary(0, 0, 6, 6);
        JUnitChecksPublisher publisher = new JUnitChecksPublisher(action, summary);
        ChecksDetails checksDetails = publisher.extractChecksDetails();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.SUCCESS));

        assertThat(checksDetails.getName().get(), is("Custom Checks Name"));

    }
}
