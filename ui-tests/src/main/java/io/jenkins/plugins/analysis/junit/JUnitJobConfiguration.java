package io.jenkins.plugins.analysis.junit;

import org.jenkinsci.test.acceptance.po.AbstractStep;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PageObject;
import org.jenkinsci.test.acceptance.po.PostBuildStep;

/**
 * {@link PageObject} representing the publish junit post build action in the freestyle job configuration.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@Describable("Publish JUnit test result report")
public class JUnitJobConfiguration extends AbstractStep implements PostBuildStep {
    private final Control retainLogStandardOutputError = control("/keepLongStdio");
    private final Control allowEmptyResults = control("/allowEmptyResults");
    private final Control skipPublishingChecks = control("/skipPublishingChecks");
    private final Control skipMarkingBuildAsUnstableOnTestFailure = control("/skipMarkingBuildUnstable");
    private final Control healthScaleFactor = control("/healthScaleFactor");
    private final Control testResults = control("testResults");

    /**
     * Creates a new page object representing the junit summary on the build page of a job.
     *
     * @param parent a created job
     * @param path path of the
     */
    public JUnitJobConfiguration(Job parent, String path) {
        super(parent, path);
    }

    /**
     * Set test results file.
     * @param value to set test results
     */
    public void setTestResults(String value) {
        this.testResults.set(value);
    }

    /**
     * Set checkbox to retain standard log output error.
     * @param shouldRetainLogStandardOutputError Check or uncheck checkbox
     */
    public void setRetainLogStandardOutputError(boolean shouldRetainLogStandardOutputError) {
        this.retainLogStandardOutputError.check(shouldRetainLogStandardOutputError);
    }

    /**
     * Set checkbox to allow empty results.
     * @param shouldAllowEmptyResults Check or uncheck checkbox
     */
    public void setAllowEmptyResults(boolean shouldAllowEmptyResults) {
        this.allowEmptyResults.check(shouldAllowEmptyResults);
    }

    /**
     * Set checkbox to skip publishing checks.
     * @param shouldSkipPublishingChecks Check or uncheck checkbox
     */
    public void setSkipPublishingChecks(boolean shouldSkipPublishingChecks) {
        this.retainLogStandardOutputError.check(shouldSkipPublishingChecks);
    }

    /**
     * Set checkbox to skip mark build as unstable on test failure.
     * @param shouldSkipMarkingBuildAsUnstableOnTestFailure Check or uncheck checkbox
     */
    public void setSkipMarkingBuildAsUnstableOnTestFailure(boolean shouldSkipMarkingBuildAsUnstableOnTestFailure) {
        this.skipMarkingBuildAsUnstableOnTestFailure.check(shouldSkipMarkingBuildAsUnstableOnTestFailure);
    }

    /**
     * Set input value of health scale factor.
     * @param value value to set the health scale factor
     */
    public void setHealthScaleFactor(String value) {
        this.healthScaleFactor.set(value);
    }
}
