package hudson.tasks.junit.pipeline;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.base.Predicate;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.Messages;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultTest;
import hudson.tasks.test.PipelineBlockWithTests;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

@WithJenkins
class JUnitResultsStepTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        this.rule = rule;
    }

    @Test
    void configRoundTrip() throws Exception {
        SnippetizerTester st = new SnippetizerTester(rule);
        JUnitResultsStep step = new JUnitResultsStep("**/target/surefire-reports/TEST-*.xml");
        st.assertRoundTrip(step, "junit '**/target/surefire-reports/TEST-*.xml'");
        step.setAllowEmptyResults(true);
        st.assertRoundTrip(step, "junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'");
        step.setHealthScaleFactor(2.0);
        st.assertRoundTrip(
                step,
                "junit allowEmptyResults: true, healthScaleFactor: 2.0, testResults: '**/target/surefire-reports/TEST-*.xml'");
        MockTestDataPublisher publisher = new MockTestDataPublisher("testing");
        step.setTestDataPublishers(Collections.singletonList(publisher));
        st.assertRoundTrip(
                step,
                "junit allowEmptyResults: true, healthScaleFactor: 2.0, testDataPublishers: [[$class: 'MockTestDataPublisher', name: 'testing']], testResults: '**/target/surefire-reports/TEST-*.xml'");
        step.setSkipMarkingBuildUnstable(true);
        st.assertRoundTrip(
                step,
                "junit allowEmptyResults: true, healthScaleFactor: 2.0, skipMarkingBuildUnstable: true, testDataPublishers: [[$class: 'MockTestDataPublisher', name: 'testing']], testResults: '**/target/surefire-reports/TEST-*.xml'");
    }

    @Issue("JENKINS-48250")
    @Test
    void emptyFails() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "emptyFails");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        + (Functions.isWindows() ? "    bat 'echo hi'\n" : "    sh 'echo hi'\n")
                        + "    junit('*.xml')\n"
                        + "  }\n"
                        + "}\n",
                true));

        WorkflowRun r = j.scheduleBuild2(0).waitForStart();
        rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
        rule.assertLogContains("ERROR: " + Messages.JUnitResultArchiver_NoTestReportFound(), r);
    }

    @Test
    void allowEmpty() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "allowEmpty");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        + (Functions.isWindows() ? "    bat 'echo hi'\n" : "    sh 'echo hi'\n")
                        + "    def results = junit(testResults: '*.xml', allowEmptyResults: true)\n"
                        + "    assert results.totalCount == 0\n"
                        + "  }\n"
                        + "}\n",
                true));

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        assertNull(r.getAction(TestResultAction.class));
        rule.assertLogContains("None of the test reports contained any result", r);
    }

    @Test
    void singleStep() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        + "    touch 'test-result.xml'\n"
                        + "    def results = junit(testResults: '*.xml')\n"
                        + // node id 7
                        "    assert results.totalCount == 6\n"
                        + "  }\n"
                        + "}\n",
                true));
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-1463.xml"), "test-result.xml");

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(1, action.getResult().getSuites().size());
        assertEquals(6, action.getTotalCount());

        assertExpectedResults(r, 1, 6, "8");

        // Case result display names shouldn't include stage, since there's only one stage.
        for (CaseResult c : action.getPassedTests()) {
            assertEquals(c.getTransformedTestName(), c.getDisplayName());
        }
    }

    @Test
    void twoSteps() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "twoSteps");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        + "    touch 'first-result.xml'\n"
                        + "    touch 'second-result.xml'\n"
                        + "    def first = junit(testResults: 'first-result.xml')\n"
                        + // node id 7
                        "    def second = junit(testResults: 'second-result.xml')\n"
                        + // node id 8
                        "    assert first.totalCount == 6\n"
                        + "    assert second.totalCount == 1\n"
                        + "  }\n"
                        + "}\n",
                true));
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-1463.xml"), "first-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-2874.xml"), "second-result.xml");

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(2, action.getResult().getSuites().size());
        assertEquals(7, action.getTotalCount());

        // First call
        assertExpectedResults(r, 1, 6, "9");

        // Second call
        assertExpectedResults(r, 1, 1, "10");

        // Combined calls
        assertExpectedResults(r, 2, 7, "9", "10");

        // Case result display names shouldn't include stage, since there's only one stage.
        for (CaseResult c : action.getPassedTests()) {
            assertEquals(c.getTransformedTestName(), c.getDisplayName());
        }
    }

    @Test
    void twoStepsSkipOldReports() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "twoSteps");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        +
                        // yup very old file so we should not have flaky tests
                        "    touch file:'first-result.xml', timestamp: 2\n"
                        + "    def first = junit(testResults: 'first-result.xml', skipOldReports: true, allowEmptyResults: true)\n"
                        + // node id 8
                        "    def second = junit(testResults: 'second-result.xml')\n"
                        + // node id 9
                        "    assert first.totalCount == 0\n"
                        + "    assert second.totalCount == 1\n"
                        + "  }\n"
                        + "}\n",
                true));
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-1463.xml"), "first-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-2874.xml"), "second-result.xml");

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(1, action.getResult().getSuites().size());
        assertEquals(1, action.getTotalCount());

        // First call
        assertExpectedResults(r, 0, 0, "8");

        // Second call
        assertExpectedResults(r, 1, 1, "9");

        // Combined calls
        assertExpectedResults(r, 1, 1, "8", "9");

        // Case result display names shouldn't include stage, since there's only one stage.
        for (CaseResult c : action.getPassedTests()) {
            assertEquals(c.getTransformedTestName(), c.getDisplayName());
        }
    }

    @Test
    void threeSteps() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "threeSteps");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        +
                        // "  sleep 1\n" +
                        "    touch 'first-result.xml'\n"
                        + "    touch 'second-result.xml'\n"
                        + "    touch 'third-result.xml'\n"
                        + "    def first = junit(testResults: 'first-result.xml')\n"
                        + // node id 7
                        "    def second = junit(testResults: 'second-result.xml')\n"
                        + // node id 8
                        "    def third = junit(testResults: 'third-result.xml')\n"
                        + // node id 9
                        "    assert first.totalCount == 6\n"
                        + "    assert second.totalCount == 1\n"
                        + "  }\n"
                        + "}\n",
                true));
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-1463.xml"), "first-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-2874.xml"), "second-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-nested-testsuites.xml"), "third-result.xml");

        WorkflowRun r = rule.assertBuildStatus(
                Result.UNSTABLE, rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(5, action.getResult().getSuites().size());
        assertEquals(10, action.getTotalCount());

        // First call
        assertExpectedResults(r, 1, 6, "10");

        // Second call
        assertExpectedResults(r, 1, 1, "11");

        // Third call
        assertExpectedResults(r, 3, 3, "12");

        // Combined first and second calls
        assertExpectedResults(r, 2, 7, "10", "11");

        // Combined first and third calls
        assertExpectedResults(r, 4, 9, "10", "12");

        // Case result display names shouldn't include stage, since there's only one stage.
        for (CaseResult c : action.getPassedTests()) {
            assertEquals(c.getTransformedTestName(), c.getDisplayName());
        }
    }

    @Test
    void parallelInStage() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "parallelInStage");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-1463.xml"), "first-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-2874.xml"), "second-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-nested-testsuites.xml"), "third-result.xml");

        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            touch 'first-result.xml'
                            touch 'second-result.xml'
                            touch 'third-result.xml'
                            parallel(a: { def first = junit(testResults: 'first-result.xml'); assert first.totalCount == 6 },
                                     b: { def second = junit(testResults: 'second-result.xml'); assert second.totalCount == 1 },
                                     c: { def third = junit(testResults: 'third-result.xml', keepTestNames: true); assert third.totalCount == 3 })
                          }
                        }
                        """,
                true));
        WorkflowRun r = rule.assertBuildStatus(
                Result.UNSTABLE, rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(5, action.getResult().getSuites().size());
        assertEquals(10, action.getTotalCount());

        assertBranchResults(r, 1, 6, 0, "a", "first", null, false);
        assertBranchResults(r, 1, 1, 0, "b", "first", null, false);
        assertBranchResults(r, 3, 3, 1, "c", "first", null, true);
        assertStageResults(r, 5, 10, 1, "first");
    }

    @Issue("JENKINS-48196")
    @Test
    void stageInParallel() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "stageInParallel");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-1463.xml"), "first-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-2874.xml"), "second-result.xml");
        copyToWorkspace(j, TestResultTest.class.getResource("junit-report-nested-testsuites.xml"), "third-result.xml");

        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('outer') {
                          node {
                            touch 'first-result.xml'
                            touch 'second-result.xml'
                            touch 'third-result.xml'
                            parallel(a: { stage('a') { def first = junit(testResults: 'first-result.xml'); assert first.totalCount == 6 }  },
                                     b: { stage('b') { def second = junit(testResults: 'second-result.xml'); assert second.totalCount == 1 } },
                                     c: { stage('d') { def third = junit(testResults: 'third-result.xml', keepTestNames: true); assert third.totalCount == 3 } })
                          }
                        }
                        """,
                true));
        WorkflowRun r = rule.assertBuildStatus(
                Result.UNSTABLE, rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
        TestResultAction action = r.getAction(TestResultAction.class);
        assertNotNull(action);
        assertEquals(5, action.getResult().getSuites().size());
        assertEquals(10, action.getTotalCount());

        // assertBranchResults looks to make sure the display names for tests are "(stageName) / (branchName) /
        // (testName)"
        // That should still effectively be the case here, even though there's a stage inside each branch, because the
        // branch and nested stage have the same name.
        assertBranchResults(r, 1, 6, 0, "a", "outer", null, false);
        assertBranchResults(r, 1, 1, 0, "b", "outer", null, false);
        // ...except for branch c. That contains a stage named 'd', so its test should have display names like
        // "outer / c / d / (testName)"
        assertBranchResults(r, 3, 3, 1, "c", "outer", "d", true);
    }

    @Test
    void testTrends() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "testTrends");
        j.setDefinition(new CpsFlowDefinition(
                """
                        node {
                          stage('first') {
                            touch 'junit-report-testTrends-first.xml'
                            def first = junit(testResults: "junit-report-testTrends-first.xml")
                          }
                          stage('second') {
                            touch 'junit-report-testTrends-second.xml'
                            def second = junit(testResults: "junit-report-testTrends-second.xml")
                          }
                        }
                        """,
                true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath firstFile = ws.child("junit-report-testTrends-first.xml");
        FilePath secondFile = ws.child("junit-report-testTrends-second.xml");

        // Populate first run's tests.
        firstFile.copyFrom(JUnitResultsStepTest.class.getResource("junit-report-testTrends-first-1.xml"));
        secondFile.copyFrom(JUnitResultsStepTest.class.getResource("junit-report-testTrends-second-1.xml"));

        WorkflowRun firstRun = rule.buildAndAssertSuccess(j);
        assertStageResults(firstRun, 1, 8, 0, "first");
        assertStageResults(firstRun, 1, 1, 0, "second");

        // Populate second run's tests.
        firstFile.copyFrom(JUnitResultsStepTest.class.getResource("junit-report-testTrends-first-2.xml"));
        secondFile.copyFrom(JUnitResultsStepTest.class.getResource("junit-report-testTrends-second-2.xml"));

        WorkflowRun secondRun = rule.assertBuildStatus(
                Result.UNSTABLE, rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
        assertStageResults(secondRun, 1, 8, 3, "first");
        assertStageResults(secondRun, 1, 1, 0, "second");

        // Populate third run's tests
        firstFile.copyFrom(JUnitResultsStepTest.class.getResource("junit-report-testTrends-first-3.xml"));
        secondFile.copyFrom(JUnitResultsStepTest.class.getResource("junit-report-testTrends-second-3.xml"));

        WorkflowRun thirdRun = rule.assertBuildStatus(
                Result.UNSTABLE, rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
        assertStageResults(thirdRun, 1, 8, 3, "first");
        assertStageResults(thirdRun, 1, 1, 0, "second");
        TestResultAction thirdAction = thirdRun.getAction(TestResultAction.class);
        assertNotNull(thirdAction);

        for (CaseResult failed : thirdAction.getFailedTests()) {
            if (failed.getDisplayName() != null) {
                if (failed.getDisplayName().equals("first / testGetVendorFirmKeyForVendorRep")) {
                    assertEquals(
                            "first / org.twia.vendor.VendorManagerTest.testGetVendorFirmKeyForVendorRep",
                            failed.getFullDisplayName());
                    assertEquals(2, failed.getFailedSince());
                } else if (failed.getDisplayName().equals("first / testCreateAdjustingFirm")) {
                    assertEquals(
                            "first / org.twia.vendor.VendorManagerTest.testCreateAdjustingFirm",
                            failed.getFullDisplayName());
                    assertEquals(2, failed.getFailedSince());
                } else if (failed.getDisplayName().equals("first / testCreateVendorFirm")) {
                    assertEquals(
                            "first / org.twia.vendor.VendorManagerTest.testCreateVendorFirm",
                            failed.getFullDisplayName());
                    assertEquals(3, failed.getFailedSince());
                } else {
                    fail("Failed test displayName " + failed.getDisplayName() + " is unexpected.");
                }
            }
        }
    }

    @Issue("JENKINS-48178")
    @Test
    void currentBuildResultUnstable() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "currentBuildResultUnstable");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        + "    def results = junit(testResults: '*.xml', skipOldReports: true)\n"
                        + // node id 7
                        "    assert results.totalCount == 8\n"
                        + "    assert currentBuild.result == 'UNSTABLE'\n"
                        + "  }\n"
                        + "}\n",
                true));
        copyToWorkspace(
                j, JUnitResultsStepTest.class.getResource("junit-report-testTrends-first-2.xml"), "test-result.xml");

        rule.assertBuildStatus(
                Result.UNSTABLE, rule.waitForCompletion(j.scheduleBuild2(0).waitForStart()));
    }

    @Test
    void skipBuildUnstable() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "currentBuildResultUnstable");
        j.setDefinition(new CpsFlowDefinition(
                "stage('first') {\n" + "  node {\n"
                        + "    touch 'test-result.xml'\n"
                        + "    def results = junit(skipMarkingBuildUnstable: true, testResults: '*.xml')\n"
                        + // node id 7
                        "    assert results.totalCount == 8\n"
                        + "    assert currentBuild.result == null\n"
                        + "  }\n"
                        + "}\n",
                true));
        copyToWorkspace(
                j, JUnitResultsStepTest.class.getResource("junit-report-testTrends-first-2.xml"), "test-result.xml");
        WorkflowRun r = rule.waitForCompletion(j.scheduleBuild2(0).waitForStart());
        rule.assertBuildStatus(Result.SUCCESS, r);
        assertStageResults(r, 1, 8, 3, "first");
    }

    @Test
    void ageResetSameTestSuiteName() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "p");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('stage 1') {
                          node {
                            touch 'ageReset-1.xml'
                            junit(testResults: '*-1.xml')
                          }
                        }
                        stage('stage 2') {
                          node {
                            touch 'ageReset-2.xml'
                            junit(testResults: '*-2.xml')
                          }
                        }
                        """,
                true));
        copyToWorkspace(j, JUnitResultsStepTest.class.getResource("ageReset-1.xml"), "ageReset-1.xml");
        copyToWorkspace(j, JUnitResultsStepTest.class.getResource("ageReset-2.xml"), "ageReset-2.xml");
        WorkflowRun r = rule.waitForCompletion(j.scheduleBuild2(0).waitForStart());
        rule.assertBuildStatus(Result.UNSTABLE, r);
        assertEquals(
                2, r.getAction(TestResultAction.class).getResult().getSuites().size());
        CaseResult caseResult = findCaseResult(r, "aClass.methodName");
        assertNotNull(caseResult);
        assertEquals(1, caseResult.getAge());

        // Run a second build, age should increase
        r = rule.waitForCompletion(j.scheduleBuild2(0).waitForStart());
        rule.assertBuildStatus(Result.UNSTABLE, r);
        caseResult = findCaseResult(r, "aClass.methodName");
        assertNotNull(caseResult);
        assertEquals(2, caseResult.getAge());
    }

    private CaseResult findCaseResult(Run r, String name) {
        for (SuiteResult suite : r.getAction(TestResultAction.class).getResult().getSuites()) {
            CaseResult caseResult = suite.getCase(name);
            if (caseResult != null) {
                return caseResult;
            }
        }
        return null;
    }

    private void copyToWorkspace(WorkflowJob j, URL source, String destination)
            throws IOException, InterruptedException {
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = ws.child(destination);
        testFile.copyFrom(source);
    }

    private static Predicate<FlowNode> branchForName(final String name) {
        return input -> input != null
                && input.getAction(LabelAction.class) != null
                && input.getAction(ThreadNameAction.class) != null
                && name.equals(input.getAction(ThreadNameAction.class).getThreadName());
    }

    private static Predicate<FlowNode> stageForName(final String name) {
        return input -> input instanceof StepStartNode
                && ((StepStartNode) input).getDescriptor() instanceof StageStep.DescriptorImpl
                && input.getDisplayName().equals(name);
    }

    public static void assertBranchResults(
            WorkflowRun run,
            int suiteCount,
            int testCount,
            int failCount,
            String branchName,
            String stageName,
            String innerStageName) {
        assertBranchResults(run, suiteCount, testCount, failCount, branchName, stageName, innerStageName, false);
    }

    public static void assertBranchResults(
            WorkflowRun run,
            int suiteCount,
            int testCount,
            int failCount,
            String branchName,
            String stageName,
            String innerStageName,
            boolean keepTestNames) {
        FlowExecution execution = run.getExecution();
        DepthFirstScanner scanner = new DepthFirstScanner();
        BlockStartNode aBranch = (BlockStartNode) scanner.findFirstMatch(execution, branchForName(branchName));
        assertNotNull(aBranch);
        TestResult branchResult = assertBlockResults(run, suiteCount, testCount, failCount, aBranch);
        String namePrefix;
        if (!keepTestNames) {
            namePrefix = stageName + " / " + branchName;
            if (innerStageName != null) {
                namePrefix += " / " + innerStageName;
            }
            namePrefix += " / ";
        } else {
            namePrefix = "";
        }
        for (CaseResult c : branchResult.getPassedTests()) {
            assertEquals(namePrefix + c.getTransformedTestName(), c.getDisplayName());
        }
    }

    public static void assertStageResults(
            WorkflowRun run, int suiteCount, int testCount, int failCount, String stageName) {
        FlowExecution execution = run.getExecution();
        DepthFirstScanner scanner = new DepthFirstScanner();
        BlockStartNode aStage = (BlockStartNode) scanner.findFirstMatch(execution, stageForName(stageName));
        assertNotNull(aStage);
        assertBlockResults(run, suiteCount, testCount, failCount, aStage);
    }

    private static TestResult assertBlockResults(
            WorkflowRun run, int suiteCount, int testCount, int failCount, BlockStartNode blockNode) {
        assertNotNull(blockNode);

        TestResultAction action = run.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResult aResult = action.getResult().getResultForPipelineBlock(blockNode.getId());
        assertNotNull(aResult);

        assertEquals(suiteCount, aResult.getSuites().size());
        assertEquals(testCount, aResult.getTotalCount());
        assertEquals(failCount, aResult.getFailCount());
        if (failCount > 0) {
            assertThat(findJUnitSteps(blockNode), CoreMatchers.hasItem(hasWarningAction()));
        } else {
            assertThat(findJUnitSteps(blockNode), CoreMatchers.not(CoreMatchers.hasItem(hasWarningAction())));
        }

        PipelineBlockWithTests aBlock = action.getResult().getPipelineBlockWithTests(blockNode.getId());

        assertNotNull(aBlock);
        List<String> aTestNodes = new ArrayList<>(aBlock.nodesWithTests());
        TestResult aFromNodes = action.getResult().getResultByNodes(aTestNodes);
        assertNotNull(aFromNodes);
        assertEquals(aResult.getSuites().size(), aFromNodes.getSuites().size());
        assertEquals(aResult.getFailCount(), aFromNodes.getFailCount());
        assertEquals(aResult.getSkipCount(), aFromNodes.getSkipCount());
        assertEquals(aResult.getPassCount(), aFromNodes.getPassCount());

        return aResult;
    }

    private void assertExpectedResults(Run<?, ?> run, int suiteCount, int testCount, String... nodeIds) {
        TestResultAction action = run.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResult result = action.getResult().getResultByNodes(Arrays.asList(nodeIds));
        assertNotNull(result);
        assertEquals(suiteCount, result.getSuites().size());
        assertEquals(testCount, result.getTotalCount());
    }

    private static List<FlowNode> findJUnitSteps(BlockStartNode blockStart) {
        return new DepthFirstScanner()
                .filteredNodes(
                        Collections.singletonList(blockStart.getEndNode()),
                        Collections.singletonList(blockStart),
                        node -> node instanceof StepAtomNode
                                && ((StepAtomNode) node).getDescriptor() instanceof JUnitResultsStep.DescriptorImpl);
    }

    private static BaseMatcher<FlowNode> hasWarningAction() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof FlowNode && ((FlowNode) item).getPersistentAction(WarningAction.class) != null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a FlowNode with a WarningAction");
            }
        };
    }

    public static class MockTestDataPublisher extends TestDataPublisher {
        private final String name;

        @DataBoundConstructor
        public MockTestDataPublisher(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public TestResultAction.Data contributeTestData(
                Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, TestResult testResult) {
            return null;
        }

        // Needed to make this extension available to all tests for {@link #testDescribableRoundTrip()}
        @TestExtension
        public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
            @Override
            public String getDisplayName() {
                return "MockTestDataPublisher";
            }
        }
    }
}
