package hudson.tasks.junit.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitTask;
import hudson.tasks.junit.StdioRetention;
import hudson.tasks.junit.Messages;
import hudson.tasks.junit.TestDataPublisher;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.swing.tree.VariableHeightLayoutCache;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JUnitResultsStep extends Step implements JUnitTask {
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
     * If true, do not mangle test names in case running in multiple stages or parallel steps.
     */
    private boolean keepTestNames;

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

    @DataBoundConstructor
    public JUnitResultsStep(String testResults) {
        this.testResults = testResults;
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
    @DataBoundSetter
    public final void setHealthScaleFactor(double healthScaleFactor) {
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

    /**
     * @param keepProperties Whether to keep the properties
     */
    @DataBoundSetter public final void setKeepProperties(boolean keepProperties) {
        this.keepProperties = keepProperties;
    }

    /**
     * @return the keepTestNames.
     */
    public boolean isKeepTestNames() {
        return keepTestNames;
    }

    /**
     * @param keepTestNames Whether to avoid adding parallel stage name into test name.
     */
    @DataBoundSetter public final void setKeepTestNames(boolean keepTestNames) {
        this.keepTestNames = keepTestNames;
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
        return Util.fixEmpty(checksName);
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

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new JUnitResultsStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "junit";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return "Archive JUnit-formatted test results";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            Collections.addAll(context, FilePath.class, FlowNode.class, TaskListener.class, Launcher.class);
            return Collections.unmodifiableSet(context);
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

    }
}
