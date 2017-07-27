package hudson.tasks.junit.pipeline;

import hudson.FilePath;
import hudson.model.Result;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultTest;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JUnitResultsStepTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @ClassRule
    public final static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void emptyFails() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "emptyFails");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    sh 'echo hi'\n" +
                "    junitResults('*.xml')\n" +
                "  }\n" +
                "}\n", true));

        WorkflowRun r = j.scheduleBuild2(0).waitForStart();
        rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
        rule.assertLogContains("ERROR: No test report files were found. Configuration error?", r);
    }

    @Test
    public void allowEmpty() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "allowEmpty");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    sh 'echo hi'\n" +
                "    def results = junitResults(testResults: '*.xml', allowEmptyResults: true)\n" +
                "    assert results.totalCount == 0\n" +
                "  }\n" +
                "}\n", true));

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        assertNull(r.getAction(TestResultAction.class));
        rule.assertLogContains("None of the test reports contained any result", r);
    }

    @Test
    public void singleStep() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def results = junitResults(testResults: '*.xml')\n" +
                "    assert results.totalCount == 6\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = ws.child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(1, action.getResult().getSuites().size());
        assertEquals(6, action.getTotalCount());

        FlowExecution execution = r.getExecution();

        FlowNode junitNode = execution.getNode("7");
        assertNotNull(junitNode);

        TestResult nodeTests = action.getResult().getResultByRunAndNode(r.getExternalizableId(), junitNode.getId());
        assertNotNull(nodeTests);
        assertEquals(1, nodeTests.getSuites().size());
        assertEquals(6, nodeTests.getTotalCount());
    }

    @Test
    public void twoSteps() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "twoSteps");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def first = junitResults(testResults: 'first-result.xml')\n" +
                "    def second = junitResults(testResults: 'second-result.xml')\n" +
                "    assert first.totalCount == 6\n" +
                "    assert second.totalCount == 1\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = ws.child("first-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));
        FilePath secondTestFile = ws.child("second-result.xml");
        secondTestFile.copyFrom(TestResultTest.class.getResource("junit-report-2874.xml"));

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(2, action.getResult().getSuites().size());
        assertEquals(7, action.getTotalCount());

        FlowExecution execution = r.getExecution();

        FlowNode firstNode = execution.getNode("7");
        FlowNode secondNode = execution.getNode("8");
        assertNotNull(firstNode);

        TestResult firstTests = action.getResult().getResultByRunAndNode(r.getExternalizableId(), firstNode.getId());
        assertNotNull(firstTests);
        assertEquals(1, firstTests.getSuites().size());
        assertEquals(6, firstTests.getTotalCount());

        assertNotNull(secondNode);

        TestResult secondTests = action.getResult().getResultByRunAndNode(r.getExternalizableId(), secondNode.getId());
        assertNotNull(secondTests);
        assertEquals(1, secondTests.getSuites().size());
        assertEquals(1, secondTests.getTotalCount());

        TestResult combinedTests = action.getResult().getResultByRunAndNodes(r.getExternalizableId(),
                Arrays.asList(firstNode.getId(), secondNode.getId()));
        assertNotNull(combinedTests);
        assertEquals(2, combinedTests.getSuites().size());
        assertEquals(7, combinedTests.getTotalCount());
    }
}
