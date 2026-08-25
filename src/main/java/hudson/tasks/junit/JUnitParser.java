/*
 * The MIT License
 *
 * Copyright (c) 2009, Yahoo!, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks.junit;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.test.PipelineTestDetails;
import hudson.tasks.test.TestResultParser;
import io.jenkins.plugins.junit.storage.JunitTestResultStorage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.MasterToSlaveFileCallable;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

/**
 * Parse some JUnit xml files and generate a TestResult containing all the
 * results parsed.
 */
public class JUnitParser extends TestResultParser {

    private static final Logger LOGGER = Logger.getLogger(JUnitParser.class.getName());

    private final StdioRetention stdioRetention;
    private final boolean keepProperties;
    private final boolean allowEmptyResults;
    private final boolean keepTestNames;

    private final boolean skipOldReports;

    /** Generally unused, but present for extension compatibility. */
    @Deprecated
    public JUnitParser() {
        this(false, false, false, false);
    }

    /**
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @since 1.358
     */
    @Deprecated
    public JUnitParser(boolean keepLongStdio) {
        this(keepLongStdio, false, false, false);
    }

    /**
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @param allowEmptyResults if true, empty results are allowed
     * @since 1.10
     */
    @Deprecated
    public JUnitParser(boolean keepLongStdio, boolean allowEmptyResults) {
        this(StdioRetention.fromKeepLongStdio(keepLongStdio), false, allowEmptyResults, false, false);
    }

    @Deprecated
    public JUnitParser(
            boolean keepLongStdio, boolean keepProperties, boolean allowEmptyResults, boolean skipOldReports) {
        this(StdioRetention.fromKeepLongStdio(keepLongStdio), keepProperties, allowEmptyResults, skipOldReports, false);
    }

    @Deprecated
    public JUnitParser(
            StdioRetention stdioRetention, boolean keepProperties, boolean allowEmptyResults, boolean skipOldReports) {
        this(stdioRetention, keepProperties, allowEmptyResults, skipOldReports, false);
    }

    public JUnitParser(
            StdioRetention stdioRetention,
            boolean keepProperties,
            boolean allowEmptyResults,
            boolean skipOldReports,
            boolean keepTestNames) {
        this.stdioRetention = stdioRetention;
        this.keepProperties = keepProperties;
        this.allowEmptyResults = allowEmptyResults;
        this.keepTestNames = keepTestNames;
        this.skipOldReports = skipOldReports;
    }

    @Override
    public String getDisplayName() {
        return Messages.JUnitParser_DisplayName();
    }

    @Override
    public String getTestResultLocationMessage() {
        return Messages.JUnitParser_TestResultLocationMessage();
    }

    @Deprecated
    @Override
    public TestResult parse(String testResultLocations, AbstractBuild build, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        return (TestResult) super.parse(testResultLocations, build, launcher, listener);
    }

    @Deprecated
    @Override
    public TestResult parseResult(
            String testResultLocations, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        return parseResult(testResultLocations, build, null, workspace, launcher, listener);
    }

    /**
     * Parses the specified set of files and builds a {@link TestResult} object that represents them.
     *
     * <p>
     * The implementation is encouraged to do the following:
     *
     * <ul>
     * <li>
     * If the build is successful but GLOB didn't match anything, report that as an error. This is
     * to detect the error in GLOB. But don't do this if the build has already failed (for example,
     * think of a failure in SCM checkout.)
     *
     * <li>
     * Examine time stamp of test report files and if those are younger than the build, ignore them.
     * This is to ignore test reports created by earlier executions. Take the possible timestamp
     * difference in the controller/agent into account.
     * </ul>
     *
     * @param testResultLocations
     *      GLOB pattern relative to the {@code workspace} that
     *      specifies the locations of the test result files. Never null.
     * @param run
     *      Build for which these tests are parsed. Never null.
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     * @param workspace the workspace in which tests can be found
     * @param launcher
     *      Can be used to fork processes on the machine where the build is running. Never null.
     * @param listener
     *      Use this to report progress and other problems. Never null.
     *
     * @return a {@link TestResult} object representing the provided files and builds.
     *
     * @throws InterruptedException
     *      If the user cancels the build, it will be received as a thread interruption. Do not catch
     *      it, and instead just forward that through the call stack.
     * @throws IOException
     *      If you don't care about handling exceptions gracefully, you can just throw IOException
     *      and let the default exception handling in Hudson takes care of it.
     * @throws AbortException
     *      If you encounter an error that you handled gracefully, throw this exception and Hudson
     *      will not show a stack trace.
     * @since 1.22
     */
    @Override
    public TestResult parseResult(
            String testResultLocations,
            Run<?, ?> build,
            PipelineTestDetails pipelineTestDetails,
            FilePath workspace,
            Launcher launcher,
            TaskListener listener)
            throws InterruptedException, IOException {
        return workspace.act(new DirectParseResultCallable(
                testResultLocations,
                build,
                stdioRetention,
                keepProperties,
                allowEmptyResults,
                keepTestNames,
                pipelineTestDetails,
                listener,
                skipOldReports));
    }

