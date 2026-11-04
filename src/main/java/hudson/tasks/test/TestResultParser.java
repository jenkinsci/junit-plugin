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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitParser;
import java.io.IOException;

/**
 * Retained for compatibility only as a superclass of {@link JUnitParser}.
 */
public abstract class TestResultParser {

    @Deprecated
    public String getDisplayName() {
        return "Unknown Parser";
    }

    @Deprecated
    public String getTestResultLocationMessage() {
        return "Paths to results files to parse:";
    }

    @Deprecated
    public TestResult parseResult(
            String testResultLocations,
            Run<?, ?> run,
            @NonNull FilePath workspace,
            Launcher launcher,
            TaskListener listener)
            throws InterruptedException, IOException {
        return parseResult(testResultLocations, run, null, workspace, launcher, listener);
    }

    public TestResult parseResult(
            String testResultLocations,
            Run<?, ?> run,
            PipelineTestDetails pipelineTestDetails,
            @NonNull FilePath workspace,
            Launcher launcher,
            TaskListener listener)
            throws InterruptedException, IOException {
        if (run instanceof AbstractBuild) {
            return parse(testResultLocations, (AbstractBuild) run, launcher, listener);
        } else {
            throw new AbstractMethodError("you must override parseResult");
        }
    }

    @Deprecated
    public TestResult parse(String testResultLocations, AbstractBuild build, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        if (Util.isOverridden(
                TestResultParser.class,
                getClass(),
                "parseResult",
                String.class,
                Run.class,
                FilePath.class,
                Launcher.class,
                TaskListener.class)) {
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
