package io.jenkins.plugins.analysis.junit;

import org.jenkinsci.test.acceptance.po.AbstractStep;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PostBuildStep;

public class JUnitBuildConfiguration extends AbstractStep implements PostBuildStep {
    private final Control retainLogStandardOutputError = control("/publisher[JUnitResultArchiver]/keepLongStdio");
    private final Control allowEmptyResults = control("/publisher[JUnitResultArchiver]/allowEmptyResults");
    private final Control skipPublishingChecks = control("/publisher[JUnitResultArchiver]/skipPublishingChecks");
    private final Control skipMarkingBuildAsUnstableOnTestFailure = control("/publisher[JUnitResultArchiver]/skipMarkingBuildUnstable");

    public JUnitBuildConfiguration(final Job parent, final String path) {
        super(parent, path);
    }

    public Control getRetainLogStandardOutputError() {
        return retainLogStandardOutputError;
    }

    public Control getSkipPublishingChecks() {
        return skipPublishingChecks;
    }

    public Control getAllowEmptyResults() {
        return allowEmptyResults;
    }

    public Control getSkipMarkingBuildAsUnstableOnTestFailure() {
        return skipMarkingBuildAsUnstableOnTestFailure;
    }

    public boolean isRetainLogOutputError() {
        return isChecked(retainLogStandardOutputError);
    }

    public boolean isAllowEmptyResults() {
        return isChecked(allowEmptyResults);
    }

    public boolean isSkipPublishingChecks() {
        return isChecked(skipPublishingChecks);
    }

    public boolean isSkipMarkingBuildAsUnstableOnTestFailure() {
        return isChecked(skipMarkingBuildAsUnstableOnTestFailure);
    }

    private boolean isChecked(final Control control) {
        return control.resolve().isSelected();
    }
}
