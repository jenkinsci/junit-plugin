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

package io.jenkins.plugins.junit.storage;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestDurationResultSummary;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TrendTestResultSummary;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import hudson.tasks.junit.HistoryTestResultSummary;
import jenkins.model.Jenkins;
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

    /**
     * Retrieves duration for history graph
     * @return test duration summary for all runs associated to the job
     * TODO Add API that only loads specific test object, will allow smaller scoped history graphs
     */
    List<TestDurationResultSummary> getTestDurationResultSummary();

    List<HistoryTestResultSummary> getHistorySummary(int offset);

    /**
     * Determines if there is multiple builds with test results
     * @return count of builds with tests results
     */
    int getCountOfBuildsWithTestResults();
    
    Run<?, ?> getFailedSinceRun(CaseResult caseResult);

    @CheckForNull
    default Run<?, ?> getRun() {
        Job<?, ?> theJob = Jenkins.get().getItemByFullName(getJobName(), Job.class);
        if (theJob == null) {
            return null;
        }
        return theJob.getBuildByNumber(getBuild());
    }

    @NonNull
    String getJobName();

    @NonNull
    int getBuild();

    @NonNull
    TestResult getResultByNodes(@NonNull List<String> nodeIds);

    /**
     * The test result for the last run that has a test result
     * Null when there's no previous result.
     * @return the previous test result or null if there's no previous one.
     */
    @CheckForNull
    TestResult getPreviousResult();
    SuiteResult getSuite(String name);


    default Collection<SuiteResult> getSuites() {
        return Collections.emptyList();
    };


    float getTotalTestDuration();
}
