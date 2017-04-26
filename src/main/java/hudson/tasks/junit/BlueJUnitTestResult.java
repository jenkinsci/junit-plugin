package hudson.tasks.junit;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.blueocean.commons.ServiceException.NotFoundException;
import io.jenkins.blueocean.rest.Reachable;
import io.jenkins.blueocean.rest.factory.BlueTestResultFactory;
import io.jenkins.blueocean.rest.hal.Link;
import io.jenkins.blueocean.rest.model.BlueTestResult;

import javax.annotation.Nullable;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Implementation to provide CaseResults to for Blue Ocean's REST API
 */
public class BlueJUnitTestResult extends BlueTestResult {

    protected final CaseResult testResult;

    public BlueJUnitTestResult(CaseResult testResult, Link parent) {
        super(parent);
        this.testResult = testResult;
    }

    @Override
    public String getName() {
        return testResult.getName() + " – " + testResult.getClassName();
    }

    @Override
    public Status getStatus() {
        Status status;
        switch (testResult.getStatus()) {
            case SKIPPED:
                status = Status.SKIPPED;
                break;
            case FAILED:
            case REGRESSION:
                status = Status.FAILED;
                break;
            case PASSED:
            case FIXED:
                status = Status.PASSED;
                break;
            default:
                status = Status.UNKNOWN;
                break;
        }
        return status;
    }

    @Override
    public State getTestState() {
        State state;
        switch (testResult.getStatus()) {
            case REGRESSION:
                state = State.REGRESSION;
                break;
            case FIXED:
                state = State.FIXED;
                break;
            default:
                state = State.UNKNOWN;
        }
        return state;
    }

    @Override
    public float getDuration() {
        return testResult.getDuration();
    }

    @Override
    public String getErrorStackTrace() {
        return testResult.getErrorStackTrace();
    }

    @Override
    public String getErrorDetails() {
        return testResult.getErrorDetails();
    }

    @Override
    protected String getUniqueId() {
        return testResult.getId();
    }

    @Override
    public int getAge() {
        int age;
        if (!testResult.isPassed() && testResult.getRun() != null) {
            age = testResult.getRun().getNumber() - testResult.getFailedSince() + 1;
        } else {
            age = 0;
        }
        return age;
    }

    @Override
    public String getStdErr() {
        return serveLog(testResult.getStderr());
    }

    @Override
    public String getStdOut() {
        return serveLog(testResult.getStdout());
    }

    private String serveLog(String log) {
        if (isEmpty(log)) {
            throw new NotFoundException("No log");
        }
        return log;
    }

    @Extension
    public static class FactoryImpl extends BlueTestResultFactory {
        @Override
        public Result getBlueTestResults(Run<?, ?> run, final Reachable parent) {
            TestResultAction action = run.getAction(TestResultAction.class);
            if (action != null) {
                Iterable<CaseResult> allCaseResults = Iterables.concat(action.getFailedTests(), action.getSkippedTests(), action.getPassedTests());
                return Result.of(Iterables.transform(allCaseResults, new Function<CaseResult, BlueTestResult>() {
                    @Override
                    public BlueTestResult apply(@Nullable CaseResult input) {
                        return new BlueJUnitTestResult(input, parent.getLink());
                    }
                }));
            } else {
                return Result.notFound();
            }
        }
    }
}
