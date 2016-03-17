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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import java.util.concurrent.TimeUnit;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.Page;

import static org.junit.Assert.*;

/**
 * User: Benjamin Shine bshine@yahoo-inc.com
 * Date: Dec 7, 2009
 * Time: 7:52:55 PM
 */
public class TestResultLinksTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    private FreeStyleProject project;
    private JUnitResultArchiver archiver;
    

    @Before
    public void setUp() throws Exception {
        project = rule.createFreeStyleProject("taqueria");
        archiver = new JUnitResultArchiver("*.xml");
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TouchBuilder());
    }

    @LocalData
    @Test
    public void testFailureLinks() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
        rule.assertBuildStatus(Result.UNSTABLE, build);

        TestResult theOverallTestResult =   build.getAction(TestResultAction.class).getResult();
        CaseResult theFailedTestCase = theOverallTestResult.getFailedTests().get(0);
        String relativePath = theFailedTestCase.getRelativePathFrom(theOverallTestResult);
        System.out.println("relative path seems to be: " + relativePath); 

        WebClient wc = rule.createWebClient();

        String testReportPageUrl =  project.getLastBuild().getUrl() + "/testReport";
        HtmlPage testReportPage = wc.goTo( testReportPageUrl );

        Page packagePage = testReportPage.getAnchorByText("tacoshack.meals").click();
        rule.assertGoodStatus(packagePage); // I expect this to work; just checking that my use of the APIs is correct.

        // Now we're on that page. We should be able to find a link to the failed test in there.
        HtmlAnchor anchor = testReportPage.getAnchorByText("tacoshack.meals.NachosTest.testBeanDip");
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
    public void testNonDescendantRelativePath() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.MINUTES); // leave time for interactive debugging
        rule.assertBuildStatus(Result.UNSTABLE, build);
        TestResult theOverallTestResult =   build.getAction(TestResultAction.class).getResult();
        CaseResult theFailedTestCase = theOverallTestResult.getFailedTests().get(0);
        String relativePath = theFailedTestCase.getRelativePathFrom(theOverallTestResult);
        System.out.println("relative path seems to be: " + relativePath);
        assertNotNull("relative path exists", relativePath);
        assertFalse("relative path doesn't start with a slash", relativePath.startsWith("/"));

        // Now ask for the relative path from the child to the parent -- we should get an absolute path
        String relativePath2 = theOverallTestResult.getRelativePathFrom(theFailedTestCase);
        System.out.println("relative path2 seems to be: " + relativePath2);
        // I know that in a HudsonTestCase we don't have a meaningful root url, so I expect an empty string here.
        // If somehow we start being able to produce a root url, then I'll also tolerate a url that starts with that.
        boolean pathIsEmptyOrNull = relativePath2 == null || relativePath2.isEmpty();
        boolean pathStartsWithRootUrl = !pathIsEmptyOrNull && relativePath2.startsWith(rule.jenkins.getRootUrl());
        assertTrue("relative path is empty OR begins with the app root", pathIsEmptyOrNull || pathStartsWithRootUrl ); 
    }
}
