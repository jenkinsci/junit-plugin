package hudson.tasks.junit;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.WorkspaceList;
import hudson.tasks.test.PipelineTestDetails;
import io.jenkins.plugins.junit.opentest.OtrReader;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.MasterToSlaveFileCallable;
import jenkins.util.BuildListenerAdapter;
import org.apache.tools.ant.DirectoryScanner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Archives the files attached to tests ({@code TestReporter.publishFile}) in Open Test Reporting reports, using the
 * build's {@link jenkins.model.ArtifactManager} like {@code archiveArtifacts} does, and links them from the test pages.
 */
@Restricted(NoExternalUse.class)
public final class TestAttachments {

    private static final Logger LOGGER = Logger.getLogger(TestAttachments.class.getName());

    /** The directory of the build artifacts the attachments are archived to. */
    static final String ARTIFACTS_DIR = "test-attachments";

    static final String PROPERTY_PREFIX = "attachment.";

    private TestAttachments() {}

    /**
     * Archives the attachments of the Open Test Reporting reports matching {@code testResults}.
     *
     * @return the data linking the archived attachments from the test pages, or {@code null} if there were none
     */
    @CheckForNull
    static Data archive(
            @NonNull String testResults,
            boolean keepTestNames,
            @CheckForNull PipelineTestDetails pipelineTestDetails,
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        List<Attachment> found = workspace.act(new Collect(testResults, keepTestNames, pipelineTestDetails));
        if (found.isEmpty()) {
            return null;
        }
        // keep the attachments of each junit invocation apart, as the same tests may run in several stages
        String prefix = ARTIFACTS_DIR + "/"
                + (pipelineTestDetails != null && pipelineTestDetails.getNodeId() != null
                        ? pipelineTestDetails.getNodeId() + "/"
                        : "");
        Map<String, String> artifacts = new LinkedHashMap<>();
        Map<String, List<Archived>> byCase = new LinkedHashMap<>();
        for (Attachment a : found) {
            String path = prefix + a.path();
            String existing = artifacts.get(path);
            if (existing != null && !existing.equals(a.workspacePath())) {
                // the same test attached a file of the same name in several reports
                path = prefix + a.report() + "/" + a.path();
            }
            artifacts.put(path, a.workspacePath());
            List<Archived> archived = byCase.computeIfAbsent(key(a.className(), a.name()), k -> new ArrayList<>());
            String archivedPath = path;
            if (archived.stream().noneMatch(x -> x.path.equals(archivedPath))) {
                archived.add(new Archived(path, a.property(), a.size()));
            }
        }
        listener.getLogger()
                .println("Archiving " + artifacts.size()
                        + (artifacts.size() == 1 ? " test attachment" : " test attachments"));
        // Lay the files out as they are to be archived: some artifact managers (e.g. azure-artifact-manager) only
        // support archiving files under the same path as in the workspace, like archiveArtifacts does.
        FilePath tmp = WorkspaceList.tempDir(workspace);
        FilePath staging = (tmp != null ? tmp : workspace).child("junit-attachments-" + UUID.randomUUID());
        try {
            staging.act(new Stage(workspace.getRemote(), artifacts));
            Map<String, String> staged = new LinkedHashMap<>();
            for (String path : artifacts.keySet()) {
                staged.put(path, path);
            }
            run.pickArtifactManager().archive(staging, launcher, new BuildListenerAdapter(listener), staged);
        } catch (IOException | RuntimeException e) {
            // the test results matter more than their attachments
            LOGGER.log(Level.WARNING, "Could not archive test attachments of " + run, e);
            listener.error("Could not archive test attachments: " + e);
            return null;
        } finally {
            try {
                staging.deleteRecursive();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Could not delete " + staging, e);
            }
        }
        return new Data(byCase);
    }

    static String key(String className, String name) {
        return className + '#' + name;
    }

    /** A file attached to a test, found on the agent. */
    record Attachment(
            String className, String name, String property, String path, String workspacePath, long size, int report)
            implements Serializable {}

    /** Copies attachments from the workspace to a staging directory, under the paths they are archived as. */
    private static final class Stage extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        private final String workspace;
        private final Map<String, String> artifacts;

        Stage(String workspace, Map<String, String> artifacts) {
            this.workspace = workspace;
            this.artifacts = new LinkedHashMap<>(artifacts);
        }

