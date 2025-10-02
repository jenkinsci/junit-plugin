/*
 * The MIT License
 *
 * Copyright (c) 2009, Yahoo!, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks.junit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.slaves.DumbSlave;
import hudson.tasks.Builder;
import hudson.tasks.test.helper.WebClientFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.htmlunit.Page;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTable;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

@WithJenkins
class TestResultPublishingTest {

    private FreeStyleProject project;
    private JUnitResultArchiver archiver;
    private final String BASIC_TEST_PROJECT = "percival";
    private final String TEST_PROJECT_WITH_HISTORY = "wonky";

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        this.rule = rule;
        project = rule.createFreeStyleProject(BASIC_TEST_PROJECT);
        project.getBuildersList().add(new TouchBuilder());
        archiver = new JUnitResultArchiver("*.xml");
        archiver.setSkipOldReports(true);
        project.getPublishersList().add(archiver);
    }

    @LocalData
    @Test
    void testBasic() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(30, TimeUnit.SECONDS);

        assertTestResults(build);

        JenkinsRule.WebClient wc = WebClientFactory.createWebClientWithDisabledJavaScript(rule);
        wc.getPage(build, "testReport"); // test report
        wc.getPage(build, "testReport/hudson.security"); // package
        wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/"); // class
        wc.getPage(
                build,
                "testReport/hudson.security/HudsonPrivateSecurityRealmTest/testDataCompatibilityWith1_282/"); // method
    }

    @LocalData
    @Test
    void testSlave() throws Exception {
        DumbSlave s = rule.createOnlineSlave();
        project.setAssignedLabel(s.getSelfLabel());

        FilePath src = new FilePath(rule.jenkins.getRootPath(), "jobs/" + BASIC_TEST_PROJECT + "/workspace/");
        assertNotNull(src);
        FilePath dest = s.getWorkspaceFor(project);
        assertNotNull(dest);
        src.copyRecursiveTo("*.xml", dest);

        testBasic();
    }

    /**
     * Verify that we can successfully parse and display test results in the
     * open junit test result publishing toolchain. Ensure that we meet this
     * compatibility requirement:
     * From users' point of view, Hudson core JUnit should continue
     * to work as if nothing has changed
     * - Old testReport URLs should still work
     */
    @LocalData
    @Test
    void testOpenJUnitPublishing() throws IOException, SAXException {
        Project proj = (Project) rule.jenkins.getItem(TEST_PROJECT_WITH_HISTORY);
        assertNotNull(proj, "We should have a project named " + TEST_PROJECT_WITH_HISTORY);

        // Validate that there are test results where I expect them to be:
        JenkinsRule.WebClient wc = WebClientFactory.createWebClientWithDisabledJavaScript(rule);

        // On the project page:
        HtmlPage projectPage = wc.getPage(proj);
        //      we should have a link that reads "Latest Test Result"
        //      that link should go to http://localhost:8080/job/breakable/lastBuild/testReport/
        rule.assertXPath(projectPage, "//a[@href='lastCompletedBuild/testReport/']");
        rule.assertXPathValue(projectPage, "//a[@href='lastCompletedBuild/testReport/']", "Latest Test Result");
        rule.assertXPathValueContains(projectPage, "//a[@href='lastCompletedBuild/testReport/']", "Latest Test Result");
        //      after "Latest Test Result" it should say "no failures"
        rule.assertXPathResultsContainText(projectPage, "//td", "(no failures)");
        //      there should be a test result trend graph
        HtmlElement trendGraphCaption = (HtmlElement)
                projectPage.getByXPath("//div[@class='test-trend-caption']").get(0);
        assertThat(trendGraphCaption.getTextContent(), is("Test Result Trend"));
        HtmlElement testCanvas =
                (HtmlElement) trendGraphCaption.getNextSibling().getFirstChild();
        assertTrue(testCanvas.getAttribute("class").contains("echarts-trend"), "couldn't find test result trend graph");

        XmlPage xmlProjectPage = wc.goToXml(proj.getUrl() + "/lastBuild/testReport/api/xml");
        rule.assertXPath(xmlProjectPage, "/testResult");
        rule.assertXPath(xmlProjectPage, "/testResult/suite");
        rule.assertXPath(xmlProjectPage, "/testResult/failCount");
        rule.assertXPathValue(xmlProjectPage, "/testResult/failCount", "0");
        rule.assertXPathValue(xmlProjectPage, "/testResult/passCount", "4");
        rule.assertXPathValue(xmlProjectPage, "/testResult/skipCount", "0");
        String[] packages = {
            "org.jvnet.hudson.examples.small.AppTest",
            "org.jvnet.hudson.examples.small.MiscTest",
            "org.jvnet.hudson.examples.small.deep.DeepTest"
        };
        for (String packageName : packages) {
            rule.assertXPath(xmlProjectPage, "/testResult/suite/case/className[text()='" + packageName + "']");
        }

        // Go to a page that we know has a failure
        HtmlPage buildPage = wc.getPage(proj.getBuildByNumber(3));
        rule.assertGoodStatus(buildPage);
        // We expect to see one failure, for com.yahoo.breakable.misc.UglyTest.becomeUglier
        // which should link to
        // http://localhost:8080/job/wonky/3/testReport/org.jvnet.hudson.examples.small/MiscTest/testEleanor/
        rule.assertXPathResultsContainText(buildPage, "//a", "org.jvnet.hudson.examples.small.MiscTest.testEleanor");
        HtmlAnchor failingTestLink = buildPage.getAnchorByText("org.jvnet.hudson.examples.small.MiscTest.testEleanor");
        assertNotNull(failingTestLink);
        Page failingTestPage = failingTestLink.click();
        rule.assertGoodStatus(failingTestPage);

        // Go to the xml page for a build we know has failures
        XmlPage xmlBuildPage = wc.goToXml(proj.getBuildByNumber(3).getUrl() + "/api/xml");
        rule.assertXPathValue(xmlBuildPage, "//failCount", "2");
        rule.assertXPathValue(xmlBuildPage, "//skipCount", "0");
        rule.assertXPathValue(xmlBuildPage, "//totalCount", "4");
        rule.assertXPathValue(xmlBuildPage, "//result", "FAILURE");

        // Check overall test result counts
        XmlPage xmlTestReportPage = wc.goToXml(proj.getBuildByNumber(3).getUrl() + "/testReport/api/xml");
        rule.assertXPathValue(xmlTestReportPage, "/testResult/failCount", "2");
        rule.assertXPathValue(xmlTestReportPage, "/testResult/passCount", "2");
        rule.assertXPathValue(xmlTestReportPage, "/testResult/skipCount", "0");

        // Make sure the right tests passed and failed
        rule.assertXPathValue(
                xmlTestReportPage,
                "/testResult/suite/case[className/text()='org.jvnet.hudson.examples.small.AppTest']/status",
                "PASSED");
        rule.assertXPathValue(xmlTestReportPage, "/testResult/suite/case[name/text()='testEleanor']/status", "FAILED");

        // TODO: implement more of these tests
        // On the lastBuild/testReport page:
        //      Breadcrumbs should read #6 > Test Result  where Test Result is a link to this page
        //      inside of div id="main-panel" we should find the text "0 failures (-1)"
        //      we should have a blue bar which is blue all the way across: div style="width: 100%; height: 1em;
        // background-color: rgb(114, 159, 207);
        //      we should find the words "7 tests (?0)"
        //      we should find the words "All Tests"
        //      we should find a table

        //      Inside that table, there should be the following rows:
        //           org.jvnet.hudson.examples.small 	0ms   0 -1 0   3
        //          org.jvnet.hudson.examples.small.deep 	4ms 0 0 0  1
        Run theRun = proj.getBuildByNumber(7);
        assertTestResultsAsExpected(
                wc,
                theRun,
                "/testReport",
                "org.jvnet.hudson.examples.small",
                "0 ms",
                "SUCCESS",
                /* total tests expected, diff */ 3,
                0,
                /* fail count expected, diff */ 0,
                -1,
                /* skip count expected, diff */ 0,
                0);

        assertTestResultsAsExpected(
                wc,
                theRun,
                "/testReport",
                "org.jvnet.hudson.examples.small.deep",
                "4 ms",
                "SUCCESS",
                /* total tests expected, diff */ 1,
                0,
                /* fail count expected, diff */ 0,
                0,
                /* skip count expected, diff */ 0,
                0);

        // TODO: more, more, more.
        // TODO: test report history by package

    }

    /**
     * Test to demonstrate bug HUDSON-5246, inter-build diffs for junit test results are wrong
     */
    @Issue("JENKINS-5246")
    @LocalData
    @Test
    void testInterBuildDiffs() throws IOException, SAXException {
        Project proj = (Project) rule.jenkins.getItem(TEST_PROJECT_WITH_HISTORY);
        assertNotNull(proj, "We should have a project named " + TEST_PROJECT_WITH_HISTORY);

        // Validate that there are test results where I expect them to be:
        JenkinsRule.WebClient wc = WebClientFactory.createWebClientWithDisabledJavaScript(rule);
        Run theRun = proj.getBuildByNumber(4);
        assertTestResultsAsExpected(
                wc,
                theRun,
                "/testReport",
                "org.jvnet.hudson.examples.small",
                "12 ms",
                "FAILURE",
                /* total tests expected, diff */ 3,
                0,
                /* fail count expected, diff */ 1,
                0,
                /* skip count expected, diff */ 0,
                0);
    }

    /**
     * Make sure the open junit publisher shows junit history
     * @throws IOException
     * @throws SAXException
     */
    @LocalData
    @Test
    void testHistoryPageOpenJunit() throws IOException, SAXException {
        Project proj = (Project) rule.jenkins.getItem(TEST_PROJECT_WITH_HISTORY);
        assertNotNull(proj, "We should have a project named " + TEST_PROJECT_WITH_HISTORY);

        // Validate that there are test results where I expect them to be:
        JenkinsRule.WebClient wc = WebClientFactory.createWebClientWithDisabledJavaScript(rule);

        HtmlPage historyPage = wc.getPage(proj.getBuildByNumber(7), "/testReport/history/");
        rule.assertGoodStatus(historyPage);
        HtmlElement historyCard =
                (HtmlElement) historyPage.getByXPath("//div[@class='card ']").get(0);
        assertThat(historyCard.getTextContent(), containsString("History"));
        DomElement wholeTable = historyPage.getElementById("testresult");
        assertNotNull(wholeTable, "table with id 'testresult' exists");
        assertInstanceOf(HtmlTable.class, wholeTable, "wholeTable is a table");
        HtmlTable table = (HtmlTable) wholeTable;

        // We really want to call table.getRowCount(), but
        // it returns 1, not the real answer,
        // because this table has *two* tbody elements,
        // and getRowCount() only seems to count the *first* tbody.
        // Maybe HtmlUnit can't handle the two tbody's. In any case,
        // the tableText.contains tests do a (ahem) passable job
        // of detecting whether the history results are present.

        String tableText = table.getTextContent();
        assertTrue(tableText.contains(TEST_PROJECT_WITH_HISTORY), "Table text is missing the project name");
        assertTrue(tableText.contains("7"), "Table text is missing the build number");
        assertTrue(tableText.contains("4 ms"), "Table text is missing the test duration");
    }

    @Issue("JENKINS-19186")
    @Test
    void testBrokenResultFile() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder());
        p.getBuildersList().add(new TouchBuilder());
        p.getPublishersList().add(new JUnitResultArchiver("TEST-foo.xml", false, null));
        rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
    }

    private static final class TestBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            build.getWorkspace().child("TEST-foo.xml").write("<bogus>", null);
            return true;
        }
    }

    void assertStringEmptyOrNull(String msg, String str) {
        if (str == null) {
            return;
        }
        if (str.isEmpty()) {
            return;
        }
        fail(msg + "(should be empty or null) : '" + str + "'");
    }

    void assertPaneDiffText(String msg, int expectedValue, Object paneObj) {
        assertInstanceOf(HtmlElement.class, paneObj, "paneObj should be an HtmlElement, it was " + paneObj.getClass());
        String paneText = ((HtmlElement) paneObj).asNormalizedText();
        if (expectedValue == 0) {
            assertStringEmptyOrNull(msg, paneText);
        } else {
            String expectedString = (expectedValue >= 1 ? "+" : "-") + Math.abs(expectedValue);
            assertEquals(expectedString, paneText, msg);
        }
    }

    void assertTestResultsAsExpected(
            JenkinsRule.WebClient wc,
            Run run,
            String restOfUrl,
            String packageName,
            String expectedResult,
            String expectedDurationStr,
            int expectedTotalTests,
            int expectedTotalDiff,
            int expectedFailCount,
            int expectedFailDiff,
            int expectedSkipCount,
            int expectedSkipDiff)
            throws IOException, SAXException {

        // TODO: verify expectedResult
        // TODO: verify expectedDuration

        XmlPage xmlPage = wc.goToXml(run.getUrl() + restOfUrl + "/" + packageName + "/api/xml");
        int expectedPassCount = expectedTotalTests - expectedFailCount - expectedSkipCount;
        // Verify xml results
        rule.assertXPathValue(xmlPage, "/packageResult/failCount", Integer.toString(expectedFailCount));
        rule.assertXPathValue(xmlPage, "/packageResult/skipCount", Integer.toString(expectedSkipCount));
        rule.assertXPathValue(xmlPage, "/packageResult/passCount", Integer.toString(expectedPassCount));
        rule.assertXPathValue(xmlPage, "/packageResult/name", packageName);

        // TODO: verify html results
        HtmlPage testResultPage = wc.getPage(run, restOfUrl);

        // Verify inter-build diffs in html table
        String xpathToFailDiff =
                "//table[@id='testresult']//tr[td//span[text()=\"" + packageName + "\"]]/td[2]/span[last()]";
        String xpathToSkipDiff =
                "//table[@id='testresult']//tr[td//span[text()=\"" + packageName + "\"]]/td[3]/span[last()]";
        String xpathToTotalDiff =
                "//table[@id='testresult']//tr[td//span[text()=\"" + packageName + "\"]]/td[5]/span[last()]";

        Object totalDiffObj = testResultPage.getFirstByXPath(xpathToTotalDiff);
        assertPaneDiffText("total diff", expectedTotalDiff, totalDiffObj);

        Object failDiffObj = testResultPage.getFirstByXPath(xpathToFailDiff);
        assertPaneDiffText("failure diff", expectedFailDiff, failDiffObj);

        Object skipDiffObj = testResultPage.getFirstByXPath(xpathToSkipDiff);
        assertPaneDiffText("skip diff", expectedSkipDiff, skipDiffObj);

        // TODO: The link in the table for each of the three packages in the testReport table should link to a
        // by-package page,
        // TODO: for example, http://localhost:8080/job/breakable/lastBuild/testReport/com.yahoo.breakable.misc/

    }

    // TODO: Make sure that we meet this compatibility requirement:
    // TODO: From users' point of view, Open Source  *Unit publishers should continue to work as if nothing has changed
    // TODO: * Old testReport URLs should still work

    private void assertTestResults(FreeStyleBuild build) {
        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertNotNull(testResultAction, "no TestResultAction");

        TestResult result = testResultAction.getResult();
        assertNotNull(result, "no TestResult");

        assertEquals(1, testResultAction.getFailCount(), "should have 1 failing test");
        assertEquals(1, result.getFailCount(), "should have 1 failing test");

        assertEquals(132, testResultAction.getTotalCount(), "should have 132 total tests");
        assertEquals(132, result.getTotalCount(), "should have 132 total tests");
    }
}
