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
package hudson.tasks.test;

import hudson.AbortException;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Parses test result files and builds in-memory representation of it as {@link TestResult}.
 * <p>
 * Originally this was treated as an {@link ExtensionPoint},
 * but no supported code path actually uses this API directly. See {@link #all}.
 * <p>
 * Parsers are stateless, and the {@link #parseResult} method
 * can be concurrently invoked by multiple threads for different builds.
 *
 * @since 1.343
 */
public abstract class TestResultParser {
    /**
     * Returns a human readable name of the parser, like "JUnit Parser".
     *
     * @return a human readable name of the parser, like "JUnit Parser".
     * @deprecated Normally unused.
     */
    @Deprecated
    public String getDisplayName() {
        return "Unknown Parser"; 
    }

    /**
     * This text is used in the UI prompt for the GLOB that specifies files to be parsed by this parser.
     * For example, "JUnit XML reports:"
     *
     * @return the text is used in the UI prompt for the GLOB that specifies files to be parsed by this parser.
     * @deprecated Normally unused.
     */
    @Deprecated
    public String getTestResultLocationMessage() {
        return "Paths to results files to parse:";
    }

    /**
     * All registered {@link TestResultParser}s.
     *
     * @return all registered {@link TestResultParser}s.
     * @deprecated Unused. In fact only the {@code labeled-test-groups-publisher} plugin seems to actually enumerate parsers this way (though not using this method), a use case superseded by {@link PipelineTestDetails}.
     */
    @Deprecated
    public static ExtensionList<TestResultParser> all() {
        return ExtensionList.lookup(TestResultParser.class);
    }

    @Deprecated
    public TestResult parseResult(String testResultLocations,
                                  Run<?,?> run, @Nonnull FilePath workspace, Launcher launcher,
                                  TaskListener listener)
            throws InterruptedException, IOException {
        return parseResult(testResultLocations, run, null, workspace, launcher, listener);
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
     * difference in the master/slave into account.
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
    public TestResult parseResult(String testResultLocations,
                                  Run<?,?> run, PipelineTestDetails pipelineTestDetails,
                                  @Nonnull FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        if (run instanceof AbstractBuild) {
            return parse(testResultLocations, (AbstractBuild) run, launcher, listener);
        } else {
            throw new AbstractMethodError("you must override parseResult");
        }
    }

    @Deprecated
    public TestResult parse(String testResultLocations,
                                       AbstractBuild build, Launcher launcher,
                                       TaskListener listener)
            throws InterruptedException, IOException {
        if (Util.isOverridden(TestResultParser.class, getClass(), "parseResult", String.class, Run.class, FilePath.class, Launcher.class, TaskListener.class)) {
            FilePath workspace = build.getWorkspace();
            if (workspace == null) {
                throw new AbortException("no workspace in " + build);
            }
            return parseResult(testResultLocations, build, workspace, launcher, listener);
        } else {
            throw new AbstractMethodError("you must override parseResult");
        }
    }
}
