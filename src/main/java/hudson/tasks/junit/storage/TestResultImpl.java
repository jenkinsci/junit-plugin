/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package hudson.tasks.junit.storage;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TrendTestResultSummary;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Pluggable implementation of {@link TestResult}.
 */
@Restricted(Beta.class)
public interface TestResultImpl {
    int getFailCount();
    int getSkipCount();
    int getPassCount();
    int getTotalCount();
    List<CaseResult> getFailedTests();
    List<CaseResult> getFailedTestsByPackage(String packageName);
    List<CaseResult> getSkippedTests();
    List<CaseResult> getSkippedTestsByPackage(String packageName);
    List<CaseResult> getPassedTests();
    List<CaseResult> getPassedTestsByPackage(String packageName);
    PackageResult getPackageResult(String packageName);
    List<PackageResult> getAllPackageResults();

    /**
     * Retrieves results for trend graphs
     * @return test summary for all runs associated to the job
     */
    List<TrendTestResultSummary> getTrendTestResultSummary();
    
    Run<?, ?> getFailedSinceRun(CaseResult caseResult);
    @NonNull
    TestResult getResultByNodes(@NonNull List<String> nodeIds);

    // These methods don't take into account context of packages
    // so could easily lookup the wrong test
    // they are used in the classic view for test results
    ClassResult getClassResult(String name);
    CaseResult getCaseResult(String name);
    // end dodgy methods with no context

}
