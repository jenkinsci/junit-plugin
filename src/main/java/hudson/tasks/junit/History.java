/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Tom Huybrechts, Yahoo!, Inc., Seiji Sogabe
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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.pivovarit.collectors.ParallelCollectors;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.LinesChartModel;
import hudson.model.Run;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestObjectIterable;
import hudson.tasks.test.TestResultDurationChart;
import hudson.tasks.test.TestResultTrendChart;
import hudson.util.RunList;
import io.jenkins.plugins.junit.storage.TestResultImpl;

/**
 * History of {@link hudson.tasks.test.TestObject} over time.
 *
 * @since 1.320
 */
@Restricted(NoExternalUse.class)
public class History {
    private static final JacksonFacade JACKSON_FACADE = new JacksonFacade();
    private static final String EMPTY_CONFIGURATION = "{}";
    private final TestObject testObject;

    public History(TestObject testObject) {
        this.testObject = testObject;
    }

    @SuppressWarnings("unused") // Called by jelly view
    public TestObject getTestObject() {
        return testObject;
    }

    @SuppressWarnings("unused") // Called by jelly view
    public boolean historyAvailable() {
        return true;
        /*if (testObject instanceof hudson.tasks.junit.TestResult) {
            TestResultImpl pluggableStorage = ((hudson.tasks.junit.TestResult) testObject).getPluggableStorage();
            if (pluggableStorage != null) {
                return pluggableStorage.getCountOfBuildsWithTestResults() > 1;
            }
        }

        return testObject.getRun().getParent().getBuilds().size() > 1;*/
    }

    @JavaScriptMethod
    @SuppressWarnings("unused") // Called by jelly view
    public String getTestResultTrend(int start, int end, String configuration) {
        return JACKSON_FACADE.toJson(createTestResultTrend(start, end, ChartModelConfiguration.fromJson(configuration)));
    }

    private LinesChartModel createTestResultTrend(int start, int end, ChartModelConfiguration chartModelConfiguration) {
        TestResultImpl pluggableStorage = getPluggableStorage();
        if (pluggableStorage != null) {
            return new TestResultTrendChart().create(pluggableStorage.getTrendTestResultSummary());
        }
        return new TestResultTrendChart().createFromTestObject(createBuildHistory(testObject, start, end), chartModelConfiguration);
    }

    @JavaScriptMethod
    @SuppressWarnings("unused") // Called by jelly view
    public String getTestDurationTrend(int start, int end, String configuration) {
        return JACKSON_FACADE.toJson(createTestDurationResultTrend(start, end, ChartModelConfiguration.fromJson(configuration)));
    }

    private LinesChartModel createTestDurationResultTrend(int start, int end, ChartModelConfiguration chartModelConfiguration) {
        TestResultImpl pluggableStorage = getPluggableStorage();

        if (pluggableStorage != null) {
            return new TestResultDurationChart().create(pluggableStorage.getTestDurationResultSummary());
        }

        return new TestResultDurationChart().create(createBuildHistory(testObject, start, end), chartModelConfiguration);
    }

    private TestObjectIterable createBuildHistory(final TestObject testObject, int start, int end) {
        HistoryTableResult r = retrieveHistorySummary(start, end);
        if (r.getHistorySummaries().size() != 0) { // Fast
            //Run<?,?> build = r.getHistorySummaries().get(0).getRun();
            //return new TestObjectIterable(testObject.getResultInRun(build));
            return new TestObjectIterable(testObject, r.getHistorySummaries());
        }
        return null;
    }

    private TestResultImpl getPluggableStorage() {
        TestResultImpl pluggableStorage = null;
        if (testObject instanceof TestResult) {
            pluggableStorage = ((TestResult) testObject).getPluggableStorage();
        } else if (testObject instanceof PackageResult) {
            pluggableStorage = ((PackageResult) testObject).getParent().getPluggableStorage();
        } else if (testObject instanceof ClassResult) {
            pluggableStorage = ((ClassResult) testObject).getParent().getParent().getPluggableStorage();
        } else if (testObject instanceof CaseResult) {
            pluggableStorage = ((CaseResult) testObject).getParent().getParent().getParent().getPluggableStorage();
        }
        return pluggableStorage;
    }

    public static class HistoryTableResult {
        private boolean descriptionAvailable;
        private List<HistoryTestResultSummary> historySummaries;

        public HistoryTableResult(List<HistoryTestResultSummary> historySummaries) {
            this.descriptionAvailable = historySummaries.stream()
            .anyMatch(summary -> summary.getDescription() != null);
            this.historySummaries = historySummaries;
        }

        public boolean isDescriptionAvailable() {
            return descriptionAvailable;
        }

        public List<HistoryTestResultSummary> getHistorySummaries() {
            return historySummaries;
        }
    }

    public HistoryTableResult retrieveHistorySummary(int start, int end) {
        TestResultImpl pluggableStorage = getPluggableStorage();
        if (pluggableStorage != null) {
            int offset = start;
            if (start > 1000 || start < 0) {
                offset = 0;
            }    
            return new HistoryTableResult(pluggableStorage.getHistorySummary(offset));
        }
        return new HistoryTableResult(getHistoryFromFileStorage(start, end));
    }
    ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, (int)(Runtime.getRuntime().availableProcessors() * 0.75 * 0.75)));
    private List<HistoryTestResultSummary> getHistoryFromFileStorage(int start, int end) {
        TestObject testObject = getTestObject();
        RunList<?> builds = testObject.getRun().getParent().getBuilds();
        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), Math.max(4, (int)(Runtime.getRuntime().availableProcessors() * 0.75 * 0.75)));
        final AtomicInteger count = new AtomicInteger(0);
        final long startedMs = java.lang.System.currentTimeMillis();
        return builds.stream().skip(start)
            .collect(ParallelCollectors.parallel(build -> {
                int c = count.incrementAndGet();
                if (c > end - start + 1 || (java.lang.System.currentTimeMillis() - startedMs) > 15000) { // Do not navigate too far or for too long, we need to finish the request this year and have to think about RAM
                    return null;
                }
                hudson.tasks.test.TestResult resultInRun = testObject.getResultInRun(build);
                if (resultInRun == null) {
                    return null;
                }

                return new HistoryTestResultSummary(build, resultInRun.getDuration(),
                        resultInRun.getFailCount(),
                        resultInRun.getSkipCount(),
                        resultInRun.getPassCount(),
                        resultInRun.getDescription()
                );
            }, executor, parallelism))
            .join()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unused") // Called by jelly view
    public static int asInt(String s, int defaultValue) {
        if (s == null) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
