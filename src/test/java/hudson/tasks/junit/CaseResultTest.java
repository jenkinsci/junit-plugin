/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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

import hudson.model.FreeStyleProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.FreeStyleBuild;
import hudson.Launcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestBuilder;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class CaseResultTest {
//    /**
//     * Verifies that Hudson can capture the stdout/stderr output from Maven surefire.
//     */
//    public void testSurefireOutput() throws Exception {
//        setJavaNetCredential();
//        configureDefaultMaven();
//
//        MavenModuleSet p = createMavenProject();
//        p.setScm(new SubversionSCM(".../hudson/test-projects/junit-failure@16411"));
//        MavenModuleSetBuild b = assertBuildStatus(UNSTABLE,p.scheduleBuild2(0).get());
//        AbstractTestResultAction<?> t = b.getAction(AbstractTestResultAction.class);
//        assertSame(1,t.getFailCount());
//        CaseResult tc = t.getFailedTests().get(0);
//        assertTrue(tc.getStderr().contains("stderr"));
//        assertTrue(tc.getStdout().contains("stdout"));
//    }

    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @Email("http://www.nabble.com/NPE-%28Fatal%3A-Null%29-in-recording-junit-test-results-td23562964.html")
    @Test
    public void testIssue20090516() throws Exception {
        FreeStyleBuild b = configureTestBuild(null);
        TestResult tr = b.getAction(TestResultAction.class).getResult();
        assertEquals(3,tr.getFailedTests().size());
        CaseResult cr = tr.getFailedTests().get(0);
        assertEquals("org.twia.vendor.VendorManagerTest",cr.getClassName());
        assertEquals("testGetVendorFirmKeyForVendorRep",cr.getName());

        // piggy back tests for annotate methods
        assertOutput(cr,"plain text", "plain text");
        assertOutput(cr,"line #1\nhttp://nowhere.net/\nline #2\n",
                "line #1\n<a href=\"http://nowhere.net/\">http://nowhere.net/</a>\nline #2\n");
        assertOutput(cr,"failed; see http://nowhere.net/",
                "failed; see <a href=\"http://nowhere.net/\">http://nowhere.net/</a>");
        assertOutput(cr,"failed (see http://nowhere.net/)",
                "failed (see <a href=\"http://nowhere.net/\">http://nowhere.net/</a>)");
        assertOutput(cr,"http://nowhere.net/ - failed: http://elsewhere.net/",
                "<a href=\"http://nowhere.net/\">http://nowhere.net/</a> - failed: " +
                "<a href=\"http://elsewhere.net/\">http://elsewhere.net/</a>");
        assertOutput(cr,"https://nowhere.net/",
                "<a href=\"https://nowhere.net/\">https://nowhere.net/</a>");
        assertOutput(cr,"stuffhttp://nowhere.net/", "stuffhttp://nowhere.net/");
        assertOutput(cr,"a < b && c < d", "a &lt; b &amp;&amp; c &lt; d");
        assertOutput(cr,"see <http://nowhere.net/>",
                "see &lt;<a href=\"http://nowhere.net/\">http://nowhere.net/</a>>");
        assertOutput(cr,"http://google.com/?q=stuff&lang=en",
                "<a href=\"http://google.com/?q=stuff&amp;lang=en\">http://google.com/?q=stuff&amp;lang=en</a>");
        assertOutput(cr,"http://localhost:8080/stuff/",
                "<a href=\"http://localhost:8080/stuff/\">http://localhost:8080/stuff/</a>");
    }


    /**
     * Verifies that the error message and stacktrace from a failed junit test actually render properly.
     */
    @Issue("JENKINS-4257")
    @Test
    public void testFreestyleErrorMsgAndStacktraceRender() throws Exception {
        FreeStyleBuild b = configureTestBuild("render-test");
        TestResult tr = b.getAction(TestResultAction.class).getResult();
        assertEquals(3,tr.getFailedTests().size());
        CaseResult cr = tr.getFailedTests().get(1);
        assertEquals("org.twia.vendor.VendorManagerTest",cr.getClassName());
        assertEquals("testGetRevokedClaimsForAdjustingFirm",cr.getName());
	assertNotNull("Error details should not be null", cr.getErrorDetails());
	assertNotNull("Error stacktrace should not be null", cr.getErrorStackTrace());

	String testUrl = cr.getRelativePathFrom(tr);
	
	HtmlPage page = rule.createWebClient().goTo("job/render-test/1/testReport/" + testUrl);

	HtmlElement errorMsg = (HtmlElement) page.getByXPath("//h3[text()='Error Message']/following-sibling::*").get(0);

	assertEquals(cr.annotate(cr.getErrorDetails()).replaceAll("&lt;", "<"), errorMsg.getTextContent());
	HtmlElement errorStackTrace = (HtmlElement) page.getByXPath("//h3[text()='Stacktrace']/following-sibling::*").get(0);
	// Have to do some annoying replacing here to get the same text Jelly produces in the end.
	assertEquals(cr.annotate(cr.getErrorStackTrace()).replaceAll("&lt;", "<").replace("\r\n", "\n"),
		     errorStackTrace.getTextContent());
    }
    
    /**
     * Verify fields show up at the correct visibility in the remote API
     */

    private static final String[] MAX_VISIBILITY_FIELDS = { "name" };
    private static final String[] REDUCED_VISIBILITY_FIELDS = { "stdout", "stderr", "errorStackTrace", "errorDetails" };
    private static final String[] OTHER_FIELDS = { "duration", "className", "failedSince", "age", "skipped", "status" };

    @Email("http://jenkins.361315.n4.nabble.com/Change-remote-API-visibility-for-CaseResult-getStdout-getStderr-td395102.html")
    @Test
    public void testRemoteApiDefaultVisibility() throws Exception {
        FreeStyleBuild b = configureTestBuild("test-remoteapi");

        XmlPage page = (XmlPage)rule.createWebClient().goTo("job/test-remoteapi/1/testReport/org.twia.vendor/VendorManagerTest/testCreateAdjustingFirm/api/xml","application/xml");

        int found = 0;

        found = page.getByXPath(composeXPath(MAX_VISIBILITY_FIELDS)).size();
        assertTrue("Should have found an element, but found " + found, found > 0);

        found = page.getByXPath(composeXPath(REDUCED_VISIBILITY_FIELDS)).size();
        assertTrue("Should have found an element, but found " + found, found > 0);

        found = page.getByXPath(composeXPath(OTHER_FIELDS)).size();
        assertTrue("Should have found an element, but found " + found, found > 0);
    }
    
    @Email("http://jenkins.361315.n4.nabble.com/Change-remote-API-visibility-for-CaseResult-getStdout-getStderr-td395102.html")
    @Test
    public void testRemoteApiNoDetails() throws Exception {
        FreeStyleBuild b = configureTestBuild("test-remoteapi");

        XmlPage page = (XmlPage)rule.createWebClient().goTo("job/test-remoteapi/1/testReport/org.twia.vendor/VendorManagerTest/testCreateAdjustingFirm/api/xml?depth=-1","application/xml");

        int found = 0;

        found = page.getByXPath(composeXPath(MAX_VISIBILITY_FIELDS)).size();
        assertTrue("Should have found an element, but found " + found, found > 0);

        found = page.getByXPath(composeXPath(REDUCED_VISIBILITY_FIELDS)).size();
        assertTrue("Should have found 0 elements, but found " + found, found == 0);

        found = page.getByXPath(composeXPath(OTHER_FIELDS)).size();
        assertTrue("Should have found an element, but found " + found, found > 0);
   }
    
    @Email("http://jenkins.361315.n4.nabble.com/Change-remote-API-visibility-for-CaseResult-getStdout-getStderr-td395102.html")
    @Test
    public void testRemoteApiNameOnly() throws Exception {
        FreeStyleBuild b = configureTestBuild("test-remoteapi");

        XmlPage page = (XmlPage)rule.createWebClient().goTo("job/test-remoteapi/1/testReport/org.twia.vendor/VendorManagerTest/testCreateAdjustingFirm/api/xml?depth=-10","application/xml");

        int found = 0;

        found = page.getByXPath(composeXPath(MAX_VISIBILITY_FIELDS)).size();
        assertTrue("Should have found an element, but found " + found, found > 0);

        found = page.getByXPath(composeXPath(REDUCED_VISIBILITY_FIELDS)).size();
        assertTrue("Should have found 0 elements, but found " + found, found == 0);

        found = page.getByXPath(composeXPath(OTHER_FIELDS)).size();
        assertTrue("Should have found 0 elements, but found " + found, found == 0);
    }

    /**
     * Makes sure the summary page remains text/plain (see commit 7089a81 in JENKINS-1544) but
     * the index page must be in text/html.
     */
    @Issue("JENKINS-21261")
    @Test
    public void testContentType() throws Exception {
        configureTestBuild("foo");
        WebClient wc = rule.createWebClient();
        wc.goTo("job/foo/1/testReport/org.twia.vendor/VendorManagerTest/testCreateAdjustingFirm/","text/html");

        wc.goTo("job/foo/1/testReport/org.twia.vendor/VendorManagerTest/testCreateAdjustingFirm/summary","text/plain");
    }

    /**
    * Execute twice a failing test and make sure its failing age is 2
    */
    @Issue("JENKINS-30413")
    @Test
    public void testAge() throws Exception {
        String projectName = "tr-age-test";
        String testResultResourceFile = "JENKINS-30413.xml";

        //Create a job:
        FreeStyleProject p = rule.createFreeStyleProject(projectName);
        p.getPublishersList().add(new JUnitResultArchiver("*.xml"));

        //First build execution:
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("junit.xml").copyFrom(
                    getClass().getResource(testResultResourceFile));
                return true;
            }
        });
        FreeStyleBuild b1 = rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());

        //First build result analysis:
        TestResult tr = b1.getAction(TestResultAction.class).getResult();
        assertEquals(1,tr.getFailedTests().size());
        CaseResult cr = tr.getFailedTests().get(0);
        assertEquals(1,cr.getAge()); //First execution, failing test age is expected to be 1





        //Second build execution:
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("junit.xml").copyFrom(
                    getClass().getResource(testResultResourceFile));
                return true;
            }
        });
        FreeStyleBuild b2 = rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());

        //Second build result analysis:
        TestResult tr2 = b2.getAction(TestResultAction.class).getResult();
        assertEquals(1,tr2.getFailedTests().size());
        CaseResult cr2 = tr2.getFailedTests().get(0);
        assertEquals(2,cr2.getAge()); //At second execution, failing test age should be 2
    }

    private FreeStyleBuild configureTestBuild(String projectName) throws Exception {
        FreeStyleProject p = projectName == null ? rule.createFreeStyleProject() : rule.createFreeStyleProject(projectName);
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("junit.xml").copyFrom(
                    getClass().getResource("junit-report-20090516.xml"));
                return true;
            }
        });
        p.getPublishersList().add(new JUnitResultArchiver("*.xml"));
        return rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
    }

    private String composeXPath(String[] fields) throws Exception {
        StringBuilder tmp = new StringBuilder(100);
        for ( String f : fields ) {
            if (tmp.length() > 0 ) {
                tmp.append("|");
            }
            tmp.append("//caseResult/");
            tmp.append(f);
        }

        return tmp.toString();
    }
    
    private void assertOutput(CaseResult cr, String in, String out) throws Exception {
        assertEquals(out, cr.annotate(in));
    }

}
