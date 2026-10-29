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
import hudson.tasks.junit.HistoryTestResultSummary;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestDurationResultSummary;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TrendTestResultSummary;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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

    /**
     * Whether this storage implementation provides a fast path for {@link CaseResult#getPreviousResult()}
     * via {@link #getPreviousCaseResult(CaseResult)}. The default (file-based) implementation of that
     * method walks up to {@code PREVIOUS_TEST_RESULT_BACKTRACK_BUILDS_MAX} historical builds one at a
     * time, resolving a suite lookup against each, purely to find the nearest earlier build that
     * contains a case with the same identity (suite/package/classname/testname). A storage backend
     * that can answer that question directly (e.g. with a single indexed query, independent of how
     * many historical builds must be considered) should return {@code true} here and override
     * {@link #getPreviousCaseResult(CaseResult)}, so the per-build walk -- and its repeated suite
     * loads -- can be skipped entirely.
     *
     * @return {@code true} if {@link #getPreviousCaseResult(CaseResult)} should be used instead of the
     *         default build-by-build search
     */
    default boolean supportsPreviousCaseResultLookup() {
        return false;
    }

    /**
     * Fast path for {@link CaseResult#getPreviousResult()}, only called when
     * {@link #supportsPreviousCaseResultLookup()} returns {@code true}.
     *
     * @param current the case to find the previous result for, belonging to the build this
     *                {@link TestResultImpl} instance represents
     * @return an {@link Optional} with the resolved result, or {@link Optional#empty()} if this
     *         implementation has determined there is none within its own backtrack limit
     */
    @NonNull
    default Optional<CaseResult> getPreviousCaseResult(@NonNull CaseResult current) {
        return Optional.empty();
    }

    /**
     * Whether this storage implementation supports {@link #forEachCaseResultSummary(int, int, Consumer)}.
     *
     * <p>Consumers that need per-test results across many builds of a job (e.g. a test history matrix)
     * should check this and, when {@code true}, use that method instead of loading the full
     * {@link TestResult} of each build, which is much more expensive for an external storage backend.
     *
     * @return {@code true} if {@link #forEachCaseResultSummary(int, int, Consumer)} is implemented
     * @since TODO
     */
    default boolean supportsCaseResultSummaries() {
        return false;
    }

    /**
     * Streams a lightweight summary of every test case recorded for builds {@code fromBuild} to
     * {@code toBuild} (both inclusive) of the job this instance belongs to, regardless of which build
     * this instance itself represents. Only called when {@link #supportsCaseResultSummaries()} returns
     * {@code true}.
     *
     * <p>Summaries are passed to the consumer grouped by build in ascending build order; within a build
     * cases are passed in the order they were recorded. Summaries should not be retained in bulk by the
     * consumer, the point of streaming them is that many builds' worth of cases need not be held in
     * memory at once.
     *
     * @param fromBuild the lowest build number to include
     * @param toBuild the highest build number to include
     * @param consumer receives each case summary
     * @since TODO
     */
    default void forEachCaseResultSummary(int fromBuild, int toBuild, @NonNull Consumer<CaseResultSummary> consumer) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support case result summaries");
    }

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
    }
    ;

    float getTotalTestDuration();
}
