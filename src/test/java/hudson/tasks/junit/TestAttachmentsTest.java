package hudson.tasks.junit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.ArrayList;
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

    @Test
    void copyDoesNotFollowSymbolicLinks(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.toRealPath().resolve("ws"));
        Files.writeString(tmp.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(root.resolve("notes.txt"), tmp.resolve("secret.txt"));
        Files.createSymbolicLink(root.resolve("dir"), tmp.toRealPath());
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("sub/ok.txt"), "ok");

        List<Copier> copiers = new ArrayList<>(List.of(TestAttachments::copyRegularFile, TestAttachments::copyByPath));
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(root)) {
            if (dir instanceof SecureDirectoryStream<Path>) {
                copiers.add((r, relative, target) -> {
                    try (SecureDirectoryStream<Path> secure =
                            (SecureDirectoryStream<Path>) Files.newDirectoryStream(r)) {
                        TestAttachments.copySecurely(secure, relative, target);
                    }
                });
            }
        }
        for (Copier copier : copiers) {
            Path target = tmp.resolve("copy.txt");
            Files.deleteIfExists(target);
            assertThrows(IOException.class, () -> copier.copy(root, Path.of("notes.txt"), target));
            assertThrows(IOException.class, () -> copier.copy(root, Path.of("dir/secret.txt"), target));
            assertThat(Files.exists(target), is(false));
            copier.copy(root, Path.of("sub/ok.txt"), target);
            assertEquals("ok", Files.readString(target));
        }
        assertThrows(
                IOException.class,
                () -> TestAttachments.copyRegularFile(root, Path.of("../secret.txt"), tmp.resolve("copy.txt")));
    }

    private interface Copier {
        void copy(Path root, Path relative, Path target) throws IOException;
    }

    @Test
    void attachmentsAreFoundWhenResultStorageKeepsNeitherNodeNorStartTime() {
        TestAttachments.Archived archived = new TestAttachments.Archived("test-attachments/1/a.txt", "a.txt", 1);
        TestAttachments.Data data = new TestAttachments.Data(
                "1",
                Map.of(
                        TestAttachments.key("C", "once", 5), List.of(archived),
                        TestAttachments.key("C", "twice", 5), List.of(archived),
                        TestAttachments.key("C", "twice", 6), List.of(archived),
                        TestAttachments.key("C", "partly", 5), List.of(archived)),
                // "partly" ran twice, attaching a file only once
                Map.of("C#once", 1, "C#twice", 2, "C#partly", 2));
        // as reconstructed by JunitTestResultStorage implementations
        CaseResult once = new CaseResult(null, "C", "once", null, null, 0, "", "", null, null);
        CaseResult twice = new CaseResult(null, "C", "twice", null, null, 0, "", "", null, null);
        CaseResult partly = new CaseResult(null, "C", "partly", null, null, 0, "", "", null, null);
        assertThat(data.getTestAction(once).size(), is(1));
        // cannot tell which
        assertThat(data.getTestAction(twice).size(), is(0));
        assertThat(data.getTestAction(partly).size(), is(0));
        // recorded by several steps, only one of which attached a file
        SuiteResult suite = new SuiteResult("C", null, null, null);
        CaseResult first = new CaseResult(suite, "C", "once", null, null, 0, "", "", null, null);
        suite.addCase(first);
        suite.addCase(new CaseResult(suite, "C", "once", null, null, 0, "", "", null, null));
        assertThat(data.getTestAction(first).size(), is(0));
    }

    @Test
    void attachmentsOfTestsWithoutStartTimesAreNotMerged() {
        TestAttachments.Archived a = new TestAttachments.Archived("test-attachments/1/a.txt", "a.txt", 1);
        TestAttachments.Archived b = new TestAttachments.Archived("test-attachments/2/b.txt", "b.txt", 1);
        TestAttachments.Data data = new TestAttachments.Data(
                "1",
                Map.of(
                        // two runs without a start time share a key
                        TestAttachments.key("C", "twice", -1), List.of(a, b),
                        TestAttachments.key("C", "once", -1), List.of(a)),
                Map.of("C#twice", 2, "C#once", 1));
        CaseResult twice = new CaseResult(null, "C", "twice", null, null, 0, "", "", null, null);
        CaseResult once = new CaseResult(null, "C", "once", null, null, 0, "", "", null, null);
        assertThat(twice.getStartTime(), is(-1L));
        assertThat(data.getTestAction(twice).size(), is(0));
        assertThat(data.getTestAction(once).size(), is(1));
    }

    @Test
    void artifactPathsDoNotCollide() {
        Map<String, String> artifacts = new java.util.HashMap<>();
        TestAttachments.Attachment first = attachment("2/notes.txt", "a/2/notes.txt", 1);
        artifacts.put(TestAttachments.artifactPath(artifacts, "p/", first), first.workspacePath());
        TestAttachments.Attachment second = attachment("notes.txt", "a/notes.txt", 1);
        artifacts.put(TestAttachments.artifactPath(artifacts, "p/", second), second.workspacePath());
        // the same name in another report: under the report number, which the first one took already
        TestAttachments.Attachment third = attachment("notes.txt", "b/notes.txt", 2);
        String path = TestAttachments.artifactPath(artifacts, "p/", third);
        assertEquals("p/2-2/notes.txt", path);
        artifacts.put(path, third.workspacePath());
        assertEquals(
                java.util.Set.of("a/2/notes.txt", "a/notes.txt", "b/notes.txt"),
                new java.util.HashSet<>(artifacts.values()));
        // the same file again
        assertEquals("p/notes.txt", TestAttachments.artifactPath(artifacts, "p/", second));
    }

    @Test
    void artifactPathsAreNotBothFileAndDirectory() {
        Map<String, String> artifacts = new java.util.HashMap<>();
        TestAttachments.Attachment file = attachment("foo", "a/foo", 1);
        artifacts.put(TestAttachments.artifactPath(artifacts, "p/", file), file.workspacePath());
        TestAttachments.Attachment under = attachment("foo/bar.txt", "b/foo/bar.txt", 2);
        assertEquals("p/2/foo/bar.txt", TestAttachments.artifactPath(artifacts, "p/", under));

        artifacts.clear();
        artifacts.put(TestAttachments.artifactPath(artifacts, "p/", under), under.workspacePath());
        assertEquals("p/1/foo", TestAttachments.artifactPath(artifacts, "p/", file));
    }

    private static TestAttachments.Attachment attachment(String path, String workspacePath, int report) {
        return new TestAttachments.Attachment("C", "t", report, path, path, workspacePath, 1, report);
    }

    @Test
    void onlyNumberedPropertiesAreAttachments() {
        assertThat(TestAttachments.isAttachmentProperty("attachment.1"), is(true));
        assertThat(TestAttachments.isAttachmentProperty("attachment.12"), is(true));
        assertThat(TestAttachments.isAttachmentProperty("attachment.notes"), is(false));
        assertThat(TestAttachments.isAttachmentProperty("attachment."), is(false));
        assertThat(TestAttachments.isAttachmentProperty("attachment.0"), is(false));
        assertThat(TestAttachments.isAttachmentProperty("attachment.-1"), is(false));
        assertThat(TestAttachments.isAttachmentProperty("notes"), is(false));
    }

    @Test
    void sameTestInSeveralReportsKeepsItsOwnAttachments() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        FilePath ws = j.jenkins.getWorkspaceFor(job);
        String report;
        try (var in =
                TestAttachmentsTest.class.getResourceAsStream("/io/jenkins/plugins/junit/opentest/maven-events.xml")) {
            report = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String module : new String[] {"a", "b"}) {
            // the modules ran one after the other
            ws.child("reports/" + module + "/open-test-report.xml")
                    .write(module.equals("a") ? report : report.replace("2026-10-10T", "2026-10-11T"), "UTF-8");
            ws.child("reports/" + module + "/" + ATTACHMENT).write("notes of " + module, "UTF-8");
        }
        job.setDefinition(new CpsFlowDefinition(
                "node { junit archiveAttachments: true, testResults: 'reports/**/*.xml' }", true));
        WorkflowRun r = j.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0));
        j.assertLogContains("Archiving 2 test attachments", r);
        List<CaseResult> tests = r.getAction(TestResultAction.class).getResult().getSuites().stream()
                .filter(s -> s.getName().equals("com.example.calc.CalculatorTest"))
                .flatMap(s -> s.getCases().stream())
                .filter(c -> c.getName().equals("publishesFile"))
                .toList();
        assertThat(tests.size(), is(2));
        JenkinsRule.WebClient wc = j.createWebClient();
        java.util.Set<String> contents = new java.util.HashSet<>();
        for (CaseResult test : tests) {
            List<String> links = links(test);
            // only the attachment of its own report
            assertThat(links.size(), is(1));
            contents.add(wc.goTo(links.get(0), "text/plain").getWebResponse().getContentAsString());
        }
        assertEquals(java.util.Set.of("notes of a", "notes of b"), contents);
    }

    @Test
    void attachmentsOfOldReportsAreSkippedLikeTheReports() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        FilePath ws = j.jenkins.getWorkspaceFor(job);
        FilePath report = ws.child("reports/otr-1/open-test-report.xml");
        report.copyFrom(TestAttachmentsTest.class.getResource("/io/jenkins/plugins/junit/opentest/maven-events.xml"));
        ws.child("reports/otr-1/" + ATTACHMENT).write("stale notes", "UTF-8");
        report.touch(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(1));
        job.setDefinition(new CpsFlowDefinition(
                "node { junit archiveAttachments: true, skipOldReports: true, allowEmptyResults: true,"
                        + " testResults: 'reports/**/*.xml' }",
                true));
        WorkflowRun r = j.buildAndAssertSuccess(job);
        j.assertLogNotContains("Archiving", r);
        assertThat(r.getArtifactManager().root().child("test-attachments").exists(), is(false));
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
    void attachmentsOfTheSameTestInSeveralStagesAreKeptApart() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        FilePath ws = j.jenkins.getWorkspaceFor(job);
        for (String stage : new String[] {"a", "b"}) {
            ws.child(stage + "/otr-1/open-test-report.xml")
                    .copyFrom(TestAttachmentsTest.class.getResource(
                            "/io/jenkins/plugins/junit/opentest/maven-events.xml"));
            ws.child(stage + "/otr-1/" + ATTACHMENT).write("notes of " + stage, "UTF-8");
        }
        job.setDefinition(new CpsFlowDefinition("""
                node {
                    stage('a') { junit archiveAttachments: true, testResults: 'a/**/*.xml' }
                    stage('b') { junit archiveAttachments: true, testResults: 'b/**/*.xml' }
                }
                """, true));
        WorkflowRun r = j.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0));
        List<CaseResult> tests = r.getAction(TestResultAction.class).getResult().getSuites().stream()
                .filter(s -> s.getName().equals("com.example.calc.CalculatorTest"))
                .flatMap(s -> s.getCases().stream())
                .filter(c -> c.getName().equals("publishesFile"))
                .toList();
        assertThat(tests.size(), is(2));
        JenkinsRule.WebClient wc = j.createWebClient();
        java.util.Set<String> contents = new java.util.HashSet<>();
        for (CaseResult test : tests) {
            List<String> links = links(test);
            // only the attachment of its own stage
            assertThat(links.size(), is(1));
            contents.add(wc.goTo(links.get(0), "text/plain").getWebResponse().getContentAsString());
        }
        assertEquals(java.util.Set.of("notes of a", "notes of b"), contents);
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
