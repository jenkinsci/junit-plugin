package hudson.tasks.junit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

@WithJenkins
class TestAttachmentsTest {

    private static final String ATTACHMENT =
            "junit-jupiter/com.example.calc.CalculatorTest/publishesFile(org.junit.jupiter.api.TestReporter)/calc-notes.txt";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void resolveOnlyAcceptsFilesInsideTheReportDirectory(@TempDir Path tmp) throws Exception {
        Path root = tmp.toRealPath();
        Path dir = Files.createDirectories(root.resolve("reports"));
        Files.writeString(Files.createDirectories(dir.resolve("a")).resolve("ok.txt"), "ok");
        Files.writeString(root.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(dir.resolve("link.txt"), root.resolve("secret.txt"));

        assertEquals(dir.resolve("a/ok.txt"), TestAttachments.resolve(root, dir, "a/ok.txt"));
        assertEquals(dir.resolve("a/ok.txt"), TestAttachments.resolve(root, dir, "a\\ok.txt"));
        assertThat(TestAttachments.resolve(root, dir, "../secret.txt"), nullValue());
        assertThat(TestAttachments.resolve(root, dir, "a/../../secret.txt"), nullValue());
        assertThat(TestAttachments.resolve(root, dir, root.resolve("secret.txt").toString()), nullValue());
        assertThat(TestAttachments.resolve(root, dir, "link.txt"), nullValue());
        assertThat(TestAttachments.resolve(root, dir, "missing.txt"), nullValue());
        assertThat(TestAttachments.resolve(root, dir, "a"), nullValue());
    }

    private WorkflowRun runWithReport(String junit) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        FilePath ws = j.jenkins.getWorkspaceFor(job);
        ws.child("reports/otr-1/open-test-report.xml")
                .copyFrom(TestAttachmentsTest.class.getResource("/io/jenkins/plugins/junit/opentest/maven-events.xml"));
        ws.child("reports/otr-1/" + ATTACHMENT).write("some notes", "UTF-8");
        job.setDefinition(new CpsFlowDefinition("node { " + junit + " }", true));
        return j.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0));
    }

    private static CaseResult publishesFile(Run<?, ?> r) {
        return r
                .getAction(TestResultAction.class)
                .getResult()
                .getSuite("com.example.calc.CalculatorTest")
                .getCases()
                .stream()
                .filter(c -> c.getName().equals("publishesFile"))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> links(CaseResult test) {
        return test.getTestActions().stream()
                .filter(TestAttachments.AttachmentsAction.class::isInstance)
                .flatMap(a -> ((TestAttachments.AttachmentsAction) a).getLinks().stream())
                .map(TestAttachments.Link::getUrl)
                .toList();
    }

    @Test
    void junitArchivesAttachmentsWhenAsked() throws Exception {
        WorkflowRun r = runWithReport("junit archiveAttachments: true, testResults: 'reports/**/*.xml'");
        j.assertLogContains("Archiving 1 test attachment", r);
        CaseResult test = publishesFile(r);
        // properties are still not kept by default
        assertThat(test.getProperties().isEmpty(), is(true));
        List<String> links = links(test);
        assertThat(links.size(), is(1));
        assertThat(links.get(0), containsString("/artifact/test-attachments/"));
        assertEquals(
                "some notes",
                j.createWebClient()
                        .goTo(links.get(0), "text/plain")
                        .getWebResponse()
                        .getContentAsString());
    }

    @Test
    void junitDoesNotArchiveAttachmentsByDefault() throws Exception {
        WorkflowRun r = runWithReport("junit 'reports/**/*.xml'");
        j.assertLogNotContains("Archiving", r);
        assertThat(r.getArtifactManager().root().child("test-attachments").exists(), is(false));
        assertThat(links(publishesFile(r)).isEmpty(), is(true));
    }

    @Test
    void attachmentsOutsideTheReportDirectoryAreIgnored() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        FilePath ws = j.jenkins.getWorkspaceFor(job);
        ws.child("secret.txt").write("secret", "UTF-8");
        String report = Util.loadFile(
                        new File(TestAttachmentsTest.class
                                .getResource("/io/jenkins/plugins/junit/opentest/maven-events.xml")
                                .toURI()),
                        java.nio.charset.StandardCharsets.UTF_8)
                .replace(ATTACHMENT, "../../secret.txt");
        ws.child("reports/otr-1/open-test-report.xml").write(report, "UTF-8");
        job.setDefinition(new CpsFlowDefinition(
                "node { junit archiveAttachments: true, testResults: 'reports/**/*.xml' }", true));
        WorkflowRun r = j.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0));
        j.assertLogNotContains("Archiving", r);
        assertThat(r.getArtifactManager().root().child("test-attachments").exists(), is(false));
    }

    @Test
    void usesTheConfiguredArtifactManager() throws Exception {
        ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(new ExternalArtifactManagerFactory());
        FreeStyleProject p = j.createFreeStyleProject();
        FilePath ws = j.jenkins.getWorkspaceFor(p);
        ws.child("reports/otr-1/open-test-report.xml")
                .copyFrom(TestAttachmentsTest.class.getResource("/io/jenkins/plugins/junit/opentest/maven-events.xml"));
        ws.child("reports/otr-1/" + ATTACHMENT).write("some notes", "UTF-8");
        JUnitResultArchiver archiver = new JUnitResultArchiver("reports/**/*.xml");
        archiver.setArchiveAttachments(true);
        p.getPublishersList().add(archiver);
        FreeStyleBuild b = j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));

        assertThat(b.getArtifactManager(), is(org.hamcrest.Matchers.instanceOf(ExternalArtifactManager.class)));
        // not in the build directory
        assertThat(new File(b.getRootDir(), "archive").exists(), is(false));
        assertThat(
                b.getArtifactManager()
                        .root()
                        .child("test-attachments/" + ATTACHMENT)
                        .exists(),
                is(true));
        assertThat(
                links(publishesFile(b)),
                contains(b.getUrl() + "artifact/test-attachments/junit-jupiter/"
                        + "com.example.calc.CalculatorTest/publishesFile(org.junit.jupiter.api.TestReporter)/"
                        + "calc-notes.txt"));
        assertEquals(
                "some notes",
                j.createWebClient()
                        .goTo(links(publishesFile(b)).get(0), "text/plain")
                        .getWebResponse()
                        .getContentAsString());
    }

    @Test
    void archivingFailuresDoNotLoseTheResults() throws Exception {
        ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(new BrokenArtifactManagerFactory());
        WorkflowRun r = runWithReport("junit archiveAttachments: true, testResults: 'reports/**/*.xml'");
        j.assertLogContains("Could not archive test attachments: java.io.IOException: storage is down", r);
        assertEquals(31, r.getAction(TestResultAction.class).getTotalCount());
        assertThat(links(publishesFile(r)).isEmpty(), is(true));
    }

    public static final class BrokenArtifactManagerFactory extends ArtifactManagerFactory {
        @DataBoundConstructor
        public BrokenArtifactManagerFactory() {}

        @Override
        public ArtifactManager managerFor(Run<?, ?> build) {
            return new BrokenArtifactManager(build);
        }

        @TestExtension("archivingFailuresDoNotLoseTheResults")
        public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {}
    }

    static final class BrokenArtifactManager extends ExternalArtifactManager {
        BrokenArtifactManager(Run<?, ?> build) {
            super(build);
        }

        @Override
        public void archive(
                FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts)
                throws IOException {
            throw new IOException("storage is down");
        }
    }

    /** Stores artifacts outside of the build directory, like cloud artifact managers do. */
    public static final class ExternalArtifactManagerFactory extends ArtifactManagerFactory {
        @DataBoundConstructor
        public ExternalArtifactManagerFactory() {}

        @Override
        public ArtifactManager managerFor(Run<?, ?> build) {
            return new ExternalArtifactManager(build);
        }

        @TestExtension("usesTheConfiguredArtifactManager")
        public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {}
    }

    static class ExternalArtifactManager extends ArtifactManager {
        private transient File dir;

        ExternalArtifactManager(Run<?, ?> build) {
            onLoad(build);
        }

        @Override
        public void onLoad(Run<?, ?> build) {
            dir = new File(
                    Jenkins.get().getRootDir(), "external-artifacts/" + Util.getDigestOf(build.getExternalizableId()));
        }

        @Override
        public void archive(
                FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts)
                throws IOException, InterruptedException {
            // like azure-artifact-manager, only supports archiving files under their path in the workspace
            Map<String, String> sameNames = new java.util.LinkedHashMap<>();
            for (String path : artifacts.keySet()) {
                sameNames.put(path, path);
            }
            workspace.copyRecursiveTo(
                    new FilePath.ExplicitlySpecifiedDirScanner(sameNames), new FilePath(dir), "copying");
        }

        @Override
        public boolean delete() throws IOException {
            Util.deleteRecursive(dir);
            return true;
        }

        @Override
        public VirtualFile root() {
            return VirtualFile.forFile(dir);
        }
    }
}
