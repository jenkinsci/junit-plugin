package io.jenkins.plugins.analysis.junit;

import org.jenkinsci.test.acceptance.po.AbstractStep;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PageObject;
import org.jenkinsci.test.acceptance.po.PostBuildStep;

/**
 * {@link PageObject} representing the publish junit post build action in the freestyle job configuration.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class JUnitJobConfiguration extends AbstractStep implements PostBuildStep {
    private final Control retainLogStandardOutputError = control("/keepLongStdio");
    private final Control allowEmptyResults = control("/allowEmptyResults");
    private final Control skipPublishingChecks = control("/skipPublishingChecks");
    private final Control skipMarkingBuildAsUnstableOnTestFailure = control("/skipMarkingBuildUnstable");
    private final Control healthScaleFactor = control("/healthScaleFactor");

    public final Control testResults = control("testResults");

    /**
     * Creates a new page object representing the junit summary on the build page of a job.
     *
     * @param parent a created job
     * @param path path of the
     */
    public JUnitJobConfiguration(Job parent, String path) {
        super(parent, path);
    }

    public void setRetainLogStandardOutputError(boolean shouldRetainLogStandardOutputError) {
        this.retainLogStandardOutputError.check(shouldRetainLogStandardOutputError);
    }

    public void setAllowEmptyResults(boolean shouldAllowEmptyResults) {
        this.allowEmptyResults.check(shouldAllowEmptyResults);
    }

    public void setSkipPublishingChecks(boolean shouldSkipPublishingChecks) {
        this.retainLogStandardOutputError.check(shouldSkipPublishingChecks);
    }

    public void setSkipMarkingBuildAsUnstableOnTestFailure(boolean shouldSkipMarkingBuildAsUnstableOnTestFailure) {
        this.skipMarkingBuildAsUnstableOnTestFailure.check(shouldSkipMarkingBuildAsUnstableOnTestFailure);
    }

    public void setHealthScaleFactor(String value) {
        this.healthScaleFactor.set(value);
    }

    private boolean isChecked(final Control control) {
        return control.resolve().isSelected();
    }
}
