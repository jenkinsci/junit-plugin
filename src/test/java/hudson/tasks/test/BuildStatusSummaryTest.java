package hudson.tasks.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests {@link Run#getBuildStatusSummary()}.
 *
 * @author kutzi
 */
@SuppressWarnings("rawtypes")
@WithJenkins
class BuildStatusSummaryTest {

    private Run build;
    private Run prevBuild;

    private JenkinsRule r; // to load AbstractTestResultAction.Summarizer

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
        mockBuilds(Run.class);
    }

    private void mockBuilds(Class<? extends Run> buildClass) {
        this.build = mock(buildClass);
        this.prevBuild = mock(buildClass);

        when(this.build.getPreviousBuild()).thenReturn(prevBuild);

        when(this.build.getBuildStatusSummary()).thenCallRealMethod();
    }

    @Test
    void testBuildGotAFailingTest() {
        // previous build has no tests at all
        mockBuilds(AbstractBuild.class);

        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);

        buildHasTestResult((AbstractBuild) this.build, 1);

        Run.Summary summary = this.build.getBuildStatusSummary();

        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_TestFailures(1), summary.message);

        // same thing should happen if previous build has tests, but no failing ones:
        buildHasTestResult((AbstractBuild) this.prevBuild, 0);
        summary = this.build.getBuildStatusSummary();
        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_TestsStartedToFail(1), summary.message);
    }

    @Test
    void testBuildGotNoTests() {
        // previous build has no tests at all
        mockBuilds(AbstractBuild.class);

        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);
        // Null test result action recorded
        when(this.build.getAction(AbstractTestResultAction.class)).thenReturn(null);

        Run.Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(hudson.model.Messages.Run_Summary_Unstable(), summary.message);

        // same thing should happen if previous build has tests, but no failing ones:
        buildHasTestResult((AbstractBuild) this.prevBuild, 0);
        summary = this.build.getBuildStatusSummary();
        assertFalse(summary.isWorse);
        assertEquals(hudson.model.Messages.Run_Summary_Unstable(), summary.message);
    }

    @Test
    void testBuildEqualAmountOfTestsFailing() {
        mockBuilds(AbstractBuild.class);

        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);

        buildHasTestResult((AbstractBuild) this.prevBuild, 1);
        buildHasTestResult((AbstractBuild) this.build, 1);

        Run.Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_TestsStillFailing(1), summary.message);
    }

    @Test
    void testBuildGotMoreFailingTests() {
        mockBuilds(AbstractBuild.class);

        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);

        buildHasTestResult((AbstractBuild) this.prevBuild, 1);
        buildHasTestResult((AbstractBuild) this.build, 2);

        Run.Summary summary = this.build.getBuildStatusSummary();

        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_MoreTestsFailing(1, 2), summary.message);
    }

    @Test
    void testBuildGotLessFailingTests() {
        mockBuilds(AbstractBuild.class);

        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);

        buildHasTestResult((AbstractBuild) this.prevBuild, 2);
        buildHasTestResult((AbstractBuild) this.build, 1);

        Run.Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_LessTestsFailing(1, 1), summary.message);
    }

    private void buildHasTestResult(AbstractBuild build, int failedTests) {
        AbstractTestResultAction testResult = mock(AbstractTestResultAction.class);
        when(testResult.getFailCount()).thenReturn(failedTests);

        when(build.getAction(AbstractTestResultAction.class)).thenReturn(testResult);
    }
}
