package hudson.tasks.junit.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitTask;
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

import javax.annotation.Nonnull;
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
     * If true, retain a suite's complete stdout/stderr even if this is huge and the suite passed.
     * @since 1.358
     */
    private boolean keepLongStdio;

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

    @DataBoundConstructor
    public JUnitResultsStep(String testResults) {
        this.testResults = testResults;
    }

    public String getTestResults() {
        return testResults;
    }

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

    public @Nonnull
    List<TestDataPublisher> getTestDataPublishers() {
        return testDataPublishers == null ? Collections.emptyList() : testDataPublishers;
    }

    /**
     * @param testDataPublishers Test data publishers.
     *
     * @since 1.2
     */
    @DataBoundSetter public final void setTestDataPublishers(@Nonnull List<TestDataPublisher> testDataPublishers) {
        this.testDataPublishers = new DescribableList<>(Saveable.NOOP);
        this.testDataPublishers.addAll(testDataPublishers);
    }

    /**
     * @return the keepLongStdio.
     */
    public boolean isKeepLongStdio() {
        return keepLongStdio;
    }

    /**
     * @param keepLongStdio Whether to keep long stdio.
     *
     * @since 1.2-beta-1
     */
    @DataBoundSetter public final void setKeepLongStdio(boolean keepLongStdio) {
        this.keepLongStdio = keepLongStdio;
    }

    /**
     *
     * @return the allowEmptyResults
     */
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
        @Nonnull
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
