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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * User: Benjamin Shine bshine@yahoo-inc.com
 * Date: Dec 7, 2009
 * Time: 7:52:55 PM
 */
@WithJenkins
class TestResultLinksTest {

    private FreeStyleProject project;
    private JUnitResultArchiver archiver;

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        this.rule = rule;
        project = rule.createFreeStyleProject("taqueria");
        archiver = new JUnitResultArchiver("*.xml");
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TouchBuilder());
    }

    @LocalData
    @Test
    void testFailureLinks() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
        rule.assertBuildStatus(Result.UNSTABLE, build);

        TestResult theOverallTestResult =
                build.getAction(TestResultAction.class).getResult();
        CaseResult theFailedTestCase = theOverallTestResult.getFailedTests().get(0);
        String relativePath = theFailedTestCase.getRelativePathFrom(theOverallTestResult);
        System.out.println("relative path seems to be: " + relativePath);

        JenkinsRule.WebClient wc = rule.createWebClient();

        String testReportPageUrl = project.getLastBuild().getUrl() + "/testReport";
        HtmlPage testReportPage = wc.goTo(testReportPageUrl);

        Page packagePage = testReportPage.getAnchorByText("tacoshack.meals").click();
        rule.assertGoodStatus(packagePage); // I expect this to work; just checking that my use of the APIs is correct.

        // Now we're on that page. We should be able to find a link to the failed test in there.
        HtmlAnchor anchor = testReportPage.getAnchorByText("testBeanDip tacoshack.meals.NachosTest");
        String href = anchor.getHrefAttribute();
        System.out.println("link is : " + href);
        Page failureFromLink = anchor.click();
        rule.assertGoodStatus(failureFromLink);

        // Now check the >>> link -- this is harder, because we can't do the javascript click handler properly
        // The summary page is just tack on /summary to the url for the test

    }

    // Exercises the b-is-not-a-descendant-of-a path.
    @LocalData
    @Test
    void testNonDescendantRelativePath() throws Exception {
        FreeStyleBuild build =
                project.scheduleBuild2(0).get(10, TimeUnit.MINUTES); // leave time for interactive debugging
        rule.assertBuildStatus(Result.UNSTABLE, build);
        TestResult theOverallTestResult =
                build.getAction(TestResultAction.class).getResult();
        CaseResult theFailedTestCase = theOverallTestResult.getFailedTests().get(0);
        String relativePath = theFailedTestCase.getRelativePathFrom(theOverallTestResult);
        System.out.println("relative path seems to be: " + relativePath);
        assertNotNull(relativePath, "relative path exists");
        assertFalse(relativePath.startsWith("/"), "relative path doesn't start with a slash");

        // Now ask for the relative path from the child to the parent -- we should get an absolute path
        String relativePath2 = theOverallTestResult.getRelativePathFrom(theFailedTestCase);
        System.out.println("relative path2 seems to be: " + relativePath2);
        // I know that in a HudsonTestCase we don't have a meaningful root url, so I expect an empty string here.
        // If somehow we start being able to produce a root url, then I'll also tolerate a url that starts with that.
        boolean pathIsEmptyOrNull = relativePath2 == null || relativePath2.isEmpty();
        boolean pathStartsWithRootUrl = !pathIsEmptyOrNull && relativePath2.startsWith(rule.jenkins.getRootUrl());
        assertTrue(pathIsEmptyOrNull || pathStartsWithRootUrl, "relative path is empty OR begins with the app root");
    }

    @Issue("JENKINS-31660")
    @Test
    void testPreviousBuildNotLoaded() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        File dataFile = TestResultTest.getDataFile("SKIPPED_MESSAGE/skippedTestResult.xml");
        testResult.parse(dataFile, null);
        FreeStyleBuild build = new FreeStyleBuild(project) {
            @Override
            public FreeStyleBuild getPreviousBuild() {
                fail("When no tests fail, we don't need tp load previous builds (expensive)");
                return null;
            }
        };
        testResult.freeze(new TestResultAction(build, testResult, null));
    }

    @Test
    void testFailedSinceAfterSkip() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        File dataFile = TestResultTest.getDataFile("SKIPPED_MESSAGE/skippedTestResult.xml");
        testResult.parse(dataFile, null);
        FreeStyleBuild build = new FreeStyleBuild(project) {
            @Override
            public FreeStyleBuild getPreviousBuild() {
                return null;
            }
        };
        build.addAction(new TestResultAction(build, testResult, null));
        TestResult testResult2 = new TestResult();
        File dataFile2 = TestResultTest.getDataFile("SKIPPED_MESSAGE/afterSkippedResult.xml");
        testResult2.parse(dataFile2, null);
        FreeStyleBuild build2 = new FreeStyleBuild(project) {
            @Override
            public FreeStyleBuild getPreviousBuild() {
                return build;
            }
        };

        testResult2.freeze(new TestResultAction(build2, testResult, null));
        assertEquals(2, testResult2.getFailedTests().get(0).getFailedSince());
    }
}
