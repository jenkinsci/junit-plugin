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
import io.jenkins.plugins.util.JenkinsFacade;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class JUnitChecksPublisher {
    public static final String SEPARATOR = ", ";
    private final TestResultAction action;
    private final JenkinsFacade jenkinsFacade;
    private final TestResultSummary summary;

    public JUnitChecksPublisher(final TestResultAction action, final TestResultSummary summary) {
        this(action, summary, new JenkinsFacade());
    }

    @VisibleForTesting
    JUnitChecksPublisher(final TestResultAction action, final TestResultSummary summary, final JenkinsFacade jenkinsFacade) {
        this.jenkinsFacade = jenkinsFacade;
        this.action = action;
        this.summary = summary;
    }

    public void publishChecks(TaskListener listener) {
        ChecksPublisher publisher = ChecksPublisherFactory.fromRun(action.run, listener);
        publisher.publish(extractChecksDetails());
    }

    @VisibleForTesting
    ChecksDetails extractChecksDetails() {
        String text = extractChecksText();
        System.out.println(text);
        ChecksOutput output = new ChecksOutput.ChecksOutputBuilder()
                .withTitle(extractChecksTitle())
                .withSummary("<sub>Send us [feedback](https://github.com/jenkinsci/junit-plugin/issues)")
                .withText(text)
                .build();

        return new ChecksDetails.ChecksDetailsBuilder()
                .withName("Tests")
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(summary.getFailCount() > 0 ? ChecksConclusion.FAILURE : ChecksConclusion.SUCCESS)
                .withDetailsURL(jenkinsFacade.getAbsoluteUrl(action.run.getUrl(), action.getUrlName()))
                .withOutput(output)
                .build();
    }

    private String extractChecksText() {
        StringBuilder builder = new StringBuilder();
        if (summary.getFailCount() > 0) {
            List<CaseResult> failedTests = action.getResult().getFailedTests();
            
            if (failedTests.size() > 10) {
                failedTests = failedTests.subList(0, 9);
            }
            
            failedTests.forEach(failedTest -> mapFailedTestToTestReport(builder, failedTest));
            if (summary.getFailCount() > 10) {
                builder.append("\n")
                        .append(summary.getFailCount() - 10).append(" more test results are not shown here, view them on Jenkins");
            }
        }

        return builder.toString();
    }

    private void mapFailedTestToTestReport(StringBuilder builder, CaseResult failedTest) {
        builder.append("## <code>").append(failedTest.getTransformedFullDisplayName().trim()).append("</code>")
                .append("\n");

        if (StringUtils.isNotBlank(failedTest.getErrorDetails())) {
            builder.append("<pre><code>").append(failedTest.getErrorDetails().trim()).append("</code></pre>")
                    .append("\n");
        }
        if (StringUtils.isNotBlank(failedTest.getErrorStackTrace())) {
            builder.append("<details><summary>Stack trace</summary>").append("\n")
                    .append("<pre><code>").append(failedTest.getErrorStackTrace().trim()).append("</code></pre>")
                    .append("</details>")
                    .append("\n");
        }

        if (StringUtils.isNotBlank(failedTest.getStderr())) {
            builder.append("<details><summary>Standard error</summary>").append("\n")
                    .append("<pre><code>").append(failedTest.getStderr().trim()).append("</code></pre>")
                    .append("</details>")
                    .append("\n");
        }

        if (StringUtils.isNotBlank(failedTest.getStdout())) {
            builder.append("<details><summary>Standard out</summary>").append("\n")
                    .append("<pre><code>").append(failedTest.getStdout().trim()).append("</code></pre>")
                    .append("</details>")
                    .append("\n");
        }
        builder.append("\n");
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