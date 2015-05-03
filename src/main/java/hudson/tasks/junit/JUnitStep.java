package hudson.tasks.junit;

import hudson.*;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Workflow step that archives JUnit Test results and returns a summary of counts
 * to the caller
 */
public class JUnitStep extends AbstractStepImpl {

    private static final Logger LOGGER = Logger.getLogger(JUnitStep.class.getName());

    @Nullable
    private final String testResults;

    private boolean keepLongStdio;

    @DataBoundConstructor
    public JUnitStep(@Nullable String testResults) {
        this.testResults = testResults;
    }

    @Nullable
    public String getTestResults() {
        return testResults;
    }

    public boolean isKeepLongStdio() {
        return keepLongStdio;
    }

    @DataBoundSetter
    public void setKeepLongStdio(boolean keepLongStdio) {
        this.keepLongStdio = keepLongStdio;
    }

    public static final class ExecutionImpl extends AbstractSynchronousStepExecution<TestResultSummary> {

        @Inject
        private transient JUnitStep step;


        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient FilePath workspace;

        @StepContextParameter
        private transient EnvVars env;

        @StepContextParameter
        private transient Run build;


        @Override
        protected TestResultSummary run() throws Exception {

            final long buildTime = build.getTimestamp().getTimeInMillis();
            final long timeOnMaster = System.currentTimeMillis();

            String location = step.testResults;
            if (location == null) {
                location = "**/target/surefire-reports/TEST-*.xml";
            }

            LOGGER.info("Looking for JUnit tests results in " + location + " in workspace " + workspace);

            TestResult result = workspace.act(new ParseResultCallable(location, buildTime, timeOnMaster, step.keepLongStdio));
            result.tally();

            // Check for empty tests
            if (result.isEmpty()) {
                // most likely a configuration error in the job - e.g. false pattern to match the JUnit result files
                throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());
            }

            // Add the results to the build
            synchronized (build) {
                TestResultAction action = build.getAction(TestResultAction.class);
                if (action == null) {
                    action = new TestResultAction(build, result, listener);
                    build.addAction(action);

                } else {
                    // add to existing results
                    result.freeze(action);
                    action.mergeResult(result, listener);
                    build.save();
                }
            }

            return new TestResultSummary(result);
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ExecutionImpl.class);
        }

        @Override
        public String getFunctionName() {
            return "archiveTestResults";
        }

        @Override
        public String getDisplayName() {
            return "Archive JUnit Test Results";
        }
    }


    private static final class ParseResultCallable implements FilePath.FileCallable<TestResult> {
        private final long buildTime;
        private final String testResults;
        private final long nowMaster;
        private final boolean keepLongStdio;

        private ParseResultCallable(String testResults, long buildTime, long nowMaster, boolean keepLongStdio) {
            this.buildTime = buildTime;
            this.testResults = testResults;
            this.nowMaster = nowMaster;
            this.keepLongStdio = keepLongStdio;
        }

        @Override
        public TestResult invoke(File ws, VirtualChannel channel) throws IOException {
            final long nowSlave = System.currentTimeMillis();

            FileSet fs = Util.createFileSet(ws, testResults);
            DirectoryScanner ds = fs.getDirectoryScanner();

            return new TestResult(buildTime + (nowSlave - nowMaster), ds, keepLongStdio);
        }

        @Override
        public void checkRoles(RoleChecker roleChecker) throws SecurityException {

        }
    }


}
