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

import hudson.FilePath;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.slaves.DumbSlave;
import hudson.tasks.test.TestObject;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.RandomlyFails;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class JUnitResultArchiverTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    private FreeStyleProject project;
    private JUnitResultArchiver archiver;

    @Before public void setUp() throws Exception {
        project = j.createFreeStyleProject("junit");
        archiver = new JUnitResultArchiver("*.xml");
        project.getPublishersList().add(archiver);

        project.getBuildersList().add(new TouchBuilder());
    }

    @LocalData
    @Test public void basic() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);

        assertTestResults(build);

        WebClient wc = j.new WebClient();
        wc.getPage(project); // project page
        wc.getPage(build); // build page
        wc.getPage(build, "testReport");  // test report
        wc.getPage(build, "testReport/hudson.security"); // package
        wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/"); // class
        wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/testDataCompatibilityWith1_282/"); // method


    }

    @RandomlyFails("TimeoutException from basic")
   @LocalData
   @Test public void slave() throws Exception {
        DumbSlave s = j.createOnlineSlave();
        project.setAssignedLabel(s.getSelfLabel());

        FilePath src = new FilePath(j.jenkins.getRootPath(), "jobs/junit/workspace/");
        assertNotNull(src);
        FilePath dest = s.getWorkspaceFor(project);
        assertNotNull(dest);
        src.copyRecursiveTo("*.xml", dest);

        basic();
    }

    private void assertTestResults(FreeStyleBuild build) {
        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertNotNull("no TestResultAction", testResultAction);

        TestResult result = testResultAction.getResult();
        assertNotNull("no TestResult", result);

        assertEquals("should have 1 failing test", 1, testResultAction.getFailCount());
        assertEquals("should have 1 failing test", 1, result.getFailCount());

        assertEquals("should have 132 total tests", 132, testResultAction.getTotalCount());
        assertEquals("should have 132 total tests", 132, result.getTotalCount());
    }

    @LocalData
    @Test public void persistence() throws Exception {
        project.scheduleBuild2(0).get(60, TimeUnit.SECONDS);

        reloadJenkins();

        FreeStyleBuild build = project.getBuildByNumber(1);

        assertTestResults(build);
    }

    private void reloadJenkins() throws Exception {
        j.jenkins.reload();
        project = (FreeStyleProject) j.jenkins.getItem("junit");
    }

    @LocalData
    @Test public void setDescription() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);

        CaseResult caseResult = build.getAction(TestResultAction.class).getFailedTests().get(0);
        String url = build.getUrl() + "/testReport/" + caseResult.getRelativePathFrom(caseResult.getTestResult());

        testSetDescription(url, caseResult);

        ClassResult classResult = caseResult.getParent();
        url = build.getUrl() + "/testReport/" + classResult.getParent().getSafeName() + "/" + classResult.getSafeName();
        testSetDescription(url, classResult);

        PackageResult packageResult = classResult.getParent();
        url = build.getUrl() + "/testReport/" + classResult.getParent().getSafeName();
        testSetDescription(url, packageResult);

    }

    private void testSetDescription(String url, TestObject object) throws Exception {
        object.doSubmitDescription("description");

        // test the roundtrip
        final WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo(url);
        page.getAnchorByHref("editDescription").click();
        wc.waitForBackgroundJavaScript(10000L);
        HtmlForm form = findForm(page, "submitDescription");
        j.submit(form);

        assertEquals("description", object.getDescription());
    }

    private HtmlForm findForm(HtmlPage page, String action) {
        for (HtmlForm form: page.getForms()) {
            if (action.equals(form.getActionAttribute())) {
                return form;
            }
        }
        fail("no form found");
        return null;
    }

    @Test public void repeatedArchiving() throws Exception {
        doRepeatedArchiving(false);
    }
    @Test public void repeatedArchivingSlave() throws Exception {
        doRepeatedArchiving(true);
    }
    private void doRepeatedArchiving(boolean slave) throws Exception {
        if (slave) {
            DumbSlave s = j.createOnlineSlave();
            project.setAssignedLabel(s.getSelfLabel());
        }
        project.getPublishersList().removeAll(JUnitResultArchiver.class);
        project.getBuildersList().add(new SimpleArchive("A", 7, 0));
        project.getBuildersList().add(new SimpleArchive("B", 0, 1));
        FreeStyleBuild build = j.assertBuildStatus(Result.UNSTABLE, project.scheduleBuild2(0).get());
        List<TestResultAction> actions = build.getActions(TestResultAction.class);
        assertEquals(1, actions.size());
        TestResultAction testResultAction = actions.get(0);
        TestResult result = testResultAction.getResult();
        assertNotNull("no TestResult", result);
        assertEquals("should have 1 failing test", 1, testResultAction.getFailCount());
        assertEquals("should have 1 failing test", 1, result.getFailCount());
        assertEquals("should have 8 total tests", 8, testResultAction.getTotalCount());
        assertEquals("should have 8 total tests", 8, result.getTotalCount());
        assertEquals(/* ⅞ = 87.5% */87, testResultAction.getBuildHealth().getScore());
    }
    public static final class SimpleArchive extends Builder {
        private final String name;
        private final int pass;
        private final int fail;
        public SimpleArchive(String name, int pass, int fail) {
            this.name = name;
            this.pass = pass;
            this.fail = fail;
        }
        @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            FilePath ws = build.getWorkspace();
            OutputStream os = ws.child(name + ".xml").write();
            try {
                PrintWriter pw = new PrintWriter(os);
                pw.println("<testsuite failures=\"" + fail + "\" errors=\"0\" skipped=\"0\" tests=\"" + (pass + fail) + "\" name=\"" + name + "\">");
                for (int i = 0; i < pass; i++) {
                    pw.println("<testcase classname=\"" + name + "\" name=\"passing" + i + "\"/>");
                }
                for (int i = 0; i < fail; i++) {
                    pw.println("<testcase classname=\"" + name + "\" name=\"failing" + i + "\"><error message=\"failure\"/></testcase>");
                }
                pw.println("</testsuite>");
                pw.flush();
            } finally {
                os.close();
            }
            new JUnitResultArchiver(name + ".xml").perform(build, ws, launcher, listener);
            return true;
        }
        @TestExtension public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            @Override public String getDisplayName() {
                return "Incremental JUnit result publishing";
            }
        }
    }

    @Test public void configRoundTrip() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setKeepLongStdio(true);
        a.setTestDataPublishers(Collections.<TestDataPublisher>singletonList(new MockTestDataPublisher("testing")));
        a.setHealthScaleFactor(0.77);
        a = j.configRoundtrip(a);
        assertEquals("TEST-*.xml", a.getTestResults());
        assertTrue(a.isKeepLongStdio());
        List<? extends TestDataPublisher> testDataPublishers = a.getTestDataPublishers();
        assertEquals(1, testDataPublishers.size());
        assertEquals(MockTestDataPublisher.class, testDataPublishers.get(0).getClass());
        assertEquals("testing", ((MockTestDataPublisher) testDataPublishers.get(0)).getName());
        assertEquals(0.77, a.getHealthScaleFactor(), 0.01);
    }

    public static class MockTestDataPublisher extends TestDataPublisher {
        private final String name;
        @DataBoundConstructor public MockTestDataPublisher(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        @Override public TestResultAction.Data contributeTestData(Run<?,?> run, FilePath workspace, Launcher launcher, TaskListener listener, TestResult testResult) throws IOException, InterruptedException {
            return null;
        }

        // Needed to make this extension available to all tests for {@link #testDescribableRoundTrip()}
        @TestExtension public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
            @Override public String getDisplayName() {
                return "MockTestDataPublisher";
            }
        }
    }

    @Test public void emptyDirectoryAllowEmptyResult() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setAllowEmptyResults(true);
        FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getPublishersList().add(a);
        j.assertBuildStatus(Result.SUCCESS, freeStyleProject.scheduleBuild2(0).get());
    }

    @Test public void emptyDirectory() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setAllowEmptyResults(false);
        FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getPublishersList().add(a);
        j.assertBuildStatus(Result.FAILURE, freeStyleProject.scheduleBuild2(0).get());
    }

    @Test public void specialCharsInRelativePath() throws Exception {
        final String ID_PREFIX = "test-../a=%3C%7C%23)/testReport/org.twia.vendor/VendorManagerTest/testCreateAdjustingFirm/";
        final String EXPECTED = "org.twia.dao.DAOException: [S2001] Hibernate encountered an error updating Claim [null]";

        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "test-" + j.jenkins.getItems().size());
        p.setAxes(new AxisList(new TextAxis("a", "<|#)")));
        p.setScm(new SingleFileSCM("report.xml", getClass().getResource("junit-report-20090516.xml")));
        p.getPublishersList().add(new JUnitResultArchiver("report.xml"));

        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);

        WebClient wc = j.createWebClient();
        HtmlPage page = wc.getPage(b, "testReport");

        assertThat(page.asText(), not(containsString(EXPECTED)));

        ((HtmlAnchor) page.getElementById(ID_PREFIX + "-showlink")).click();
        wc.waitForBackgroundJavaScript(10000L);
        assertThat(page.asText(), containsString(EXPECTED));

        ((HtmlAnchor) page.getElementById(ID_PREFIX + "-hidelink")).click();
        wc.waitForBackgroundJavaScript(10000L);
        assertThat(page.asText(), not(containsString(EXPECTED)));
    }

    @Issue("JENKINS-26535")
    @Test
    public void testDescribableRoundTrip() throws Exception {
        DescribableModel<JUnitResultArchiver> model = new DescribableModel<JUnitResultArchiver>(JUnitResultArchiver.class);
        Map<String,Object> args = new TreeMap<String,Object>();

        args.put("testResults", "**/TEST-*.xml");
        JUnitResultArchiver j = model.instantiate(args);
        assertEquals("**/TEST-*.xml", j.getTestResults());
        assertFalse(j.isAllowEmptyResults());
        assertFalse(j.isKeepLongStdio());
        assertEquals(1.0, j.getHealthScaleFactor(), 0);
        assertTrue(j.getTestDataPublishers().isEmpty());
        assertEquals(args, model.uninstantiate(model.instantiate(args)));

        // Test roundtripping from a Pipeline-style describing of the publisher.
        Map<String,Object> describedPublisher = new HashMap<String, Object>();
        describedPublisher.put("$class", "MockTestDataPublisher");
        describedPublisher.put("name", "test");
        args.put("testDataPublishers", Collections.singletonList(describedPublisher));

        Map<String,Object> described = model.uninstantiate(model.instantiate(args));
        JUnitResultArchiver j2 = model.instantiate(described);
        List<TestDataPublisher> testDataPublishers = j2.getTestDataPublishers();
        assertFalse(testDataPublishers.isEmpty());
        assertEquals(1, testDataPublishers.size());
        assertEquals(MockTestDataPublisher.class, testDataPublishers.get(0).getClass());
        assertEquals("test", ((MockTestDataPublisher)testDataPublishers.get(0)).getName());

        assertEquals(described, model.uninstantiate(model.instantiate(described)));
    }
}
