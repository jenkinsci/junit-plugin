/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt,
 * Tom Huybrechts, Yahoo!, Inc., Richard Hierlmeier
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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.test.PipelineTestDetails;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.junit.checks.JUnitChecksPublisher;
import io.jenkins.plugins.junit.storage.FileJunitTestResultStorage;
import io.jenkins.plugins.junit.storage.JunitTestResultStorage;
import jenkins.tasks.SimpleBuildStep;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Generates HTML report from JUnit test result XML files.
 *
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends Recorder implements SimpleBuildStep, JUnitTask {

    private static final Logger LOGGER = Logger.getLogger(JUnitResultArchiver.class.getName());

    /**
     * {@link FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String testResults;

    /**
     * Whether to complete test stdout/stderr even if this is huge.
     */
    private String stdioRetention;

    private boolean keepProperties;
    /**
     * {@link TestDataPublisher}s configured for this archiver, to process the recorded data.
     * For compatibility reasons, can be null.
     * @since 1.320
     */
    private DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers;

    private Double healthScaleFactor;

    /**
     * If true, don't throw exception on missing test results or no files found.
     */
    private boolean allowEmptyResults;
    private boolean skipPublishingChecks;
    private String checksName;
    /**
     * If true, the run won't be marked as unstable if there are failing tests. Only the stage will be marked as unstable.
     */
    private boolean skipMarkingBuildUnstable;

    private boolean skipOldReports;

    private static final String DEFAULT_CHECKS_NAME = "Tests";

    @DataBoundConstructor
    public JUnitResultArchiver(String testResults) {
        this.testResults = testResults;
    }

    @Deprecated
    public JUnitResultArchiver(String testResults,
            DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
        this(testResults, false, testDataPublishers);
    }

    @Deprecated
    public JUnitResultArchiver(
            String testResults,
            boolean keepLongStdio,
            DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
        this(testResults, keepLongStdio, false, testDataPublishers, 1.0);
    }

    @Deprecated
    public JUnitResultArchiver(
            String testResults,
            boolean keepLongStdio,
            boolean keepProperties,
            DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers,
            double healthScaleFactor) {
        this.testResults = testResults;
        setKeepLongStdio(keepLongStdio);
        setKeepProperties(keepProperties);
        setTestDataPublishers(testDataPublishers == null ? Collections.emptyList() : testDataPublishers);
        setHealthScaleFactor(healthScaleFactor);
        setAllowEmptyResults(false);
    }

    @Deprecated
    private TestResult parse(String expandedTestResults, Run<?,?> run, @NonNull FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException
    {
        return parse(this, null, expandedTestResults, run, workspace, launcher, listener);

    }

    private static TestResult parse(@NonNull JUnitTask task, PipelineTestDetails pipelineTestDetails,
                                    String expandedTestResults, Run<?,?> run, @NonNull FilePath workspace,
                                    Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        return new JUnitParser(task.getParsedStdioRetention(), task.isKeepProperties(), task.isAllowEmptyResults(), task.isSkipOldReports())
                .parseResult(expandedTestResults, run, pipelineTestDetails, workspace, launcher, listener);
    }

    @Deprecated
    protected TestResult parse(String expandedTestResults, AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException
    {
        final FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new IllegalArgumentException("The provided build has no workspace");
        }
        return parse(expandedTestResults, build, workspace, launcher, listener);
    }

    @Override
    public void perform(Run build, FilePath workspace, Launcher launcher,
            TaskListener listener) throws InterruptedException, IOException {
        if (parseAndSummarize(this, null, build, workspace, launcher, listener).getFailCount() > 0 && !skipMarkingBuildUnstable) {
            build.setResult(Result.UNSTABLE);
        }
    }

    /** @deprecated use {@link #parseAndSummarize} instead */
    @Deprecated
    public static TestResultAction parseAndAttach(@NonNull JUnitTask task, PipelineTestDetails pipelineTestDetails,
                                                  Run build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        listener.getLogger().println(Messages.JUnitResultArchiver_Recording());

        final String testResults = build.getEnvironment(listener).expand(task.getTestResults());

        TestResult result = parse(task, pipelineTestDetails, testResults, build, workspace, launcher, listener);

        synchronized (build) {
            // TODO can the build argument be omitted now, or is it used prior to the call to addAction?
            TestResultAction action = build.getAction(TestResultAction.class);
            boolean appending;
            if (action == null) {
                appending = false;
                action = new TestResultAction(build, result, listener);
            } else {
                appending = true;
                result.freeze(action);
                action.mergeResult(result, listener);
            }
            action.setHealthScaleFactor(task.getHealthScaleFactor()); // overwrites previous value if appending
            if (result.isEmpty()) {
                if (build.getResult() == Result.FAILURE) {
                    // most likely a build failed before it gets to the test phase.
                    // don't report confusing error message.
                    return null;
                }
                if (task.isAllowEmptyResults()) {
                    // User allow empty results
                    listener.getLogger().println(Messages.JUnitResultArchiver_ResultIsEmpty());
                    return null;
                }
                // most likely a configuration error in the job - e.g. false pattern to match the JUnit result files
                throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());
            }

            // TODO: Move into JUnitParser [BUG 3123310]
            if (task.getTestDataPublishers() != null) {
                for (TestDataPublisher tdp : task.getTestDataPublishers()) {
                    Data d = tdp.contributeTestData(build, workspace, launcher, listener, result);
                    if (d != null) {
                        action.addData(d);
                    }
                }
            }

            if (appending) {
                build.save();
            } else {
                build.addAction(action);
            }

            return action;
        }
    }

    public static TestResultSummary parseAndSummarize(@NonNull JUnitTask task, PipelineTestDetails pipelineTestDetails,
                                                  Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (storage instanceof FileJunitTestResultStorage) {
            listener.getLogger().println(Messages.JUnitResultArchiver_Recording());
        } // else let storage decide what to print

        String testResults = build.getEnvironment(listener).expand(task.getTestResults());

        TestResult result;
        TestResultSummary summary;
        if (storage instanceof FileJunitTestResultStorage) {
            result = parse(task, pipelineTestDetails, testResults, build, workspace, launcher, listener);
            summary = null; // see below
        } else {
            result = new TestResult(storage.load(build.getParent().getFullName(), build.getNumber())); // irrelevant
            summary = new JUnitParser(task.getParsedStdioRetention(), task.isKeepProperties(), task.isAllowEmptyResults(), task.isSkipOldReports())
                    .summarizeResult(testResults, build, pipelineTestDetails, workspace, launcher, listener, storage);
        }

        synchronized (build) {
            // TODO can the build argument be omitted now, or is it used prior to the call to addAction?
            TestResultAction action = build.getAction(TestResultAction.class);
            boolean appending;
            if (action == null) {
                appending = false;
                action = new TestResultAction(build, result, listener);
            } else {
                appending = true;
                if (storage instanceof FileJunitTestResultStorage) {
                    result.freeze(action);
                    action.mergeResult(result, listener);
                }
            }
            if (summary == null) {
                assert storage instanceof FileJunitTestResultStorage;
                // Cannot do this above since the result has not yet been frozen.
                summary = new TestResultSummary(result);
            }
            action.setHealthScaleFactor(task.getHealthScaleFactor()); // overwrites previous value if appending
            if (summary.getTotalCount() == 0 && /* maybe a secondary effect */ build.getResult() != Result.FAILURE) {
                if (task.isAllowEmptyResults()) {
                    listener.getLogger().println(Messages.JUnitResultArchiver_ResultIsEmpty());
                } else {
                    throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());
                }
            }

            if (task.getTestDataPublishers() != null) {
                for (TestDataPublisher tdp : task.getTestDataPublishers()) {
                    Data d = tdp.contributeTestData(build, workspace, launcher, listener, result);
                    if (d != null) {
                        action.addData(d);
                    }
                }
            }

            if (appending) {
                build.save();
            } else if (summary.getTotalCount() > 0) {
                build.addAction(action);
            }

            if (!task.isSkipPublishingChecks()) {
                // If we haven't been provided with a checks name, and we have pipeline test details, set the checks name
                // to be a ' / '-joined string of the enclosing blocks names, plus 'Tests' at the start. If there are no
                // enclosing blocks, you'll end up with just 'Tests'.
                String checksName = task.getChecksName();
                if (checksName == null && pipelineTestDetails != null) {
                    List<String> checksComponents = new ArrayList<>(pipelineTestDetails.getEnclosingBlockNames());
                    checksComponents.add(DEFAULT_CHECKS_NAME);
                    Collections.reverse(checksComponents);
                    checksName = String.join(" / ", checksComponents);
                }
                if (Util.fixEmpty(checksName) == null) {
                    checksName = DEFAULT_CHECKS_NAME;
                }
                try {
                    new JUnitChecksPublisher(build, checksName, result, summary).publishChecks(listener);
                } catch (Exception x) {
                    Functions.printStackTrace(x, listener.error("Publishing JUnit checks failed:"));
                }
            }

            return summary;
        }
    }

    /**
     * Not actually used, but left for backward compatibility
     * @param ds Directory scanner.
     * @param buildTime Build Time.
     *
     * @return a {@link TestResult}.
     * @throws IOException if an error occurs.
     * @deprecated since 2009-08-10.
     */
    protected TestResult parseResult(DirectoryScanner ds, long buildTime)
            throws IOException {
        return new TestResult(buildTime, ds);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public String getTestResults() {
        return testResults;
    }

    @Override
    public double getHealthScaleFactor() {
        return healthScaleFactor == null ? 1.0 : healthScaleFactor;
    }

    /**
     * @param healthScaleFactor Health scale factor.
     *
     * @since 1.2-beta-1
     */
    @DataBoundSetter public final void setHealthScaleFactor(double healthScaleFactor) {
        this.healthScaleFactor = Math.max(0.0, healthScaleFactor);
    }

    @NonNull
    @Override
    public List<TestDataPublisher> getTestDataPublishers() {
        return testDataPublishers == null ? Collections.emptyList() : testDataPublishers;
    }

    /**
     * @param testDataPublishers Test data publishers.
     *
     * @since 1.2
     */
    @DataBoundSetter public final void setTestDataPublishers(@NonNull List<TestDataPublisher> testDataPublishers) {
        this.testDataPublishers = new DescribableList<>(Saveable.NOOP);
        this.testDataPublishers.addAll(testDataPublishers);
    }

    /**
     * @param keepLongStdio Whether to keep long stdio.
     *
     * @since 1.2-beta-1
     */
    @Deprecated
    @DataBoundSetter public final void setKeepLongStdio(boolean keepLongStdio) {
        this.stdioRetention = StdioRetention.fromKeepLongStdio(keepLongStdio).name();
    }

    @Deprecated
    public boolean isKeepLongStdio() {
        return StdioRetention.ALL == getParsedStdioRetention();
    }

    /**
     * @return the stdioRetention
     */
    @Override
    public String getStdioRetention() {
        return stdioRetention == null ? StdioRetention.DEFAULT.name() : stdioRetention;
    }

    /**
     * @param stdioRetention How to keep long stdio.
     */
    @DataBoundSetter public final void setStdioRetention(String stdioRetention) {
        this.stdioRetention = stdioRetention;
    }

    /**
     * @return the keepProperties.
     */
    @Override
    public boolean isKeepProperties() {
        return keepProperties;
    }

    @DataBoundSetter public final void setKeepProperties(boolean keepProperties) {
        this.keepProperties = keepProperties;
    }

    /**
     *
     * @return the allowEmptyResults
     */
    @Override
    public boolean isAllowEmptyResults() {
        return allowEmptyResults;
    }

    /**
     * Should we skip publishing checks to the checks API plugin.
     *
     * @return if publishing checks should be skipped, {@code false} otherwise
     */
    @Override
    public boolean isSkipPublishingChecks() {
        return skipPublishingChecks;
    }

    @DataBoundSetter
    public void setSkipPublishingChecks(boolean skipPublishingChecks) {
        this.skipPublishingChecks = skipPublishingChecks;
    }

    @Override
    public String getChecksName() {
        return checksName;
    }

    @DataBoundSetter
    public void setChecksName(String checksName) {
        this.checksName = checksName;
    }

    @DataBoundSetter public final void setAllowEmptyResults(boolean allowEmptyResults) {
        this.allowEmptyResults = allowEmptyResults;
    }

    public boolean isSkipMarkingBuildUnstable() {
        return skipMarkingBuildUnstable;
    }

    @DataBoundSetter
    public void setSkipMarkingBuildUnstable(boolean skipMarkingBuildUnstable) {
        this.skipMarkingBuildUnstable = skipMarkingBuildUnstable;
    }

    @Override
    public boolean isSkipOldReports() {
        return this.skipOldReports;
    }

    @DataBoundSetter
    public void setSkipOldReports(boolean skipOldReports) {
        this.skipOldReports = skipOldReports;
    }

    private static final long serialVersionUID = 1L;

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return Messages.JUnitResultArchiver_DisplayName();
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         * @param project Project.
         * @param value File mask to validate.
         *
         * @return the validation result.
         * @throws IOException if an error occurs.
         */
        public FormValidation doCheckTestResults(
                @AncestorInPath AbstractProject project,
                @QueryParameter String value) throws IOException {
            if (project == null || !project.hasPermission(Item.WORKSPACE)) {
                return FormValidation.ok();
            }
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckHealthScaleFactor(@QueryParameter double value) {
            if (value < 1e-7) return FormValidation.warning("Test health reporting disabled");
            return FormValidation.ok(Messages.JUnitResultArchiver_HealthScaleFactorAnalysis(
                    1,
                    (int) (100.0 - Math.max(0.0, Math.min(100.0, 1 * value))),
                    5,
                    (int) (100.0 - Math.max(0.0, Math.min(100.0, 5 * value)))
            ));
        }

        public ListBoxModel doFillStdioRetentionItems() {
            ListBoxModel result = new ListBoxModel();

            for (StdioRetention option : StdioRetention.values()) {
                result.add(option.getDisplayName(), option.name());
            }

            return result;
        }
    }
}
