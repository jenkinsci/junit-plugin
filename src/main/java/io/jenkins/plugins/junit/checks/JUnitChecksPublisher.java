package io.jenkins.plugins.junit.checks;

import edu.hm.hafner.util.VisibleForTesting;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultSummary;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class JUnitChecksPublisher {
    public static final String SEPARATOR = ", ";

    // arbitrary cap to avoid hitting any API limits and to limit the size of the page
    // can be tuned based on feedback
    private static final int MAX_RESULTS_TO_SEND_TO_CHECKS_API = 10;
    private final TestResultAction action;
    private final TestResultSummary summary;

    public JUnitChecksPublisher(final TestResultAction action, final TestResultSummary summary) {
        this.action = action;
        this.summary = summary;
    }

    public void publishChecks(TaskListener listener) {
        ChecksPublisher publisher = ChecksPublisherFactory.fromRun(action.run, listener);
        publisher.publish(extractChecksDetails());
    }

    @VisibleForTesting
    ChecksDetails extractChecksDetails() {
        String testsURL = DisplayURLProvider.get().getTestsURL(action.run);
        ChecksOutput output = new ChecksOutput.ChecksOutputBuilder()
                .withTitle(extractChecksTitle())
                .withSummary("<sub>Send us [feedback](https://github.com/jenkinsci/junit-plugin/issues)")
                .withText(extractChecksText(testsURL))
                .build();

        return new ChecksDetails.ChecksDetailsBuilder()
                .withName(action.getChecksName() != null ? action.getChecksName() : "Tests")
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(summary.getFailCount() > 0 ? ChecksConclusion.FAILURE : ChecksConclusion.SUCCESS)
                .withDetailsURL(testsURL)
                .withOutput(output)
                .build();
    }

    private String extractChecksText(String testsURL) {
        StringBuilder builder = new StringBuilder();
        if (summary.getFailCount() > 0) {
            List<CaseResult> failedTests = action.getResult().getFailedTests();
            
            if (failedTests.size() > MAX_RESULTS_TO_SEND_TO_CHECKS_API) {
                failedTests = failedTests.subList(0, MAX_RESULTS_TO_SEND_TO_CHECKS_API - 1);
            }
            
            failedTests.forEach(failedTest -> mapFailedTestToTestReport(builder, failedTest));
            if (summary.getFailCount() > MAX_RESULTS_TO_SEND_TO_CHECKS_API) {
                builder.append("\n")
                        .append(summary.getFailCount() - MAX_RESULTS_TO_SEND_TO_CHECKS_API)
                        .append(" more test results are not shown here, view them on [Jenkins](")
                        .append(testsURL).append(")");
            }
        }

        return builder.toString();
    }

    private void mapFailedTestToTestReport(StringBuilder builder, CaseResult failedTest) {
        builder.append("## `").append(failedTest.getTransformedFullDisplayName().trim()).append("`")
                .append("\n");

        if (StringUtils.isNotBlank(failedTest.getErrorDetails())) {
            builder.append(codeTextFencedBlock(failedTest.getErrorDetails()))
                    .append("\n");
        }
        if (StringUtils.isNotBlank(failedTest.getErrorStackTrace())) {
            builder.append("<details><summary>Stack trace</summary>\n")
                    .append(codeTextFencedBlock(failedTest.getErrorStackTrace()))
                    .append("</details>\n");
        }

        if (StringUtils.isNotBlank(failedTest.getStderr())) {
            builder.append("<details><summary>Standard error</summary>\n")
                    .append(codeTextFencedBlock(failedTest.getStderr()))
                    .append("</details>\n");
        }

        if (StringUtils.isNotBlank(failedTest.getStdout())) {
            builder.append("<details><summary>Standard out</summary>\n")
                    .append(codeTextFencedBlock(failedTest.getStdout()))
                    .append("</details>\n");
        }
        builder.append("\n");
    }
    
    private String codeTextFencedBlock(String body) {
        return "\n```text\n" + body.trim() + "\n```\n";
    }

    private String extractChecksTitle() {
        StringBuilder builder = new StringBuilder();

        if (summary.getFailCount() == 1) {
            CaseResult failedTest = action.getResult().getFailedTests().get(0);
            builder.append(failedTest.getTransformedFullDisplayName()).append(" failed");
            return builder.toString();
        }

        if (summary.getFailCount() > 0) {
            builder.append("failed: ").append(summary.getFailCount());
            if (summary.getSkipCount() > 0 || summary.getPassCount() > 0) {
                builder.append(SEPARATOR);
            }
        }

        if (summary.getSkipCount() > 0) {
            builder.append("skipped: ").append(summary.getSkipCount());

            if (summary.getPassCount() > 0) {
                builder.append(SEPARATOR);
            }
        }

        if (summary.getPassCount() > 0) {
            builder.append("passed: ").append(summary.getPassCount());
        }


        return builder.toString();
    }
}
