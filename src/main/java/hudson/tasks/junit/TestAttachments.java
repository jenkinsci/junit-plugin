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
import java.io.InputStream;
import java.io.Serializable;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * @param filesTimestamp reports older than this are ignored, like by the parser with {@code skipOldReports}; or
     *                       {@link Long#MIN_VALUE}
     * @return the data linking the archived attachments from the test pages, or {@code null} if there were none
     */
    @CheckForNull
    static Data archive(
            @NonNull String testResults,
            boolean keepTestNames,
            long filesTimestamp,
            @CheckForNull PipelineTestDetails pipelineTestDetails,
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws InterruptedException {
        Collected collected;
        try {
            collected = workspace.act(new Collect(testResults, keepTestNames, filesTimestamp, pipelineTestDetails));
        } catch (IOException | RuntimeException e) {
            // the test results matter more than their attachments
            LOGGER.log(Level.WARNING, "Could not collect test attachments of " + run, e);
            listener.error("Could not collect test attachments: " + e);
            return null;
        }
        List<Attachment> found = collected.attachments();
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
            String path = artifactPath(artifacts, prefix, a);
            artifacts.put(path, a.workspacePath());
            List<Archived> archived =
                    byCase.computeIfAbsent(key(a.className(), a.name(), a.startTime()), k -> new ArrayList<>());
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
        return new Data(pipelineTestDetails != null ? pipelineTestDetails.getNodeId() : null, byCase, collected.runs());
    }

    /**
     * Chooses where to archive an attachment.
     *
     * @param artifacts the attachments to archive so far, by artifact path
     */
    static String artifactPath(Map<String, String> artifacts, String prefix, Attachment a) {
        String path = prefix + a.path();
        for (int n = 1; collides(artifacts, path, a.workspacePath()); n++) {
            // the same test attached a file of the same name in several reports, or another took the path already
            path = prefix + a.report() + (n == 1 ? "" : "-" + n) + "/" + a.path();
        }
        return path;
    }

    /**
     * Whether a path is taken by another file, or cannot be a file as another one is under it, or the other way round
     * (e.g. {@code foo} and {@code foo/bar.txt}).
     */
    private static boolean collides(Map<String, String> artifacts, String path, String workspacePath) {
        String existing = artifacts.get(path);
        if (existing != null) {
            return !existing.equals(workspacePath);
        }
        for (String other : artifacts.keySet()) {
            if (other.startsWith(path + "/") || path.startsWith(other + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identifies a test run: the same test may be in several reports, e.g. of several modules, each with their own
     * attachments. Their suites are merged, so they are told apart by when they started instead.
     */
    static String key(String className, String name, long startTime) {
        return testKey(className, name) + '@' + startTime;
    }

    /** Identifies a test, whichever run of it. */
    static String testKey(String className, String name) {
        return className + '#' + name;
    }

    /** Whether a property holds the path of a file attached to the test, rather than a report entry. */
    static boolean isAttachmentProperty(String key) {
        if (!key.startsWith(PROPERTY_PREFIX)) {
            return false;
        }
        String n = key.substring(PROPERTY_PREFIX.length());
        return !n.isEmpty() && n.charAt(0) != '0' && n.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** A file attached to a test, found on the agent. */
    record Attachment(
            String className,
            String name,
            long startTime,
            String property,
            String path,
            String workspacePath,
            long size,
            int report)
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
            Path root = Path.of(workspace).toRealPath();
            for (Map.Entry<String, String> e : artifacts.entrySet()) {
                Path target = staging.toPath().resolve(e.getKey());
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                copyRegularFile(root, Path.of(e.getValue()), target);
            }
            return null;
        }
    }

    /** Finds the attachments of the Open Test Reporting reports matching a pattern. */
    /**
     * @param runs how many times each test with attachments ran, by {@link #testKey}, including runs without
     *             attachments
     */
    record Collected(List<Attachment> attachments, Map<String, Integer> runs) implements Serializable {}

    private static final class Collect extends MasterToSlaveFileCallable<Collected> {
        private static final long serialVersionUID = 1L;

        private final String testResults;
        private final boolean keepTestNames;
        private final long filesTimestamp;

        @CheckForNull
        private final PipelineTestDetails pipelineTestDetails;

        Collect(
                String testResults,
                boolean keepTestNames,
                long filesTimestamp,
                @CheckForNull PipelineTestDetails pipelineTestDetails) {
            this.testResults = testResults;
            this.keepTestNames = keepTestNames;
            this.filesTimestamp = filesTimestamp;
            this.pipelineTestDetails = pipelineTestDetails;
        }

        @Override
        public Collected invoke(File ws, VirtualChannel channel) throws IOException {
            DirectoryScanner ds = Util.createFileSet(ws, testResults).getDirectoryScanner();
            Path root = ws.toPath().toRealPath();
            List<Attachment> found = new ArrayList<>();
            Map<String, Integer> runs = new HashMap<>();
            List<Path> dirs = new ArrayList<>();
            List<OpenTestReportConverter.TestRun> reports = new ArrayList<>();
            for (String name : ds.getIncludedFiles()) {
                File report = new File(ws, name);
                if (filesTimestamp != Long.MIN_VALUE
                        && Files.getLastModifiedTime(report.toPath()).toMillis()
                                < filesTimestamp - TestResult.FILE_TIME_PRECISION_MARGIN) {
                    // not parsed either
                    continue;
                }
                if (OtrReader.detect(report) == null) {
                    continue;
                }
                dirs.add(report.getParentFile().toPath().toRealPath());
                // converted just like the results, so that the test names match
                reports.add(new OpenTestReportConverter.TestRun(OpenTestReportConverter.parse(
                        report, StdioRetention.NONE, true, keepTestNames, pipelineTestDetails)));
            }
            OpenTestReportConverter.Reruns reruns = OpenTestReportConverter.mergeReruns(reports);
            for (SuiteResult suite : reruns.suites()) {
                for (CaseResult c : suite.getCases()) {
                    runs.merge(testKey(c.getClassName(), c.getName()), 1, Integer::sum);
                }
            }
            for (int index = 0; index < reports.size(); index++) {
                Path dir = dirs.get(index);
                for (SuiteResult suite : reports.get(index).suites()) {
                    for (CaseResult c : suite.getCases()) {
                        // the attachments of every attempt of a test that was rerun go with the result kept
                        long startTime = reruns.attempts().getOrDefault(c, c).getStartTime();
                        for (Map.Entry<String, String> property :
                                c.getProperties().entrySet()) {
                            if (!isAttachmentProperty(property.getKey())) {
                                continue;
                            }
                            Path file = resolve(root, dir, property.getValue());
                            if (file != null) {
                                found.add(new Attachment(
                                        c.getClassName(),
                                        c.getName(),
                                        startTime,
                                        property.getValue(),
                                        dir.relativize(file).toString().replace(File.separatorChar, '/'),
                                        root.relativize(file).toString().replace(File.separatorChar, '/'),
                                        Files.size(file),
                                        index + 1));
                            }
                        }
                    }
                }
            }
            Set<String> attached = new HashSet<>();
            for (Attachment a : found) {
                attached.add(testKey(a.className(), a.name()));
            }
            runs.keySet().retainAll(attached);
            return new Collected(found, runs);
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

    /**
     * Copies a file found in the workspace earlier, without following a symbolic link it, or a directory above it, may
     * have been replaced with since.
     *
     * @param root the real path of the workspace
     * @param relative the path of the file relative to the workspace
     */
    @Restricted(NoExternalUse.class)
    public static void copyRegularFile(Path root, Path relative, Path target) throws IOException {
        Path path = relative.normalize();
        if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")) {
            throw new IOException(relative + " is not inside " + root);
        }
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(root)) {
            if (dir instanceof SecureDirectoryStream<Path> secure) {
                copySecurely(secure, path, target);
                return;
            }
        }
        copyByPath(root, path, target);
    }

    /** Opens each directory relative to the one above it, so that none can be swapped for a symbolic link. */
    static void copySecurely(SecureDirectoryStream<Path> root, Path relative, Path target) throws IOException {
        SecureDirectoryStream<Path> dir = root;
        try {
            for (int i = 0; i < relative.getNameCount() - 1; i++) {
                SecureDirectoryStream<Path> child =
                        dir.newDirectoryStream(relative.getName(i), LinkOption.NOFOLLOW_LINKS);
                if (dir != root) {
                    dir.close();
                }
                dir = child;
            }
            try (SeekableByteChannel channel = dir.newByteChannel(
                            relative.getFileName(), Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                    InputStream in = Channels.newInputStream(channel)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (dir != root) {
                dir.close();
            }
        }
    }

    /**
     * Where directories cannot be opened relative to each other, e.g. on Windows: checks the directory, then opens the
     * file, which leaves a short window to swap a directory for a symbolic link.
     */
    static void copyByPath(Path root, Path relative, Path target) throws IOException {
        Path source = root.resolve(relative);
        Path parent = source.getParent();
        if (parent == null || !parent.toRealPath().startsWith(root)) {
            throw new IOException(source + " is not inside " + root);
        }
        try (InputStream in = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
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
        /**
         * The flow node of the step that recorded the results, as the same tests may be recorded by several steps
         * (e.g. in parallel stages), each with their own attachments; {@code null} outside of Pipelines.
         */
        @CheckForNull
        private final String nodeId;

        /** By {@link #key test run}. */
        private final Map<String, List<Archived>> attachments;

        /** How many times each test with attachments ran, by {@link #testKey test}. */
        private final Map<String, Integer> runs;

        Data(@CheckForNull String nodeId, Map<String, List<Archived>> attachments, Map<String, Integer> runs) {
            this.nodeId = nodeId;
            this.attachments = attachments;
            this.runs = runs;
        }

        @Override
        public List<? extends TestAction> getTestAction(TestObject testObject) {
            if (!(testObject instanceof CaseResult c)) {
                return Collections.emptyList();
            }
            String flowNodeId = c.getFlowNodeId();
            if (flowNodeId != null && !flowNodeId.equals(nodeId)) {
                return Collections.emptyList();
            }
            // without a start time, the runs of a test cannot be told apart: their attachments share a key
            List<Archived> archived =
                    c.getStartTime() < 0 ? null : attachments.get(key(c.getClassName(), c.getName(), c.getStartTime()));
            if (archived == null && (flowNodeId == null || c.getStartTime() < 0)) {
                // result storage need not keep which step recorded a test, nor when it started
                archived = byName(c);
            }
            return archived == null ? Collections.emptyList() : List.of(new AttachmentsAction(c, archived));
        }

        /** The attachments of a test by its name only, if it ran just once in the whole build. */
        @CheckForNull
        private List<Archived> byName(CaseResult c) {
            String test = testKey(c.getClassName(), c.getName());
            if (runs == null || runs.getOrDefault(test, 0) != 1) {
                return null;
            }
            SuiteResult suite = c.getSuiteResult();
            TestResult result = suite != null ? suite.getParent() : null;
            if (result != null
                    && result.getParentAction() instanceof TestResultAction action
                    && action.getData() != null) {
                for (TestResultAction.Data data : action.getData()) {
                    if (data != this
                            && data instanceof Data other
                            && other.runs != null
                            && other.runs.containsKey(test)) {
                        // also attached files when recorded by another step, e.g. in another stage
                        return null;
                    }
                }
            }
            if (suite != null
                    && suite.getCases().stream()
                                    .filter(o -> o.getClassName().equals(c.getClassName())
                                            && o.getName().equals(c.getName()))
                                    .count()
                            > 1) {
                // recorded by several steps, without attachments in the others
                return null;
            }
            // the key of its only run
            String prefix = test + '@';
            for (Map.Entry<String, List<Archived>> e : attachments.entrySet()) {
                String key = e.getKey();
                if (key.startsWith(prefix) && key.lastIndexOf('@') == prefix.length() - 1) {
                    return e.getValue();
                }
            }
            return null;
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
            return isAttachmentProperty(key) && archived.stream().anyMatch(a -> a.source.equals(value));
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
