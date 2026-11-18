package hudson.tasks.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Builds whose {@code build.xml} contains {@link CaseResult}s with flaky or rerun failures must still load.
 */
@WithJenkins
class FlakyFailurePersistenceTest {

    private static final String FLAKY_REPORT = """
            <testsuite name="s" tests="1" failures="0" errors="0">
              <testcase classname="c" name="t" time="1.0">
                <flakyFailure message="first attempt failed" type="failure"><stackTrace>stack</stackTrace></flakyFailure>
              </testcase>
            </testsuite>
            """;

    private static final String PLAIN_REPORT = """
            <testsuite name="s" tests="1" failures="0" errors="0">
              <testcase classname="c" name="t" time="1.0"/>
            </testsuite>
            """;

    @Issue("GH-1298")
    @Test
    void buildWithFlakyFailureInBuildXmlSurvivesReload(JenkinsRule r) throws Exception {
        WorkflowJob job = runAndReload(r, "flaky", FLAKY_REPORT);

        WorkflowRun build = job.getBuildByNumber(1);
        assertNotNull(build, "build was lost on reload");
        assertNotNull(build.getExecution(), "build.xml failed to load");
        assertFalse(build.isBuilding());
        assertEquals(Result.SUCCESS, build.getResult());

        ResultHoldingData data = build.getAction(TestResultAction.class).getData().stream()
                .filter(ResultHoldingData.class::isInstance)
                .map(ResultHoldingData.class::cast)
                .findFirst()
                .orElseThrow();
        // TestResult's lookup maps are transient, so search the deserialized lists
        List<Failure> flakyFailures = data.testResult.getSuites().stream()
                .flatMap(suite -> suite.getCases().stream())
                .filter(c -> c.getFullName().equals("c.t"))
                .findFirst()
                .orElseThrow()
                .getFlakyFailures();
        assertEquals(List.of(new Failure("first attempt failed", "failure", "stack", null, null)), flakyFailures);
    }

    @Issue("GH-1298")
    @Test
    void buildWithoutFlakyFailureSurvivesReload(JenkinsRule r) throws Exception {
        WorkflowJob job = runAndReload(r, "plain", PLAIN_REPORT);

        WorkflowRun build = job.getBuildByNumber(1);
        assertNotNull(build);
        assertNotNull(build.getExecution());
        assertEquals(Result.SUCCESS, build.getResult());
    }

    private static WorkflowJob runAndReload(JenkinsRule r, String name, String report) throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, name);
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  writeFile file: 'reports/r.xml', text: '''" + report + "'''\n"
                        + "  junit testResults: 'reports/*.xml', testDataPublishers: [[$class: 'ResultHoldingPublisher']]\n"
                        + "}",
                true));
        r.buildAndAssertSuccess(job);

        r.jenkins.reload();
        return r.jenkins.getItemByFullName(name, WorkflowJob.class);
    }

    /** Stores a reference to the whole {@link TestResult} in {@code build.xml}, like Flaky Test Handler does. */
    public static class ResultHoldingPublisher extends TestDataPublisher {

        @DataBoundConstructor
        public ResultHoldingPublisher() {}

        @Override
        public TestResultAction.Data contributeTestData(
                Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, TestResult testResult) {
            return new ResultHoldingData(testResult);
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
            @Override
            public String getDisplayName() {
                return "ResultHoldingPublisher";
            }
        }
    }

    public static class ResultHoldingData extends TestResultAction.Data {
        private final TestResult testResult;

        ResultHoldingData(TestResult testResult) {
            this.testResult = testResult;
        }

        @Override
        public List<? extends TestAction> getTestAction(TestObject testObject) {
            return List.of();
        }
    }
}
