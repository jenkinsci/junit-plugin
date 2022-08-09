package io.jenkins.plugins.junit.checks;

import edu.hm.hafner.util.VisibleForTesting;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultSummary;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.api.TruncatedString;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.List;

@Restricted(NoExternalUse.class)
public class JUnitChecksPublisher {
    public static final String SEPARATOR = ", ";

    private final Run run;
    private final String checksName;
    private final TestResult result;
    private final TestResultSummary summary;

    public JUnitChecksPublisher(final Run run, final String checksName, final TestResult result, final TestResultSummary summary) {
        this.run = run;
        this.checksName = checksName;
        this.result = result;
        this.summary = summary;
    }

    public void publishChecks(TaskListener listener) {
        ChecksPublisher publisher = ChecksPublisherFactory.fromRun(run, listener);
        publisher.publish(extractChecksDetails());
    }

    @VisibleForTesting
    ChecksDetails extractChecksDetails() {
        String testsURL = DisplayURLProvider.get().getTestsURL(run);
        ChecksOutput output = new ChecksOutput.ChecksOutputBuilder()
                .withTitle(extractChecksTitle())
                .withSummary("<sub>Send us [feedback](https://github.com/jenkinsci/junit-plugin/issues)")
                .withText(extractChecksText(testsURL))
                .build();

        return new ChecksDetails.ChecksDetailsBuilder()
                .withName(checksName)
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(summary.getFailCount() > 0 ? ChecksConclusion.FAILURE : ChecksConclusion.SUCCESS)
                .withDetailsURL(testsURL)
                .withOutput(output)
                .build();
    }

    private TruncatedString extractChecksText(String testsURL) {
        TruncatedString.Builder builder = new TruncatedString.Builder()
                .withTruncationText(String.format("%nmore test results are not shown here, view them on [Jenkins](%s)", testsURL));

        if (summary.getFailCount() > 0) {
            result.getFailedTests().stream()
                    .map(this::mapFailedTestToTestReport)
                    .forEach(builder::addText);
        }

        return builder.build();
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

        if (summary.getTotalCount() == 0) {
            return "No test results found";
        }

        StringBuilder builder = new StringBuilder();

        if (summary.getFailCount() == 1) {
            CaseResult failedTest = result.getFailedTests().get(0);
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
