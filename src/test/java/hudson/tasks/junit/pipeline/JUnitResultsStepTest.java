package hudson.tasks.junit.pipeline;

import com.google.common.base.Predicate;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultTest;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                "    junit('*.xml')\n" +
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
                "    def results = junit(testResults: '*.xml', allowEmptyResults: true)\n" +
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
                "    def results = junit(testResults: '*.xml')\n" + // node id 7
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

        assertExpectedResults(r, 1, 6, "7");

        // Case result display names shouldn't include stage, since there's only one stage.
        for (CaseResult c : action.getPassedTests()) {
            assertEquals(c.getTransformedTestName(), c.getDisplayName());
        }
    }

    @Test
    public void twoSteps() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "twoSteps");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def first = junit(testResults: 'first-result.xml')\n" +    // node id 7
                "    def second = junit(testResults: 'second-result.xml')\n" +  // node id 8
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

        // First call
        assertExpectedResults(r, 1, 6, "7");

        // Second call
        assertExpectedResults(r, 1, 1, "8");

        // Combined calls
        assertExpectedResults(r, 2, 7, "7", "8");

        // Case result display names shouldn't include stage, since there's only one stage.
        for (CaseResult c : action.getPassedTests()) {
            assertEquals(c.getTransformedTestName(), c.getDisplayName());
        }
    }

    @Test
    public void threeSteps() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "threeSteps");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    def first = junit(testResults: 'first-result.xml')\n" +    // node id 7
                "    def second = junit(testResults: 'second-result.xml')\n" +  // node id 8
                "    def third = junit(testResults: 'third-result.xml')\n" +    // node id 9
                "    assert first.totalCount == 6\n" +
                "    assert second.totalCount == 1\n" +
                "  }\n" +
                "}\n", true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = ws.child("first-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));
        FilePath secondTestFile = ws.child("second-result.xml");
        secondTestFile.copyFrom(TestResultTest.class.getResource("junit-report-2874.xml"));
        FilePath thirdTestFile = ws.child("third-result.xml");
        thirdTestFile.copyFrom(TestResultTest.class.getResource("junit-report-nested-testsuites.xml"));

        WorkflowRun r = rule.assertBuildStatus(Result.UNSTABLE,
                rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(5, action.getResult().getSuites().size());
        assertEquals(10, action.getTotalCount());

        // First call
        assertExpectedResults(r, 1, 6, "7");

        // Second call
        assertExpectedResults(r, 1, 1, "8");

        // Third call
        assertExpectedResults(r, 3, 3, "9");

        // Combined first and second calls
        assertExpectedResults(r, 2, 7, "7", "8");

        // Combined first and third calls
        assertExpectedResults(r, 4, 9, "7", "9");

        // Case result display names shouldn't include stage, since there's only one stage.
        for (CaseResult c : action.getPassedTests()) {
            assertEquals(c.getTransformedTestName(), c.getDisplayName());
        }
    }

    @Test
    public void parallelInStage() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "parallelInStage");
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = ws.child("first-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));
        FilePath secondTestFile = ws.child("second-result.xml");
        secondTestFile.copyFrom(TestResultTest.class.getResource("junit-report-2874.xml"));
        FilePath thirdTestFile = ws.child("third-result.xml");
        thirdTestFile.copyFrom(TestResultTest.class.getResource("junit-report-nested-testsuites.xml"));

        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    parallel(a: { def first = junit(testResults: 'first-result.xml'); assert first.totalCount == 6 },\n" +
                "             b: { def second = junit(testResults: 'second-result.xml'); assert second.totalCount == 1 },\n" +
                "             c: { def third = junit(testResults: 'third-result.xml'); assert third.totalCount == 3 })\n" +
                "  }\n" +
                "}\n", true
        ));
        WorkflowRun r = rule.assertBuildStatus(Result.UNSTABLE,
                rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(5, action.getResult().getSuites().size());
        assertEquals(10, action.getTotalCount());

        assertBranchResults(r, 1, 6, "a", "first");
        assertBranchResults(r, 1, 1, "b", "first");
        assertBranchResults(r, 3, 3, "c", "first");
        assertStageResults(r, 5, 10, "first");
    }

    private static Predicate<FlowNode> branchForName(final String name) {
        return new Predicate<FlowNode>() {
            @Override
            public boolean apply(@Nullable FlowNode input) {
                return input != null &&
                        input.getAction(LabelAction.class) != null &&
                        input.getAction(ThreadNameAction.class) != null &&
                        name.equals(input.getAction(ThreadNameAction.class).getThreadName());
            }
        };
    }

    private static Predicate<FlowNode> stageForName(final String name) {
        return new Predicate<FlowNode>() {
            @Override
            public boolean apply(@Nullable FlowNode input) {
                return input instanceof StepStartNode &&
                        ((StepStartNode) input).getDescriptor() instanceof StageStep.DescriptorImpl &&
                        input.getDisplayName().equals(name);
            }
        };
    }

    public static void assertBranchResults(WorkflowRun run, int suiteCount, int testCount, String branchName, String stageName) {
        FlowExecution execution = run.getExecution();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode aBranch = scanner.findFirstMatch(execution, branchForName(branchName));
        assertNotNull(aBranch);
        TestResult branchResult = assertBlockResults(run, suiteCount, testCount, aBranch);
        for (CaseResult c : branchResult.getPassedTests()) {
            assertEquals(stageName + " / " + branchName + " / " + c.getTransformedTestName(), c.getDisplayName());
        }
    }

    public static void assertStageResults(WorkflowRun run, int suiteCount, int testCount, String stageName) {
        FlowExecution execution = run.getExecution();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode aStage = scanner.findFirstMatch(execution, stageForName(stageName));
        assertNotNull(aStage);
        assertBlockResults(run, suiteCount, testCount, aStage);
    }

    private static TestResult assertBlockResults(WorkflowRun run, int suiteCount, int testCount, FlowNode blockNode) {
        assertNotNull(blockNode);

        TestResultAction action = run.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResult.BlocksWithChildren aBlock = action.getResult().getBlockWithChildren(run.getExternalizableId(), blockNode.getId());

        assertNotNull(aBlock);
        TestResult aResult = aBlock.toTestResult(run.getExternalizableId(), action.getResult());
        assertNotNull(aResult);

        assertEquals(suiteCount, aResult.getSuites().size());
        assertEquals(testCount, aResult.getTotalCount());

        List<String> aTestNodes = new ArrayList<>(aBlock.nodesWithTests());
        TestResult aFromNodes = action.getResult().getResultByRunAndNodes(run.getExternalizableId(), aTestNodes);
        assertNotNull(aFromNodes);
        assertEquals(aResult.getSuites().size(), aFromNodes.getSuites().size());
        assertEquals(aResult.getFailCount(), aFromNodes.getFailCount());
        assertEquals(aResult.getSkipCount(), aFromNodes.getSkipCount());
        assertEquals(aResult.getPassCount(), aFromNodes.getPassCount());

        return aResult;
    }

    private void assertExpectedResults(Run<?,?> run, int suiteCount, int testCount, String... nodeIds) throws Exception {
        TestResultAction action = run.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResult result = action.getResult().getResultByRunAndNodes(run.getExternalizableId(), Arrays.asList(nodeIds));
        assertNotNull(result);
        assertEquals(suiteCount, result.getSuites().size());
        assertEquals(testCount, result.getTotalCount());
    }
}
