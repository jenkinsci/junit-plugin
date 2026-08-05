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
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.UnprotectedRootAction;
import hudson.slaves.DumbSlave;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.helper.WebClientFactory;
import hudson.util.HttpResponses;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;

@WithJenkins
class JUnitResultArchiverTest {

    private final LogRecorder logging = new LogRecorder().recordPackage(JUnitResultArchiver.class, Level.FINE);

    private FreeStyleProject project;
    private JUnitResultArchiver archiver;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        project = j.createFreeStyleProject("junit");
        archiver = new JUnitResultArchiver("*.xml");
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TouchBuilder());
    }

    @LocalData("All")
    @Test
    void basic() throws Exception {
        FreeStyleBuild build = j.assertBuildStatus(
                Result.UNSTABLE, j.waitForCompletion(project.scheduleBuild2(0).waitForStart()));

        assertTestResults(build);

        JenkinsRule.WebClient wc = WebClientFactory.createWebClientWithDisabledJavaScript(j);
        wc.getPage(project); // project page
        wc.getPage(build); // build page
        wc.getPage(build, "testReport"); // test report
        wc.getPage(build, "testReport/hudson.security"); // package
        wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/"); // class
        wc.getPage(
                build,
                "testReport/hudson.security/HudsonPrivateSecurityRealmTest/testDataCompatibilityWith1_282/"); // method
    }

    @LocalData("All")
    @Test
    void slave() throws Exception {
        assumeFalse(Functions.isWindows(), "TODO frequent TimeoutException from basic");
        DumbSlave node = j.createSlave("label1 label2", null);
        // the node needs to be online before showAgentLogs
        j.waitOnline(node);
        j.showAgentLogs(node, Map.of(JUnitResultArchiver.class.getPackageName(), Level.FINE));
        project.setAssignedLabel(j.jenkins.getLabel("label1"));

        FilePath src = new FilePath(j.jenkins.getRootPath(), "jobs/junit/workspace/");
        assertNotNull(src);
        FilePath dest = node.getWorkspaceFor(project);
        assertNotNull(dest);
        src.copyRecursiveTo("*.xml", dest);
        assertEquals(56, dest.list("*.xml").length);
        basic();
    }

    private void assertTestResults(FreeStyleBuild build) throws Exception {
        j.assertBuildStatus(Result.UNSTABLE, build);
        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertNotNull(testResultAction, "no TestResultAction");

        TestResult result = testResultAction.getResult();
        assertNotNull(result, "no TestResult");

        assertEquals(1, testResultAction.getFailCount(), "should have 1 failing test");
        assertEquals(1, result.getFailCount(), "should have 1 failing test");

        assertEquals(132, testResultAction.getTotalCount(), "should have 132 total tests");
        assertEquals(132, result.getTotalCount(), "should have 132 total tests");

        for (SuiteResult suite : result.getSuites()) {
            assertNull(suite.getNodeId(), "No nodeId should be present on the SuiteResult");
        }
    }

    @LocalData("All")
    @Test
    void persistence() throws Exception {
        project.scheduleBuild2(0).get(60, TimeUnit.SECONDS);

        reloadJenkins();

        FreeStyleBuild build = project.getBuildByNumber(1);

        assertTestResults(build);
    }

    private void reloadJenkins() throws Exception {
        j.jenkins.reload();
        project = (FreeStyleProject) j.jenkins.getItem("junit");
    }

    @LocalData("All")
    @Test
    void setDescription() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);

        CaseResult caseResult =
                build.getAction(TestResultAction.class).getFailedTests().get(0);
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
        final JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo(url);
        page.getAnchorByHref("editDescription").click();
        wc.waitForBackgroundJavaScript(10000L);
        HtmlForm form = findForm(page, "submitDescription");
        j.submit(form);

        assertEquals("description", object.getDescription());
    }

    private HtmlForm findForm(HtmlPage page, String action) {
        for (HtmlForm form : page.getForms()) {
            if (action.equals(form.getActionAttribute())) {
                return form;
            }
        }
        fail("no form found");
        return null;
    }

    @Test
    void repeatedArchiving() throws Exception {
        doRepeatedArchiving(false);
    }

    @Test
    void repeatedArchivingSlave() throws Exception {
        doRepeatedArchiving(true);
    }

    private void doRepeatedArchiving(boolean slave) throws Exception {
        if (slave) {
            DumbSlave s = j.createSlave("label1 label2", null);
            project.setAssignedLabel(j.jenkins.getLabel("label1"));
            j.waitOnline(s);
        }
        project.getPublishersList().removeAll(JUnitResultArchiver.class);
        project.getBuildersList().add(new SimpleArchive("A", 7, 0));
        project.getBuildersList().add(new SimpleArchive("B", 0, 1));
        FreeStyleBuild build =
                j.assertBuildStatus(Result.UNSTABLE, project.scheduleBuild2(0).get());
        List<TestResultAction> actions = build.getActions(TestResultAction.class);
        assertEquals(1, actions.size());
        TestResultAction testResultAction = actions.get(0);
        TestResult result = testResultAction.getResult();
        assertNotNull(result, "no TestResult");
        assertEquals(1, testResultAction.getFailCount(), "should have 1 failing test");
        assertEquals(1, result.getFailCount(), "should have 1 failing test");
        assertEquals(8, testResultAction.getTotalCount(), "should have 8 total tests");
        assertEquals(8, result.getTotalCount(), "should have 8 total tests");
        assertEquals(/* ⅞ = 87.5% */ 87, testResultAction.getBuildHealth().getScore());
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

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            FilePath ws = build.getWorkspace();
            try (OutputStream os = ws.child(name + ".xml").write()) {
                PrintWriter pw = new PrintWriter(os);
                pw.println("<testsuite failures=\"" + fail + "\" errors=\"0\" skipped=\"0\" tests=\"" + (pass + fail)
                        + "\" name=\"" + name + "\">");
                for (int i = 0; i < pass; i++) {
                    pw.println("<testcase classname=\"" + name + "\" name=\"passing" + i + "\"/>");
                }
                for (int i = 0; i < fail; i++) {
                    pw.println("<testcase classname=\"" + name + "\" name=\"failing" + i
                            + "\"><error message=\"failure\"/></testcase>");
                }
                pw.println("</testsuite>");
                pw.flush();
            }
            ws.touch(build.getTimeInMillis() + 1);
            new JUnitResultArchiver(name + ".xml").perform(build, ws, launcher, listener);
            return true;
        }

        @TestExtension
        public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            @Override
            public String getDisplayName() {
                return "Incremental JUnit result publishing";
            }
        }
    }

    @Test
    void configRoundTrip() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setStdioRetention(StdioRetention.ALL.name());
        a.setTestDataPublishers(Collections.singletonList(new MockTestDataPublisher("testing")));
        a.setHealthScaleFactor(0.77);
        a = j.configRoundtrip(a);
        assertEquals("TEST-*.xml", a.getTestResults());
        assertEquals(StdioRetention.ALL, a.getParsedStdioRetention());
        List<? extends TestDataPublisher> testDataPublishers = a.getTestDataPublishers();
        assertEquals(1, testDataPublishers.size());
        assertEquals(MockTestDataPublisher.class, testDataPublishers.get(0).getClass());
        assertEquals("testing", ((MockTestDataPublisher) testDataPublishers.get(0)).getName());
        assertEquals(0.77, a.getHealthScaleFactor(), 0.01);
    }

    public static class MockTestDataPublisher extends TestDataPublisher {
        private final String name;

        @DataBoundConstructor
        public MockTestDataPublisher(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public TestResultAction.Data contributeTestData(
                Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, TestResult testResult) {
            return null;
        }

        // Needed to make this extension available to all tests for {@link #testDescribableRoundTrip()}
        @TestExtension
        public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
            @Override
            public String getDisplayName() {
                return "MockTestDataPublisher";
            }
        }
    }

    @Test
    void noTestResultFilesAllowEmptyResult() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setAllowEmptyResults(true);
        FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getPublishersList().add(a);
        FreeStyleBuild build = freeStyleProject.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, build);
        j.assertLogContains(Messages.JUnitResultArchiver_NoTestReportFound(), build);
    }

    @Test
    void noTestResultFilesDisallowEmptyResult() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setAllowEmptyResults(false);
        FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getPublishersList().add(a);
        FreeStyleBuild build = freeStyleProject.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains(Messages.JUnitResultArchiver_NoTestReportFound(), build);
    }

    @Test
    void noResultsInTestResultFilesAllowEmptyResult() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setAllowEmptyResults(true);
        FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getBuildersList().add(new NoResultsInTestResultFileBuilder());
        freeStyleProject.getPublishersList().add(a);
        FreeStyleBuild build = freeStyleProject.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, build);
        j.assertLogContains(Messages.JUnitResultArchiver_ResultIsEmpty(), build);
    }

    @Test
    void noResultsInTestResultFilesDisallowEmptyResult() throws Exception {
        JUnitResultArchiver a = new JUnitResultArchiver("TEST-*.xml");
        a.setAllowEmptyResults(false);
        FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getBuildersList().add(new NoResultsInTestResultFileBuilder());
        freeStyleProject.getPublishersList().add(a);
        FreeStyleBuild build = freeStyleProject.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains(Messages.JUnitResultArchiver_ResultIsEmpty(), build);
    }

    public static final class NoResultsInTestResultFileBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            String fileName = "TEST-foo.xml";
            String fileContent = "<testsuite name=\"foo\" failures=\"0\" errors=\"0\" skipped=\"0\" tests=\"0\"/>";
            build.getWorkspace().child(fileName).write(fileContent, "UTF-8");
            return true;
        }
    }

    @Test
    void specialCharsInRelativePath() throws Exception {
        assumeFalse(Functions.isWindows());
        final String ID_PREFIX =
                "test-../a=%3C%7C%23)/testReport/org.twia.vendor/VendorManagerTest/testCreateAdjustingFirm/";
        final String EXPECTED =
                "org.twia.dao.DAOException: [S2001] Hibernate encountered an error updating Claim [null]";

        MatrixProject p = j.jenkins.createProject(
                MatrixProject.class, "test-" + j.jenkins.getItems().size());
        p.setAxes(new AxisList(new TextAxis("a", "<|#)")));
        p.setScm(new SingleFileSCM("report.xml", getClass().getResource("junit-report-20090516.xml")));
        p.getPublishersList().add(new JUnitResultArchiver("report.xml"));

        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.getPage(b, "testReport");

        assertThat(page.asNormalizedText(), not(containsString(EXPECTED)));

        page.getElementById(ID_PREFIX + "-showlink").click();
        wc.waitForBackgroundJavaScript(10000L);
        assertThat(page.asNormalizedText(), containsString(EXPECTED));

        page.getElementById(ID_PREFIX + "-showlink").click();
        wc.waitForBackgroundJavaScript(10000L);
        assertThat(page.asNormalizedText(), not(containsString(EXPECTED)));
    }

    @Issue("JENKINS-26535")
    @Test
    void testDescribableRoundTrip() {
        DescribableModel<JUnitResultArchiver> model = new DescribableModel<>(JUnitResultArchiver.class);
        Map<String, Object> args = new TreeMap<>();

        args.put("testResults", "**/TEST-*.xml");
        JUnitResultArchiver j = model.instantiate(args);
        assertEquals("**/TEST-*.xml", j.getTestResults());
        assertFalse(j.isAllowEmptyResults());
        assertEquals(StdioRetention.NONE, j.getParsedStdioRetention());
        assertEquals(1.0, j.getHealthScaleFactor(), 0);
        assertTrue(j.getTestDataPublishers().isEmpty());
        assertEquals(args, model.uninstantiate(model.instantiate(args)));

        // Test roundtripping from a Pipeline-style describing of the publisher.
        Map<String, Object> describedPublisher = new HashMap<>();
        describedPublisher.put("$class", "MockTestDataPublisher");
        describedPublisher.put("name", "test");
        args.put("testDataPublishers", Collections.singletonList(describedPublisher));

        Map<String, Object> described = model.uninstantiate(model.instantiate(args));
        JUnitResultArchiver j2 = model.instantiate(described);
        List<TestDataPublisher> testDataPublishers = j2.getTestDataPublishers();
        assertFalse(testDataPublishers.isEmpty());
        assertEquals(1, testDataPublishers.size());
        assertEquals(MockTestDataPublisher.class, testDataPublishers.get(0).getClass());
        assertEquals("test", ((MockTestDataPublisher) testDataPublishers.get(0)).getName());

        assertEquals(described, model.uninstantiate(model.instantiate(described)));
    }

    @Test
    @Issue("SECURITY-521")
    void testXxe() throws Exception {
        String oobInUserContentLink = j.getURL() + "userContent/oob.xml";
        String triggerLink = j.getURL() + "triggerMe";

        URL xxeResourceUrl = this.getClass().getResource("testXxe-xxe.xml");
        if (xxeResourceUrl == null) {
            throw new FileNotFoundException("Resource 'testXxe-xxe.xml' not found");
        }
        File xxeFile = new File(xxeResourceUrl.toURI());
        String xxeFileContent = FileUtils.readFileToString(xxeFile, StandardCharsets.UTF_8);
        String adaptedXxeFileContent = xxeFileContent.replace("$OOB_LINK$", oobInUserContentLink);

        URL oobResourceUrl = this.getClass().getResource("testXxe-oob.xml");
        if (oobResourceUrl == null) {
            throw new FileNotFoundException("Resource 'testXxe-oob.xml' not found");
        }
        File oobFile = new File(oobResourceUrl.toURI());
        String oobFileContent = FileUtils.readFileToString(oobFile, StandardCharsets.UTF_8);
        String adaptedOobFileContent = oobFileContent.replace("$TARGET_URL$", triggerLink);

        File userContentDir = new File(j.jenkins.getRootDir(), "userContent");
        FileUtils.writeStringToFile(new File(userContentDir, "oob.xml"), adaptedOobFileContent, StandardCharsets.UTF_8);

        FreeStyleProject project = j.createFreeStyleProject();
        DownloadBuilder builder = new DownloadBuilder();
        builder.fileContent = adaptedXxeFileContent;
        project.getBuildersList().add(builder);

        JUnitResultArchiver publisher = new JUnitResultArchiver("xxe.xml");
        project.getPublishersList().add(publisher);

        project.scheduleBuild2(0).get();
        // UNSTABLE
        // assertEquals(Result.SUCCESS, project.scheduleBuild2(0).get().getResult());

        YouCannotTriggerMe urlHandler =
                j.jenkins.getExtensionList(UnprotectedRootAction.class).get(YouCannotTriggerMe.class);
        assertEquals(0, urlHandler.triggerCount);
    }

    public static class DownloadBuilder extends Builder {
        String fileContent;

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            try {
                FileUtils.writeStringToFile(new File(build.getWorkspace().getRemote(), "xxe.xml"), fileContent);
            } catch (IOException e) {
                return false;
            }

            return true;
        }

        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }

    @TestExtension("testXxe")
    public static class YouCannotTriggerMe implements UnprotectedRootAction {
        private int triggerCount = 0;

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "triggerMe";
        }

        public HttpResponse doIndex() {
            triggerCount++;
            return HttpResponses.plainText("triggered");
        }
    }
}