        @Override
        public Void invoke(File staging, VirtualChannel channel) throws IOException {
            for (Map.Entry<String, String> e : artifacts.entrySet()) {
                Path target = staging.toPath().resolve(e.getKey());
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(new File(workspace, e.getValue()).toPath(), target);
            }
            return null;
        }
    }

    /** Finds the attachments of the Open Test Reporting reports matching a pattern. */
    private static final class Collect extends MasterToSlaveFileCallable<List<Attachment>> {
        private static final long serialVersionUID = 1L;

        private final String testResults;
        private final boolean keepTestNames;

        @CheckForNull
        private final PipelineTestDetails pipelineTestDetails;

        Collect(String testResults, boolean keepTestNames, @CheckForNull PipelineTestDetails pipelineTestDetails) {
            this.testResults = testResults;
            this.keepTestNames = keepTestNames;
            this.pipelineTestDetails = pipelineTestDetails;
        }

        @Override
        public List<Attachment> invoke(File ws, VirtualChannel channel) throws IOException {
            DirectoryScanner ds = Util.createFileSet(ws, testResults).getDirectoryScanner();
            Path root = ws.toPath().toRealPath();
            List<Attachment> found = new ArrayList<>();
            int index = 0;
            for (String name : ds.getIncludedFiles()) {
                File report = new File(ws, name);
                if (OtrReader.detect(report) == null) {
                    continue;
                }
                index++;
                Path dir = report.getParentFile().toPath().toRealPath();
                // converted just like the results, so that the test names match
                for (SuiteResult suite : OpenTestReportConverter.parse(
                        report, StdioRetention.NONE, true, keepTestNames, pipelineTestDetails)) {
                    for (CaseResult c : suite.getCases()) {
                        for (Map.Entry<String, String> property :
                                c.getProperties().entrySet()) {
                            if (!property.getKey().startsWith(PROPERTY_PREFIX)) {
                                continue;
                            }
                            Path file = resolve(root, dir, property.getValue());
                            if (file != null) {
                                found.add(new Attachment(
                                        c.getClassName(),
                                        c.getName(),
                                        property.getValue(),
                                        dir.relativize(file).toString().replace(File.separatorChar, '/'),
                                        root.relativize(file).toString().replace(File.separatorChar, '/'),
                                        Files.size(file),
                                        index));
                            }
                        }
                    }
                }
            }
            return found;
        }
    }

    /**
     * Resolves an attachment path, which is relative to the directory of its report.
     *
     * @return the file, or {@code null} if it does not exist or is not inside both the report directory and the
     * workspace (reports are not trusted)
     */
    @CheckForNull
    static Path resolve(Path root, Path dir, String path) {
        try {
            Path relative = Path.of(path.replace('\\', '/'));
            if (relative.isAbsolute()) {
                return null;
            }
            Path file = dir.resolve(relative).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                return null;
            }
            Path real = file.toRealPath();
            if (!real.startsWith(dir) || !real.startsWith(root)) {
                return null;
            }
            return real;
        } catch (InvalidPathException | IOException e) {
            LOGGER.log(Level.FINE, "Ignoring attachment " + path, e);
            return null;
        }
    }

    /** An archived attachment of a test. */
    static final class Archived {
        /** The artifact path. */
        final String path;
        /** The path in the report, as kept in the {@code attachment.N} property. */
        final String source;

        final long size;

        Archived(String path, String source, long size) {
            this.path = path;
            this.source = source;
            this.size = size;
        }
    }

    /** Links the archived attachments of tests from their pages. */
    public static final class Data extends TestResultAction.Data {
        /** By {@link #key test}. */
        private final Map<String, List<Archived>> attachments;

        Data(Map<String, List<Archived>> attachments) {
            this.attachments = attachments;
        }

        @Override
        public List<? extends TestAction> getTestAction(TestObject testObject) {
            if (testObject instanceof CaseResult c) {
                List<Archived> archived = attachments.get(key(c.getClassName(), c.getName()));
                if (archived != null) {
                    return List.of(new AttachmentsAction(c, archived));
                }
            }
            return Collections.emptyList();
        }
    }

    /** Shows the archived attachments of a test on its page. */
    public static final class AttachmentsAction extends TestAction {
        private final CaseResult caseResult;
        private final List<Archived> archived;

        AttachmentsAction(CaseResult caseResult, List<Archived> archived) {
            this.caseResult = caseResult;
            this.archived = archived;
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Attachments";
        }

        @Override
        public String getUrlName() {
            return null;
        }

        /** Whether this shows the attachment kept in a property, which therefore need not be shown again. */
        boolean shows(String key, String value) {
            return key.startsWith(PROPERTY_PREFIX) && archived.stream().anyMatch(a -> a.source.equals(value));
        }

        public List<Link> getLinks() {
            Run<?, ?> run = caseResult.getRun();
            List<Link> links = new ArrayList<>();
            for (Archived a : archived) {
                StringBuilder url = new StringBuilder(run == null ? "" : run.getUrl()).append("artifact");
                for (String segment : a.path.split("/")) {
                    url.append('/').append(Util.rawEncode(segment));
                }
                links.add(new Link(a.path.substring(a.path.lastIndexOf('/') + 1), url.toString(), a.size));
            }
            return links;
        }
    }

    /** An archived attachment. */
    public static final class Link {
        private final String name;
        private final String url;
        private final long size;

        Link(String name, String url, long size) {
            this.name = name;
            this.url = url;
            this.size = size;
        }

        /** The file name. */
        public String getName() {
            return name;
        }

        /** The URL of the archived file, relative to the context path. */
        public String getUrl() {
            return url;
        }

        /** The size of the file, for display. */
        public String getFileSize() {
            return Functions.humanReadableByteSize(size);
        }
    }
}
