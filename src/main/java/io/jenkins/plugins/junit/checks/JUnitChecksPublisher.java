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

    // cap to avoid hitting check API message limit
    private static final int MAX_MSG_SIZE_TO_CHECKS_API = 65535;
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
                .withName(action.getChecksName())
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

            for (CaseResult failedTest: failedTests) {
                String testReport = mapFailedTestToTestReport(failedTest);
                int messageSize = testReport.length() + builder.toString().length();
                // to ensure text size is withing check API message limit
                if (messageSize > (MAX_MSG_SIZE_TO_CHECKS_API - 1024)){
                    builder.append("\n")
                            .append("more test results are not shown here, view them on [Jenkins](")
                            .append(testsURL).append(")");
                    break;
                }
                builder.append(testReport);
            }
        }

        return builder.toString();
    }

    private String mapFailedTestToTestReport(CaseResult failedTest) {
        StringBuilder builder = new StringBuilder();
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
        return builder.toString();
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
