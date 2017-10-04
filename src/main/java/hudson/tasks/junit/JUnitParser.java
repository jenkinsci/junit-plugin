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
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.test.TestResultParser;
import jenkins.MasterToSlaveFileCallable;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.io.IOException;

/**
 * Parse some JUnit xml files and generate a TestResult containing all the
 * results parsed.
 */
@Extension
public class JUnitParser extends TestResultParser {

    private final KeepStdioConfig config;
    private final boolean allowEmptyResults;

    /** TODO TestResultParser.all does not seem to ever be called so why must this be an Extension? */
    @Deprecated
    public JUnitParser() {
        this(false, false);
    }

    /**
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @since 1.358
     */
    @Deprecated
    public JUnitParser(boolean keepLongStdio) {
        this.config = KeepStdioConfig.defaults(keepLongStdio);
        this.allowEmptyResults = false;
    }

    /**
     * @param config stdio configuration.
     * @param allowEmptyResults if true, empty results are allowed
     * @since 1.23
     */
    public JUnitParser(KeepStdioConfig config, boolean allowEmptyResults) {
        this.config = config;
        this.allowEmptyResults = allowEmptyResults;
    }

    /**
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @param allowEmptyResults if true, empty results are allowed
     * @since 1.10
     */
    public JUnitParser(boolean keepLongStdio, boolean allowEmptyResults) {
        this.config = KeepStdioConfig.defaults(keepLongStdio);
        this.allowEmptyResults = allowEmptyResults;
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
    @Override public TestResult parse(String testResultLocations, AbstractBuild build, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        return (TestResult) super.parse(testResultLocations, build, launcher, listener);
    }

    @Override
    public TestResult parseResult(String testResultLocations,
                                       Run<?,?> build, FilePath workspace, Launcher launcher,
                                       TaskListener listener)
            throws InterruptedException, IOException
    {
        final long buildTime = build.getTimestamp().getTimeInMillis();
        final long timeOnMaster = System.currentTimeMillis();

        // [BUG 3123310] TODO - Test Result Refactor: review and fix TestDataPublisher/TestAction subsystem]
        // also get code that deals with testDataPublishers from JUnitResultArchiver.perform

        return workspace.act(new ParseResultCallable(testResultLocations, buildTime,
                                                     timeOnMaster, config, allowEmptyResults));
    }

    private static final class ParseResultCallable extends MasterToSlaveFileCallable<TestResult> {
        private final long buildTime;
        private final String testResults;
        private final long nowMaster;
        private final KeepStdioConfig config;
        private final boolean allowEmptyResults;

        private ParseResultCallable(String testResults, long buildTime, long nowMaster, KeepStdioConfig config,
                                    boolean allowEmptyResults) {
            this.buildTime = buildTime;
            this.testResults = testResults;
            this.nowMaster = nowMaster;
            this.config = config;
            this.allowEmptyResults = allowEmptyResults;
        }

        public TestResult invoke(File ws, VirtualChannel channel) throws IOException {
            final long nowSlave = System.currentTimeMillis();

            FileSet fs = Util.createFileSet(ws, testResults);
            DirectoryScanner ds = fs.getDirectoryScanner();
            TestResult result = null;

            String[] files = ds.getIncludedFiles();
            if (files.length > 0) {
                result = new TestResult(buildTime + (nowSlave - nowMaster), ds, config);
                result.tally();
            } else {
                if (this.allowEmptyResults) {
                    result = new TestResult();
                } else {
                    // no test result. Most likely a configuration
                    // error or fatal problem
                    throw new AbortException(Messages.JUnitResultArchiver_NoTestReportFound());
                }
            }
            return result;
        }
    }

}
