package hudson.tasks.junit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.junit.rot13.Rot13Publisher;
import hudson.tasks.test.helper.WebClientFactory;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTable;
import org.htmlunit.html.HtmlTableCell;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * Verifies that TestDataPublishers can contribute custom columns to html tables in result pages.
 */
public class CustomColumnsTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private FreeStyleProject project;

    private static final String reportFileName = "junit-report-494.xml";

    @Before
    public void setUp() throws Exception {
        project = jenkins.createFreeStyleProject("customcolumns");
        JUnitResultArchiver archiver = new JUnitResultArchiver("*.xml");
        archiver.setTestDataPublishers(Collections.singletonList(new Rot13Publisher()));
        archiver.setSkipPublishingChecks(true);
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws InterruptedException, IOException {
                build.getWorkspace().child(reportFileName).copyFrom(getClass().getResource(reportFileName));
                return true;
            }
        });
        FreeStyleBuild build = project.scheduleBuild2(0).get(5, TimeUnit.MINUTES);
        jenkins.assertBuildStatus(Result.UNSTABLE, build);
    }

    @SafeVarargs
    private void verifyThatTableContainsExpectedValues(String pathToPage, String tableId, String headerName,
                                                       Pair<String, String>... rowValues) throws Exception {

        JenkinsRule.WebClient wc = WebClientFactory.createWebClientWithDisabledJavaScript(jenkins);
        HtmlPage projectPage = wc.getPage(project);
        jenkins.assertGoodStatus(projectPage);
        HtmlPage classReportPage = wc.getPage(project, pathToPage);
        jenkins.assertGoodStatus(classReportPage);

        HtmlTable testResultTable = (HtmlTable) classReportPage.getFirstByXPath("//table[@id='" + tableId + "']");
        List<HtmlTableCell> headerRowCells = testResultTable.getHeader().getRows().get(0).getCells();
        int numberOfColumns = headerRowCells.size();
        assertEquals(headerName, headerRowCells.get(numberOfColumns - 1).asNormalizedText());

        for (int x = 0; x < rowValues.length; x++) {
            List<HtmlTableCell> bodyRowCells = testResultTable.getBodies().get(0).getRows().get(x).getCells();
            assertThat(bodyRowCells.get(0).asNormalizedText(), CoreMatchers.containsString(rowValues[x].getLeft()));
            assertEquals(rowValues[x].getRight(), bodyRowCells.get(numberOfColumns - 1).asNormalizedText());
        }
    }

    @Test
    public void verifyThatCustomColumnIsAddedToTheTestsTableOnTheClassResultPage() throws Exception {
        verifyThatTableContainsExpectedValues("/lastBuild/testReport/junit/io.jenkins.example/AnExampleTestClass/",
                "testresult", "ROT13 for cases on class page", Pair.of("testCaseA", "grfgPnfrN for case"), Pair.of(
                        "testCaseZ", "grfgPnfrM for case"));
    }

    @Test
    public void verifyThatCustomColumnIsAddedToTheFailedTestsTableOnThePackageResultPage() throws Exception {
        verifyThatTableContainsExpectedValues("/lastBuild/testReport/junit/io.jenkins.example/", "failedtestresult",
                "ROT13 for failed cases on package page", Pair.of("testCaseA", "grfgPnfrN for case"));
    }

    @Test
    public void verifyThatCustomColumnIsAddedToTheClassesTableOnThePackageResultPage() throws Exception {
        verifyThatTableContainsExpectedValues("/lastBuild/testReport/junit/io.jenkins.example/", "testresult",
                "ROT13 for all classes on package page", Pair.of("AnExampleTestClass", "NaRknzcyrGrfgPynff for class"));
    }

    @Test
    public void verifyThatCustomColumnIsAddedToTheFailedTestsTableOnTheTestResultPage() throws Exception {
        verifyThatTableContainsExpectedValues("/lastBuild/testReport/", "failedtestresult",
                "ROT13 for failed cases on test page", Pair.of("testCaseA", "grfgPnfrN for case"));
    }

    @Test
    public void verifyThatCustomColumnIsAddedToTheClassesTableOnTheTestResultPage() throws Exception {
        verifyThatTableContainsExpectedValues("/lastBuild/testReport/", "testresult",
                "ROT13 for all packages on test page", Pair.of("io.jenkins.example", "vb.wraxvaf.rknzcyr for package"));
    }
}
