/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.recipes.LocalData;

public final class TestResultActionTest {

    @Rule public JenkinsSessionRule rr = new JenkinsSessionRule();
    @Rule public LoggerRule logging = new LoggerRule();

    @Issue("JENKINS-71139")
    @LocalData
    @Test public void stdioNull() throws Throwable {
        logging.record(TestResultAction.class, Level.WARNING).capture(10);
        rr.then(r -> {
            /* Setup from 1196.vb_4cf28b_c7724, right before fix:
            var p = r.createFreeStyleProject("p");
            p.getPublishersList().add(new JUnitResultArchiver("TEST-x.xml"));
            var ws = r.jenkins.getWorkspaceFor(p);
            ws.child("TEST-x.xml").write("<testsuite errors='1' failures='0' name='x' tests='1'><testcase classname='X' name='x'><error message='x'>xxx</error></testcase></testsuite>", null);
            ws.child("x-output.txt").write("foo\u0000bar", "ISO-8859-1");
            var b = r.buildAndAssertStatus(Result.UNSTABLE, p);
            var a = b.getAction(TestResultAction.class);
            assertThat(a.getFailCount(), is(1));
            assertThat(FileUtils.readFileToString(new File(b.getRootDir(), "junitResult.xml"), StandardCharsets.UTF_8), containsString("&#x0;"));
            System.err.println(r.jenkins.getRootDir());
            Thread.sleep(Long.MAX_VALUE);
            */
            TestResultAction a = r.jenkins.getItemByFullName("p", FreeStyleProject.class).getBuildByNumber(1).getAction(TestResultAction.class);
            assertThat(a, notNullValue());
            assertThat(a.getFailCount(), is(1));
            assertThat(a.getResult().getSuites(), empty());
        });
        rr.then(r -> {
            TestResultAction a = r.jenkins.getItemByFullName("p", FreeStyleProject.class).getBuildByNumber(1).getAction(TestResultAction.class);
            assertThat(a, notNullValue());
            assertThat(a.getFailCount(), is(1));
        });
        assertThat(logging.getMessages(), empty());
    }

}
