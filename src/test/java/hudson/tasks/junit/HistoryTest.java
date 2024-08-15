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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Result;
import java.util.List;
import java.util.Optional;
import org.htmlunit.AlertHandler;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTable;
import org.htmlunit.html.HtmlTableCell;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class HistoryTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    private FreeStyleProject project;

    private static final String PROJECT_NAME = "wonky";

    @Before
    public void setUp() throws Exception {
        List<FreeStyleProject> projects = rule.jenkins.getAllItems(FreeStyleProject.class);
        Project theProject = null;
        for (Project p : projects) {
            if (p.getName().equals(PROJECT_NAME)) {
                theProject = p;
            }
        }
        assertNotNull("We should have a project named " + PROJECT_NAME, theProject);
        project = (FreeStyleProject) theProject;
    }

    @LocalData
    @Test
    public void testFailedSince() throws Exception {
        assertNotNull("project should exist", project);

        // Check the status of a few builds
        FreeStyleBuild build4 = project.getBuildByNumber(4);
        assertNotNull("build4", build4);
        rule.assertBuildStatus(Result.FAILURE, build4);

        FreeStyleBuild build7 = project.getBuildByNumber(7);
        assertNotNull("build7", build7);
        rule.assertBuildStatus(Result.SUCCESS, build7);

        TestResult tr = build4.getAction(TestResultAction.class).getResult();
        assertEquals(2, tr.getFailedTests().size());

        // In build 4, we expect these tests to have failed since these builds
        // org.jvnet.hudson.examples.small.deep.DeepTest.testScubaGear failed since 3
        // org.jvnet.hudson.examples.small.MiscTest.testEleanor failed since 3

        PackageResult deepPackage = tr.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is failed", !deepPackage.isPassed());
        ClassResult deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is failed", !deepClass.isPassed());
        CaseResult scubaCase = deepClass.getCaseResult("testScubaGear");
        assertNotNull(scubaCase);
        assertTrue("scubaCase case is failed", !scubaCase.isPassed());
        int scubaFailedSince = scubaCase.getFailedSince();
        assertEquals("scubaCase should have failed since build 3", 3, scubaFailedSince);

        // In build 5 the scuba test begins to pass
        TestResult tr5 =
                project.getBuildByNumber(5).getAction(TestResultAction.class).getResult();
        assertEquals(1, tr5.getFailedTests().size());
        deepPackage = tr5.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is passed", deepPackage.isPassed());
        deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is passed", deepClass.isPassed());
        scubaCase = deepClass.getCaseResult("testScubaGear");
        assertNotNull(scubaCase);
        assertTrue("scubaCase case is passed", scubaCase.isPassed());

        // In build5, testEleanor has been failing since build 3
        PackageResult smallPackage = tr5.byPackage("org.jvnet.hudson.examples.small");
        ClassResult miscClass = smallPackage.getClassResult("MiscTest");
        CaseResult eleanorCase = miscClass.getCaseResult("testEleanor");
        assertTrue("eleanor failed", !eleanorCase.isPassed());
        assertEquals("eleanor has failed since build 3", 3, eleanorCase.getFailedSince());
    }

    @LocalData
    @Test
    @Issue("SECURITY-2760")
    public void testXSS() throws Exception {
        assertNotNull("project should exist", project);

        FreeStyleBuild build4 = project.getBuildByNumber(4);
        TestResult tr = build4.getAction(TestResultAction.class).getResult();

        tr.setDescription("<script>alert(\"<XSS>\")</script>");
        build4.save(); // Might be unnecessary

        try (final JenkinsRule.WebClient webClient = rule.createWebClient()) {
            Alerter alerter = new Alerter();
            webClient.setJavaScriptEnabled(true);
            webClient
                    .getOptions()
                    .setThrowExceptionOnScriptError(false); // HtmlUnit finds a syntax error in bootstrap 5
            webClient.setAlertHandler(alerter); // This catches any alert dialog popup

            final HtmlPage page = webClient.getPage(build4, "testReport/history/");
            assertNull(alerter.message); // No alert dialog popped up
            assertNull(alerter.page);
            final HtmlTable table = (HtmlTable) page.getElementById("testresult");
            final Optional<HtmlTableCell> descr = table.getRows().stream()
                    .flatMap(row -> row.getCells().stream())
                    .filter(cell -> cell.getTextContent()
                            .equals("<script>alert(\"<XSS>\")</script>")) // cell.getTextContent() seems to
                    // translate back from &gt; to < etc.
                    .findFirst();
            assertTrue("Should have found the description", descr.isPresent());
        }
    }

    static class Alerter implements AlertHandler {

        Page page = null;
        String message = null;

        @Override
        public void handleAlert(final Page page, final String message) {
            this.page = page;
            this.message = message;
        }
    }
}
