/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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
package hudson.tasks.test;

import com.gargoylesoftware.htmlunit.AlertHandler;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.junit.JUnitResultArchiver;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * Simple test for UI changes to the test reports
 *
 * @author kzantow
 */
public class TestReportUiTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Validate CSS styles present to prevent duration text from wrapping
     */
    @Issue("JENKINS-24352")
    @Test
    public void testDurationStyle() throws Exception {
        AbstractBuild b = configureTestBuild("render-test");

        JenkinsRule.WebClient wc = j.createWebClient();

        wc.setAlertHandler(new AlertHandler() {
            @Override
            public void handleAlert(Page page, String message) {
                throw new AssertionError();
            }
        });

        HtmlPage pg = wc.getPage(b, "testReport");

        // these are from the test result file:
        String duration14sec = Util.getTimeSpanString((long) (14.398 * 1000));
        String duration3_3sec = Util.getTimeSpanString((long) (3.377 * 1000));
        String duration2_5sec = Util.getTimeSpanString((long) (2.502 * 1000));

        Assert.assertNotNull(pg.getFirstByXPath("//td[contains(text(),'" + duration3_3sec + "')][contains(@class,'no-wrap')]"));

        pg = wc.getPage(b, "testReport/org.twia.vendor");

        Assert.assertNotNull(pg.getFirstByXPath("//td[contains(text(),'" + duration3_3sec + "')][contains(@class,'no-wrap')]"));
        Assert.assertNotNull(pg.getFirstByXPath("//td[contains(text(),'" + duration14sec + "')][contains(@class,'no-wrap')]"));

        pg = wc.getPage(b, "testReport/org.twia.vendor/VendorManagerTest");

        Assert.assertNotNull(pg.getFirstByXPath("//td[contains(text(),'" + duration2_5sec + "')][contains(@class,'no-wrap')]"));
    }

    /**
     * Creates a freestyle project & build with UNSTABLE status
     * containing a test report from: /hudson/tasks/junit/junit-report-20090516.xml
     */
    private FreeStyleBuild configureTestBuild(String projectName) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject(projectName);
        p.getBuildersList().add(new TestBuilder() {
            @Override
            @SuppressWarnings("null")
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("junit.xml").copyFrom(
                        getClass().getResource("/hudson/tasks/junit/junit-report-20090516.xml"));
                return true;
            }
        });
        p.getPublishersList().add(new JUnitResultArchiver("*.xml"));
        return j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
    }
}
