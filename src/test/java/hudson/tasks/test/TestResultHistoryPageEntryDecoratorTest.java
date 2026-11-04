package hudson.tasks.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.widgets.HistoryWidget;
import jenkins.widgets.HistoryPageEntryDecorator;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TestResultHistoryPageEntryDecoratorTest {

    @Test
    void summarisesCountsRegressionsAndFixes(JenkinsRule rule) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new TouchBuilder());
        JUnitResultArchiver archiver = new JUnitResultArchiver("x.xml");
        archiver.setAllowEmptyResults(true);
        p.getPublishersList().add(archiver);
        FilePath report = rule.jenkins.getWorkspaceFor(p).child("x.xml");

        report.write(
                "<testsuite>"
                        + "<testcase classname='A' name='fixed'><failure/></testcase>"
                        + "<testcase classname='A' name='regressed'/>"
                        + "<testcase classname='A' name='stillPassing'/>"
                        + "</testsuite>",
                null);
        rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));

        report.write(
                "<testsuite>"
                        + "<testcase classname='A' name='fixed'/>"
                        + "<testcase classname='A' name='regressed'><failure/></testcase>"
                        + "<testcase classname='A' name='stillPassing'/>"
                        + "<testcase classname='A' name='skipped'><skipped/></testcase>"
                        + "</testsuite>",
                null);
        FreeStyleBuild build = rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));

        TestResultHistoryPageEntryDecorator decorator =
                HistoryPageEntryDecorator.all().get(TestResultHistoryPageEntryDecorator.class);
        assertTrue(decorator.isApplicable(widget(), build));

        TestResultHistoryPageEntryDecorator.Summary summary = decorator.getSummary(build);
        assertEquals(1, summary.getFailCount());
        assertEquals(2, summary.getPassCount());
        assertEquals(1, summary.getSkipCount());
        assertEquals(4, summary.getTotalCount());
        assertEquals(1, summary.getRegressionCount());
        assertEquals(1, summary.getFixedCount());
        assertEquals("testReport", summary.getUrlName());
    }

    @Test
    void countsFixesOfTestsMissingFromThePreviousBuild(JenkinsRule rule) throws Exception {
        FreeStyleProject p = createProject(rule);
        FilePath report = rule.jenkins.getWorkspaceFor(p).child("x.xml");

        report.write(
                "<testsuite name='S'>"
                        + "<testcase classname='A' name='flaky'><failure/></testcase>"
                        + "<testcase classname='A' name='other'/>"
                        + "</testsuite>",
                null);
        rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));

        // flaky doesn't run in this build, so the next build has to look further back to find it failing
        report.write("<testsuite name='S'><testcase classname='A' name='other'/></testsuite>", null);
        rule.buildAndAssertSuccess(p);

        report.write(
                "<testsuite name='S'>"
                        + "<testcase classname='A' name='flaky'/>"
                        + "<testcase classname='A' name='other'/>"
                        + "</testsuite>",
                null);
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);

        assertEquals(1, summary(build).getFixedCount());
    }

    @Test
    void countsAcrossSeparatelyRecordedResults(JenkinsRule rule) throws Exception {
        WorkflowJob p = rule.createProject(WorkflowJob.class);
        // Each stage records its own results, so the counts must cover both once they're merged
        p.setDefinition(new CpsFlowDefinition("""
                def unit = [1: "<testcase classname='U' name='a'><failure/></testcase><testcase classname='U' name='b'/>",
                            2: "<testcase classname='U' name='a'/><testcase classname='U' name='b'><failure/></testcase>"]
                def integration = [1: "<testcase classname='I' name='x'/><testcase classname='I' name='y'><failure/></testcase>",
                                   2: "<testcase classname='I' name='x'><failure/></testcase><testcase classname='I' name='y'/>"]
                def n = env.BUILD_NUMBER as int
                parallel unit: {
                    stage('Unit') {
                        node {
                            writeFile file: 'unit.xml', text: "<testsuite name='unit'>" + unit[n] + "</testsuite>"
                            junit 'unit.xml'
                        }
                    }
                }, integration: {
                    stage('Integration') {
                        node {
                            writeFile file: 'integration.xml', text: "<testsuite name='integration'>" + integration[n] + "</testsuite>"
                            junit 'integration.xml'
                        }
                    }
                }
                """, true));
        rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));
        WorkflowRun build = rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));

        TestResultHistoryPageEntryDecorator.Summary summary = summary(build);
        assertEquals(2, summary.getFailCount());
        assertEquals(2, summary.getPassCount());
        assertEquals(2, summary.getRegressionCount());
        assertEquals(2, summary.getFixedCount());
    }

    @Test
    void doesNotMatchTestsWhoseIdsCollide(JenkinsRule rule) throws Exception {
        FreeStyleProject p = createProject(rule);
        FilePath report = rule.jenkins.getWorkspaceFor(p).child("x.xml");

        // "test-a" and "test a" have the same ID once made safe for a URL
        report.write(
                "<testsuite name='S'><testcase classname='A' name='test-a'><failure/></testcase></testsuite>", null);
        rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));

        report.write("<testsuite name='S'><testcase classname='A' name='test a'/></testsuite>", null);
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);

        assertEquals(0, summary(build).getFixedCount());
    }

    @Test
    void linksToTheReportOfTheAction(JenkinsRule rule) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);
        build.addAction(new CustomTestResultAction());

        TestResultHistoryPageEntryDecorator.Summary summary = summary(build);
        assertEquals("customReport", summary.getUrlName());
        assertEquals(1, summary.getFailCount());
        assertEquals(0, summary.getRegressionCount());
        assertEquals(0, summary.getFixedCount());
    }

    @Test
    void notApplicableWithoutTestResults(JenkinsRule rule) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);

        TestResultHistoryPageEntryDecorator decorator =
                HistoryPageEntryDecorator.all().get(TestResultHistoryPageEntryDecorator.class);
        assertFalse(decorator.isApplicable(widget(), build));
    }

    private static FreeStyleProject createProject(JenkinsRule rule) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new TouchBuilder());
        p.getPublishersList().add(new JUnitResultArchiver("x.xml"));
        return p;
    }

    private static TestResultHistoryPageEntryDecorator.Summary summary(Run<?, ?> build) {
        return HistoryPageEntryDecorator.all()
                .get(TestResultHistoryPageEntryDecorator.class)
                .getSummary(build);
    }

    // Like an AggregatedTestResultPublisher.TestResultAction, which isn't served at testReport/
    private static final class CustomTestResultAction extends AbstractTestResultAction<CustomTestResultAction> {

        @Override
        public String getUrlName() {
            return "customReport";
        }

        @Override
        public int getFailCount() {
            return 1;
        }

        @Override
        public int getTotalCount() {
            return 2;
        }

        @Override
        public Object getResult() {
            return null;
        }
    }

    // The decorator doesn't use the widget, and a real one needs an active request
    private static HistoryWidget<?, ?> widget() {
        return mock(HistoryWidget.class);
    }
}
