package hudson.tasks.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.widgets.HistoryWidget;
import jenkins.widgets.HistoryPageEntryDecorator;
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
    }

    @Test
    void notApplicableWithoutTestResults(JenkinsRule rule) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);

        TestResultHistoryPageEntryDecorator decorator =
                HistoryPageEntryDecorator.all().get(TestResultHistoryPageEntryDecorator.class);
        assertFalse(decorator.isApplicable(widget(), build));
    }

    // The decorator doesn't use the widget, and a real one needs an active request
    private static HistoryWidget<?, ?> widget() {
        return mock(HistoryWidget.class);
    }
}
