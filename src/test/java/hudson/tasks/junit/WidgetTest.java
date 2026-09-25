package hudson.tasks.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class WidgetTest {

    @TempDir
    private File tmp;

    @Test
    void showsFailedAndCounts() throws Exception {
        File report = new File(tmp, "x.xml");
        Files.writeString(
                report.toPath(),
                "<testsuite>"
                        + "<testcase classname='A' name='passed'/>"
                        + "<testcase classname='A' name='failed'><failure/></testcase>"
                        + "<testcase classname='A' name='skipped'><skipped/></testcase>"
                        + "</testsuite>");
        TestResult testResult = new TestResult();
        testResult.parse(report);
        testResult.tally();

        List<Widget.Line> lines = new Widget(testResult).getLines();

        assertEquals("1 test failed", lines.get(0).getSegments().get(0).getText());
        assertEquals(
                "1 passed, 1 skipped, 3 total",
                lines.get(1).getSegments().get(0).getText());
    }

    @Test
    @WithJenkins
    void showsRegressionsAndFixed(JenkinsRule rule) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new TouchBuilder());
        p.getPublishersList().add(new JUnitResultArchiver("x.xml"));
        FilePath report = rule.jenkins.getWorkspaceFor(p).child("x.xml");

        report.write(
                "<testsuite>"
                        + "<testcase classname='A' name='fixed'><failure/></testcase>"
                        + "<testcase classname='A' name='regressed'/>"
                        + "</testsuite>",
                null);
        rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));

        report.write(
                "<testsuite>"
                        + "<testcase classname='A' name='fixed'/>"
                        + "<testcase classname='A' name='regressed'><failure/></testcase>"
                        + "</testsuite>",
                null);
        TestResultAction action =
                rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0)).getAction(TestResultAction.class);

        List<Widget.Line.Segment> changes = action.getWidget().getLines().get(1).getSegments();

        assertEquals("1 regression", changes.get(0).getText());
        assertEquals("1 fixed", changes.get(1).getText());
    }
}
