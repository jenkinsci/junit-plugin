package io.jenkins.plugins.junit.checks;

import hudson.FilePath;
import hudson.model.Result;
import hudson.tasks.junit.TestResultTest;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksStatus;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static java.util.Objects.requireNonNull;
import static org.mockito.ArgumentMatchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.crypto.*", "javax.security.*", "javax.net.ssl.*"})
@PrepareForTest({ChecksDetails.class, ChecksOutput.class})
public class JUnitChecksPublisherTest {

    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @Before
    public void setupChecksDetails() throws Exception {
        whenNew(ChecksDetails.class).withAnyArguments().thenReturn(mock(ChecksDetails.class));
        whenNew(ChecksOutput.class).withAnyArguments().thenReturn(mock(ChecksOutput.class));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void extractChecksDetailsPassingTestResults() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junit(testResults: '*.xml')\n" +
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        verifyNew(ChecksDetails.class).withArguments(
                eq("first"),
                eq(ChecksStatus.COMPLETED),
                anyString(),
                isNull(),
                eq(ChecksConclusion.SUCCESS),
                isNull(),
                any(ChecksOutput.class),
                anyList(),
                isNull()
        );

        verifyNew(ChecksOutput.class).withArguments(
                eq("passed: 6"),
                anyString(),
                eq(""),
                anyList(),
                anyList(),
                isNull()
        );

    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void extractChecksDetailsFailingSingleTestResult() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junit(testResults: '*.xml')\n" +
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-errror-details.xml"));

        rule.buildAndAssertStatus(Result.FAILURE, j);

        verifyNew(ChecksDetails.class).withArguments(
                eq("first"),
                eq(ChecksStatus.COMPLETED),
                anyString(),
                isNull(),
                eq(ChecksConclusion.FAILURE),
                isNull(),
                any(ChecksOutput.class),
                anyList(),
                isNull()
        );

        verifyNew(ChecksOutput.class).withArguments(
                eq("some.package.somewhere.WhooHoo.testHudsonReporting failed"),
                anyString(),
                anyString(),
                anyList(),
                anyList(),
                isNull()
        );

    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void extractChecksDetailsFailingMultipleTests() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junit(testResults: '*.xml')\n" +
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-20090516.xml"));

        rule.buildAndAssertStatus(Result.FAILURE, j);

        verifyNew(ChecksDetails.class).withArguments(
                eq("first"),
                eq(ChecksStatus.COMPLETED),
                anyString(),
                isNull(),
                eq(ChecksConclusion.FAILURE),
                isNull(),
                any(ChecksOutput.class),
                anyList(),
                isNull()
        );

        verifyNew(ChecksOutput.class).withArguments(
                eq("failed: 3, passed: 5"),
                anyString(),
                anyString(),
                anyList(),
                anyList(),
                isNull()
        );

    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void setCustomCheckName() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junit(testResults: '*.xml', checksName: 'Custom Checks Name')\n" +
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        verifyNew(ChecksDetails.class).withArguments(
                eq("Custom Checks Name"),
                eq(ChecksStatus.COMPLETED),
                anyString(),
                isNull(),
                eq(ChecksConclusion.SUCCESS),
                isNull(),
                any(ChecksOutput.class),
                anyList(),
                isNull()
        );
    }
}
