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

import java.io.StringWriter;
import java.util.*;
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import hudson.tasks.junit.util.*;
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

    private String getTestDurationTrendJsonStr(List<HistoryTestResultSummary> htrList) {
        ObjectMapper mapper = new ObjectMapper();
        // create a JSON object
        ObjectNode root = mapper.createObjectNode();
        ArrayNode domainAxisLabels = mapper.createArrayNode();
        ArrayNode buildNumbers = mapper.createArrayNode();
        //ObjectNode visualMap = mapper.createObjectNode();
        ArrayNode series = mapper.createArrayNode();
        ObjectNode durationSeries = mapper.createObjectNode();
        series.add(durationSeries);
        durationSeries.put("name", "Seconds");
        durationSeries.put("type", "line");
        durationSeries.put("symbol", "circle");
        durationSeries.put("symbolSize", "8");
        ArrayNode durationData = mapper.createArrayNode();
        durationSeries.set("data", durationData);
        ObjectNode durationStyle = mapper.createObjectNode();
        durationSeries.set("itemStyle", durationStyle);
        durationStyle.put("color", "rgba(160, 173, 177, 0.5)");
        //durationSeries.put("stack", "stacked");
        ObjectNode durationAreaStyle = mapper.createObjectNode();
        durationSeries.set("areaStyle", durationAreaStyle);
        durationAreaStyle.put("normal", true);
        ObjectNode buildMap = mapper.createObjectNode();
        root.set("buildMap", buildMap);
        ObjectNode durationMarkLine = mapper.createObjectNode();
        durationSeries.set("markLine", durationMarkLine);
        ArrayNode durationMarkData = mapper.createArrayNode();
        durationMarkLine.set("data", durationMarkData);
        ObjectNode durationAvgMark = mapper.createObjectNode();
        ObjectNode hideLabel = mapper.createObjectNode();
        hideLabel.put("show", false);
        ObjectNode dashLineStyle = mapper.createObjectNode();
        dashLineStyle.put("dashOffset", 50);
        ArrayNode lightDashType = mapper.createArrayNode();
        lightDashType.add(5);
        lightDashType.add(10);        
        dashLineStyle.set("type", lightDashType);
        durationAvgMark.put("type", "average");
        durationAvgMark.put("name", "Avg");
        durationAvgMark.set("label", hideLabel);
        durationAvgMark.set("lineStyle", dashLineStyle);
        durationMarkData.add(durationAvgMark);

        /*ObjectNode durationMinMark = mapper.createObjectNode();
        durationMinMark.put("type", "min");
        durationMinMark.put("name", "Min");
        durationMinMark.set("label", hideLabel);
        durationMinMark.set("lineStyle", dashLineStyle);
        durationMarkData.add(durationMinMark);
        ObjectNode durationMaxMark = mapper.createObjectNode();
        durationMaxMark.put("type", "max");
        durationMaxMark.put("name", "Max");
        durationMaxMark.set("label", hideLabel);
        durationMaxMark.set("lineStyle", dashLineStyle);
        durationMarkData.add(durationMaxMark);*/

        List<hudson.tasks.test.TestResult> history = htrList.stream()
            .map(r -> testObject.getResultInRun(r.getRun()))
            .filter(r -> r != null)
            .collect(Collectors.toList());
        Collections.reverse(history);
        int index = 0;
        ObjectNode failedStyle = mapper.createObjectNode();
        failedStyle.put("color", "rgba(255, 100, 100, 0.8)");
        ObjectNode skippedStyle = mapper.createObjectNode();
        skippedStyle.put("color", "gray");
        ObjectNode okStyle = mapper.createObjectNode();
        okStyle.put("color", "rgba(50, 200, 50, 0.8)");
        float tmpMax = 0;
        double[] lrX = new double[history.size()];
        double[] lrY = new double[history.size()];
        for (hudson.tasks.test.TestResult to : history) {
            lrX[index] = ((double)index);
            Run<?,?> r = to.getRun();
            String fdn = r.getDisplayName();
            ObjectNode buildObj = mapper.createObjectNode();
            buildObj.put("url", r.getUrl());
            buildMap.set(fdn, buildObj);
            domainAxisLabels.add(fdn);
            buildNumbers.add(r.number);
            tmpMax = Math.max(to.getDuration(), tmpMax);
            ObjectNode durationColor = mapper.createObjectNode();
            lrY[index] = ((double)to.getDuration());
            durationColor.put("value", to.getDuration());
            if (to.isPassed()) {
                durationColor.set("itemStyle", okStyle);
            } else {
                if (to.getSkipCount() > 0) {
                    durationColor.set("itemStyle", skippedStyle);
                } else {
                    durationColor.set("itemStyle", failedStyle);
                }
            } 
            durationData.add(durationColor);
            ++index;
        }
        LinearRegression lr = new LinearRegression(lrX, lrY);
        ObjectNode lrSeries = mapper.createObjectNode();
        series.add(lrSeries);
        lrSeries.put("name", "Linear Regression of Seconds");
        lrSeries.put("type", "line");
        lrSeries.put("symbolSize", 3);
        ArrayNode lrData = mapper.createArrayNode();
        lrSeries.set("data", lrData);
        ObjectNode lrStyle = mapper.createObjectNode();
        lrSeries.set("itemStyle", lrStyle);
        lrStyle.put("color", "rgba(100, 100, 255, 0.4)");
        //lrSeries.put("stack", "stacked");
        ObjectNode lrAreaStyle = mapper.createObjectNode();
        lrSeries.set("areaStyle", lrAreaStyle);
        lrAreaStyle.put("color", "rgba(0, 0, 255, 0.1)");

        for (index = 0; index < history.size(); ++index) {
            lrData.add(lr.intercept() + index * lr.slope());
        }

        root.set("series", series);
        root.set("domainAxisLabels", domainAxisLabels);
        root.set("buildNumbers", buildNumbers);
        //root.set("visualMap", visualMap);
        root.put("integerRangeAxis", true);
        root.put("domainAxisItemName", "Build");
        //root.put("rangeMax", null);
        root.put("rangeMin", 0);
        if (tmpMax > 0.5) {
            root.put("rangeMax", (int)Math.ceil(tmpMax));
        }
        try {
            return root.toString();
        } catch (Exception e) {
            return e.toString();
        }
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
        if (r.getHistorySummaries().size() != 0) {
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
        private String trendChartJson;
        private String trendChartJsonStr; // Something weird happens on Javascript side, preventing the Json from being serialized multiple times, so provide it as JSON escaped string

        public HistoryTableResult(List<HistoryTestResultSummary> historySummaries, String trendChartJson) {
            this.descriptionAvailable = historySummaries.stream()
            .anyMatch(summary -> summary.getDescription() != null);
            this.historySummaries = historySummaries;
            this.trendChartJson = trendChartJson;
            ObjectMapper mapper = new ObjectMapper();
            try {
                this.trendChartJsonStr = mapper.writeValueAsString(trendChartJson);
            } catch (Exception e) {
                this.trendChartJsonStr = e.toString();
            }
        }

        public boolean isDescriptionAvailable() {
            return descriptionAvailable;
        }

        public List<HistoryTestResultSummary> getHistorySummaries() {
            return historySummaries;
        }

        public String getTrendChartJson() {
            return trendChartJson;
        }

        public String getTrendChartJsonStr() {
            return trendChartJsonStr;
        }
    }

    public HistoryTableResult retrieveHistorySummary(int start, int end) {
        TestResultImpl pluggableStorage = getPluggableStorage();
        List<HistoryTestResultSummary> trs = null;
        if (pluggableStorage != null) {
            int offset = start;
            if (start > 1000 || start < 0) {
                offset = 0;
            }
            trs = pluggableStorage.getHistorySummary(offset);
        } else {
            trs = getHistoryFromFileStorage(start, end);
        }
        return new HistoryTableResult(trs, getTestDurationTrendJsonStr(trs));
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