    public TestResultSummary summarizeResult(
            String testResultLocations,
            Run<?, ?> build,
            PipelineTestDetails pipelineTestDetails,
            FilePath workspace,
            Launcher launcher,
            TaskListener listener,
            JunitTestResultStorage storage)
            throws InterruptedException, IOException {
        return workspace.act(new StorageParseResultCallable(
                testResultLocations,
                build,
                stdioRetention,
                keepProperties,
                allowEmptyResults,
                keepTestNames,
                pipelineTestDetails,
                listener,
                storage.createRemotePublisher(build),
                skipOldReports));
    }

    private abstract static class ParseResultCallable<T> extends MasterToSlaveFileCallable<T> {

        private static final Logger LOGGER = Logger.getLogger(ParseResultCallable.class.getName());

        private final long buildStartTimeInMillis;

        private final long buildTimeInMillis;
        private final String testResults;
        private final long nowMaster;
        private final StdioRetention stdioRetention;
        private final boolean keepProperties;
        private final boolean keepTestNames;
        private final boolean allowEmptyResults;
        private final PipelineTestDetails pipelineTestDetails;
        private final TaskListener listener;

        private boolean skipOldReports;

        private ParseResultCallable(
                String testResults,
                Run<?, ?> build,
                StdioRetention stdioRetention,
                boolean keepProperties,
                boolean allowEmptyResults,
                boolean keepTestNames,
                PipelineTestDetails pipelineTestDetails,
                TaskListener listener,
                boolean skipOldReports) {
            this.buildStartTimeInMillis = build.getStartTimeInMillis();
            this.buildTimeInMillis = build.getTimeInMillis();
            this.testResults = testResults;
            this.nowMaster = System.currentTimeMillis();
            this.stdioRetention = stdioRetention;
            this.keepProperties = keepProperties;
            this.keepTestNames = keepTestNames;
            this.allowEmptyResults = allowEmptyResults;
            this.pipelineTestDetails = pipelineTestDetails;
            this.listener = listener;
            this.skipOldReports = skipOldReports;
        }

        @Override
        public T invoke(File ws, VirtualChannel channel) throws IOException {
            final long nowSlave = System.currentTimeMillis();
            FileSet fs = Util.createFileSet(ws, testResults);
            DirectoryScanner ds = fs.getDirectoryScanner();
            TestResult result;
            String[] files = ds.getIncludedFiles();
            if (files.length > 0) {
                // not sure we can rely seriously on those timestamp so let's take the smaller one...
                long filesTimestamp = Math.min(buildStartTimeInMillis, buildTimeInMillis);
                // previous mode buildStartTimeInMillis + (nowSlave - nowMaster);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("buildStartTimeInMillis:" + buildStartTimeInMillis
                            + ",buildTimeInMillis:" + buildTimeInMillis + ",filesTimestamp:" + filesTimestamp
                            + ",nowSlave:"
                            + nowSlave + ",nowMaster:" + nowMaster);
                }
                result = new TestResult(
                        filesTimestamp,
                        ds,
                        stdioRetention,
                        keepProperties,
                        keepTestNames,
                        pipelineTestDetails,
                        skipOldReports);
                result.tally();
            } else {
                if (this.allowEmptyResults) {
                    listener.getLogger().println(Messages.JUnitResultArchiver_NoTestReportFound());
                    result = new TestResult();
                } else {
                    // no test result. Most likely a configuration error or fatal problem
                    throw new AbortException(Messages.JUnitResultArchiver_NoTestReportFound());
                }
            }
            return handle(result);
        }

        protected abstract T handle(TestResult result) throws IOException;
    }

    private static final class DirectParseResultCallable extends ParseResultCallable<TestResult> {

        DirectParseResultCallable(
                String testResults,
                Run<?, ?> build,
                StdioRetention stdioRetention,
                boolean keepProperties,
                boolean allowEmptyResults,
                boolean keepTestNames,
                PipelineTestDetails pipelineTestDetails,
                TaskListener listener,
                boolean skipOldReports) {
            super(
                    testResults,
                    build,
                    stdioRetention,
                    keepProperties,
                    allowEmptyResults,
                    keepTestNames,
                    pipelineTestDetails,
                    listener,
                    skipOldReports);
        }

        @Override
        protected TestResult handle(TestResult result) throws IOException {
            return result;
        }
    }

    private static final class StorageParseResultCallable extends ParseResultCallable<TestResultSummary> {

        private final JunitTestResultStorage.RemotePublisher publisher;

        StorageParseResultCallable(
                String testResults,
                Run<?, ?> build,
                StdioRetention stdioRetention,
                boolean keepProperties,
                boolean allowEmptyResults,
                boolean keepTestNames,
                PipelineTestDetails pipelineTestDetails,
                TaskListener listener,
                JunitTestResultStorage.RemotePublisher publisher,
                boolean skipOldReports) {
            super(
                    testResults,
                    build,
                    stdioRetention,
                    keepProperties,
                    allowEmptyResults,
                    keepTestNames,
                    pipelineTestDetails,
                    listener,
                    skipOldReports);
            this.publisher = publisher;
        }

        @Override
        protected TestResultSummary handle(TestResult result) throws IOException {
            publisher.publish(result, super.listener);
            return new TestResultSummary(result);
        }
    }
}
