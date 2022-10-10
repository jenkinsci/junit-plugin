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
import hudson.tasks.test.TestResultTrendChart;
import hudson.util.RunList;
import io.jenkins.plugins.junit.storage.TestResultImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import hudson.tasks.junit.util.*;

import umontreal.ssj.functionfit.SmoothingCubicSpline;
import umontreal.ssj.probdist.*;


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

    private class TrendJsons {
  
        public String duration;
        public String result;
        public String buildMap;
      
        public TrendJsons(String duration, String result, String buildMap) {
      
            this.duration = duration;
            this.result = result;
            this.buildMap = buildMap;
        }
    }

    private ObjectNode computeDurationTrendJson(List<HistoryTestResultSummary> history) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode domainAxisLabels = mapper.createArrayNode();
        ArrayNode series = mapper.createArrayNode();
        ObjectNode durationSeries = mapper.createObjectNode();
        series.add(durationSeries);
        durationSeries.put("name", "Seconds");
        durationSeries.put("type", "line");
        durationSeries.put("symbol", "circle");
        durationSeries.put("symbolSize", "6");
        durationSeries.put("sampling", "lttb");
        ArrayNode durationData = mapper.createArrayNode();
        durationSeries.set("data", durationData);
        ObjectNode durationStyle = mapper.createObjectNode();
        durationSeries.set("itemStyle", durationStyle);
        durationStyle.put("color", "rgba(160, 173, 177, 0.6)");
        ObjectNode durationAreaStyle = mapper.createObjectNode();
        durationSeries.set("areaStyle", durationAreaStyle);
        durationAreaStyle.put("normal", true);
        ObjectNode durationMarkLine = mapper.createObjectNode();
        durationSeries.set("markLine", durationMarkLine);
        ArrayNode durationMarkData = mapper.createArrayNode();
        durationMarkLine.set("data", durationMarkData);
        ObjectNode durationAvgMark = mapper.createObjectNode();
        ObjectNode hideLabel = mapper.createObjectNode();
        hideLabel.put("show", false);
        ObjectNode dashLineStyle = mapper.createObjectNode();
        dashLineStyle.put("dashOffset", 50);
        dashLineStyle.put("color", "rgba(128, 128, 128, 0.1)");
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

        int index = 0;
        ObjectNode skippedStyle = mapper.createObjectNode();
        skippedStyle.put("color", "gray");
        ObjectNode okStyle = mapper.createObjectNode();
        okStyle.put("color", "rgba(50, 200, 50, 0.8)");
        float tmpMax = 0;
        double[] lrX = new double[history.size()];
        double[] lrY = new double[history.size()];
        float maxDuration = (float)0.0;
        for (HistoryTestResultSummary h : history) {
            hudson.tasks.test.TestResult to = h.getResultInRun();
            lrX[index] = ((double)index);
            Run<?,?> r = h.getRun();
            String fdn = r.getDisplayName();
            domainAxisLabels.add(fdn);
            tmpMax = Math.max(to.getDuration(), tmpMax);
            ObjectNode durationColor = mapper.createObjectNode();
            lrY[index] = ((double)to.getDuration());
            if (maxDuration < to.getDuration()) {
                maxDuration = to.getDuration();
            }
            durationColor.put("value", to.getDuration());
            if (to.isPassed() || (to.getPassCount() > 0 && to.getFailCount() == 0)) {
                durationColor.set("itemStyle", okStyle);
            } else {
                if (to.getFailCount() > 0) {
                    ObjectNode failedStyle = mapper.createObjectNode();
                    double k = Math.min(1.0, to.getFailCount() / (to.getTotalCount() * 0.02));
                    failedStyle.put("color", "rgba(255, 100, 100, " + (0.5 + 0.5 * k) +")");
                    durationColor.set("itemStyle", failedStyle);
                } else {
                    durationColor.set("itemStyle", skippedStyle);
                }
            }
            durationData.add(durationColor);
            ++index;
        }

        createLinearTrend(mapper, series, history, lrX, lrY, "Trend of Seconds", "rgba(0, 120, 255, 0.5)", 0.0, Double.MAX_VALUE, 0, 0);
        createSplineTrend(mapper, series, history, lrX, lrY, "Smooth of Seconds", "rgba(120, 50, 255, 0.5)", 0.0, Double.MAX_VALUE, 0, 0);

        root.set("series", series);
        root.set("domainAxisLabels", domainAxisLabels);
        root.put("integerRangeAxis", true);
        root.put("domainAxisItemName", "Build");
        if (maxDuration > 0.0) {
            root.put("rangeMax", maxDuration);
        }
        root.put("rangeMin", 0);
        if (tmpMax > 5) {
            root.put("rangeMax", (int)Math.ceil(tmpMax));
        }
        return root;
    }

    private void createLinearTrend(ObjectMapper mapper, ArrayNode series, List<HistoryTestResultSummary> history, double[] lrX, double[] lrY, String title, String color, double minV, double maxV, int xAxisIndex, int yAxisIndex) {
        LinearRegression lr = new LinearRegression(lrX, lrY);
        ObjectNode lrSeries = mapper.createObjectNode();
        series.add(lrSeries);
        lrSeries.put("name", title);
        lrSeries.put("type", "line");
        lrSeries.put("symbol", "circle");
        lrSeries.put("symbolSize", 0);
        lrSeries.put("xAxisIndex", xAxisIndex);
        lrSeries.put("yAxisIndex", yAxisIndex);
        ArrayNode lrData = mapper.createArrayNode();
        lrSeries.set("data", lrData);
        ObjectNode lrStyle = mapper.createObjectNode();
        lrSeries.set("itemStyle", lrStyle);
        lrStyle.put("color", color);
        ObjectNode lrAreaStyle = mapper.createObjectNode();
        lrSeries.set("areaStyle", lrAreaStyle);
        lrAreaStyle.put("color", "rgba(0, 0, 0, 0)");

        for (int index = 0; index < history.size(); ++index) {
            //lrData.add((float)Math.min(maxV, Math.max(minV, lr.predict(index))));
            lrData.add((float)lr.predict(index));
        }
    }

    private void createSplineTrend(ObjectMapper mapper, ArrayNode series, List<HistoryTestResultSummary> history, double[] lrX, double[] lrY, String title, String color, double minV, double maxV, int xAxisIndex, int yAxisIndex) {
        if (history.size() < 200) {
            return;
        }
        double windowSize = 100.0;
        double rho = Math.min(1.0, 1.0 / (history.size() / 2));
        if (rho > 0.75) {
            return; // Too close to linear
        }
        SmoothingCubicSpline scs = new SmoothingCubicSpline(lrX, lrY, 0.001);
        ObjectNode lrSeries = mapper.createObjectNode();
        series.add(lrSeries);
        lrSeries.put("name", title);
        lrSeries.put("type", "line");
        lrSeries.put("symbol", "circle");
        lrSeries.put("symbolSize", 0);
        lrSeries.put("xAxisIndex", xAxisIndex);
        lrSeries.put("yAxisIndex", yAxisIndex);
        ArrayNode lrData = mapper.createArrayNode();
        lrSeries.set("data", lrData);
        ObjectNode lrStyle = mapper.createObjectNode();
        lrSeries.set("itemStyle", lrStyle);
        lrStyle.put("color", color);
        ObjectNode lrAreaStyle = mapper.createObjectNode();
        lrSeries.set("areaStyle", lrAreaStyle);
        lrAreaStyle.put("color", "rgba(0, 0, 0, 0)");

        for (int index = 0; index < history.size(); ++index) {
            //lrData.add((float)Math.min(maxV, Math.max(minV, scs.evaluate(index))));
            lrData.add((float)scs.evaluate(index));
        }
    }

    private ObjectNode computeResultTrendJson(List<HistoryTestResultSummary> history) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode domainAxisLabels = mapper.createArrayNode();
        ArrayNode series = mapper.createArrayNode();

        ObjectNode okSeries = mapper.createObjectNode();
        okSeries.put("name", "Passed");
        okSeries.put("xAxisIndex", 1);
        okSeries.put("yAxisIndex", 1);
        okSeries.put("type", "line");
        okSeries.put("symbol", "circle");
        okSeries.put("symbolSize", "0");
        okSeries.put("sampling", "lttb");
        ArrayNode okData = mapper.createArrayNode();
        okSeries.set("data", okData);
        ObjectNode okStyle = mapper.createObjectNode();
        okSeries.set("itemStyle", okStyle);
        okStyle.put("color", "rgba(50, 200, 50, 0.8)");
        okSeries.put("stack", "stacked");
        ObjectNode okAreaStyle = mapper.createObjectNode();
        okSeries.set("areaStyle", okAreaStyle);
        okAreaStyle.put("normal", true);
        
        ObjectNode okMarkLine = mapper.createObjectNode();
        okSeries.set("markLine", okMarkLine);
        ArrayNode okMarkData = mapper.createArrayNode();
        okMarkLine.set("data", okMarkData);
        ObjectNode avgMark = mapper.createObjectNode();
        ObjectNode hideLabel = mapper.createObjectNode();
        hideLabel.put("show", false);
        ObjectNode dashLineStyle = mapper.createObjectNode();
        dashLineStyle.put("dashOffset", 50);
        dashLineStyle.put("color", "rgba(128, 128, 128, 0.1)");
        ArrayNode lightDashType = mapper.createArrayNode();
        lightDashType.add(5);
        lightDashType.add(10);        
        dashLineStyle.set("type", lightDashType);
        avgMark.put("type", "average");
        avgMark.put("name", "Avg");
        avgMark.set("label", hideLabel);
        avgMark.set("lineStyle", dashLineStyle);
        okMarkData.add(avgMark);

        //mapper.readTree();

        ObjectNode failSeries = mapper.createObjectNode();
        failSeries.put("name", "Failed");
        failSeries.put("type", "line");
        failSeries.put("symbol", "circle");
        failSeries.put("symbolSize", "0");
        failSeries.put("sampling", "lttb");
        failSeries.put("xAxisIndex", 1);
        failSeries.put("yAxisIndex", 1);
        ArrayNode failData = mapper.createArrayNode();
        failSeries.set("data", failData);
        ObjectNode failStyle = mapper.createObjectNode();
        failSeries.set("itemStyle", failStyle);
        failStyle.put("color", "rgba(200, 50, 50, 0.8)");
        failSeries.put("stack", "stacked");
        ObjectNode failAreaStyle = mapper.createObjectNode();
        failSeries.set("areaStyle", failAreaStyle);
        failAreaStyle.put("normal", true);

        ObjectNode skipSeries = mapper.createObjectNode();
        skipSeries.put("name", "Skipped");
        skipSeries.put("type", "line");
        skipSeries.put("symbol", "circle");
        skipSeries.put("symbolSize", "0");
        skipSeries.put("sampling", "lttb");
        skipSeries.put("xAxisIndex", 1);
        skipSeries.put("yAxisIndex", 1);
        ArrayNode skipData = mapper.createArrayNode();
        skipSeries.set("data", skipData);
        ObjectNode skipStyle = mapper.createObjectNode();
        skipSeries.set("itemStyle", skipStyle);
        skipStyle.put("color", "rgba(160, 173, 177, 0.6)");
        skipSeries.put("stack", "stacked");
        ObjectNode skipAreaStyle = mapper.createObjectNode();
        skipSeries.set("areaStyle", skipAreaStyle);
        skipAreaStyle.put("normal", true);

        ObjectNode totalSeries = mapper.createObjectNode();
        totalSeries.put("name", "Total");
        totalSeries.put("type", "line");
        totalSeries.put("symbol", "circle");
        totalSeries.put("symbolSize", "0");
        totalSeries.put("sampling", "lttb");
        totalSeries.put("xAxisIndex", 1);
        totalSeries.put("yAxisIndex", 1);
        ArrayNode totalData = mapper.createArrayNode();
        totalSeries.set("data", totalData);
        ObjectNode lineStyle = mapper.createObjectNode();
        totalSeries.set("lineStyle", lineStyle);
        lineStyle.put("width", 1);
        lineStyle.put("type", "dashed");
        ObjectNode totalStyle = mapper.createObjectNode();
        totalSeries.set("itemStyle", totalStyle);
        totalStyle.put("color", "rgba(0, 255, 255, 0.6)");

        ObjectNode totalAreaStyle = mapper.createObjectNode();
        totalSeries.set("areaStyle", totalAreaStyle);
        totalAreaStyle.put("color", "rgba(0, 0, 0, 0)");

        series.add(skipSeries);
        series.add(failSeries);
        series.add(okSeries);
        series.add(totalSeries);
        
        int maxTotalCount = 0;
        int index = 0;
        double[] lrX = new double[history.size()];
        double[] lrY = new double[history.size()];
        for (HistoryTestResultSummary h : history) {
            hudson.tasks.test.TestResult to = h.getResultInRun();
            lrX[index] = ((double)index);
            Run<?,?> r = h.getRun();
            String fdn = r.getDisplayName();
            domainAxisLabels.add(fdn);
            lrY[index] = ((double)to.getPassCount());
            okData.add(to.getPassCount());
            skipData.add(to.getSkipCount());
            failData.add(to.getFailCount());
            totalData.add(to.getTotalCount());
            if (maxTotalCount < to.getTotalCount()) {
                maxTotalCount = to.getTotalCount();
            }
            ++index;
        }

        createLinearTrend(mapper, series, history, lrX, lrY, "Trend of Passed", "rgba(50, 50, 255, 0.5)", 0.0, maxTotalCount, 1, 1);
        createSplineTrend(mapper, series, history, lrX, lrY, "Smooth of Passed", "rgba(255, 50, 255, 0.5)", 0.0, maxTotalCount, 1, 1);

        root.set("series", series);
        root.set("domainAxisLabels", domainAxisLabels);
        root.put("integerRangeAxis", true);
        root.put("domainAxisItemName", "Build");
        //if (maxTotalCount > 0.0) {
        //    root.put("rangeMax", maxTotalCount);
        //}
        root.put("rangeMin", 0);
        return root;
    }

    private ObjectNode computeDistributionJson(List<HistoryTestResultSummary> history) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode series = mapper.createArrayNode();
        ArrayNode domainAxisLabels = mapper.createArrayNode();

        ObjectNode durationSeries = mapper.createObjectNode();
        durationSeries.put("name", "Build Count");
        durationSeries.put("type", "line");
        durationSeries.put("symbol", "circle");
        durationSeries.put("symbolSize", "0");
        durationSeries.put("sampling", "lttb");
        ArrayNode durationData = mapper.createArrayNode();
        durationSeries.set("data", durationData);
        ObjectNode durationStyle = mapper.createObjectNode();
        durationSeries.set("itemStyle", durationStyle);
        durationStyle.put("color", "rgba(50, 200, 50, 0.8)");
        durationSeries.put("stack", "stacked");
        ObjectNode durAreaStyle = mapper.createObjectNode();
        durationSeries.set("areaStyle", durAreaStyle);
        durAreaStyle.put("color", "rgba(0,0,0,0)");
        durationSeries.put("smooth", true);
        series.add(durationSeries);
        
        double maxDuration = 0, minDuration = Double.MAX_VALUE;
        for (HistoryTestResultSummary h : history) {
            hudson.tasks.test.TestResult to = h.getResultInRun();
            if (maxDuration < to.getDuration()) {
                maxDuration = to.getDuration();
            }
            if (minDuration > to.getDuration()) {
                minDuration = to.getDuration();
            }
        }
        double extraDuration = Math.max(0.001, (maxDuration - minDuration) * 0.05);
        minDuration = Math.max(0.0, minDuration - extraDuration);
        maxDuration = maxDuration + extraDuration;
        int[] counts = new int[100];
        int smoothBuffer = 2;
        double[] lrX = new double[counts.length + smoothBuffer * 2 + 1];
        double[] lrY = new double[counts.length + smoothBuffer * 2 + 1];
        double scale = maxDuration - minDuration;
        double step = scale / counts.length;
        for (HistoryTestResultSummary h : history) {
            hudson.tasks.test.TestResult to = h.getResultInRun();
            lrY[smoothBuffer + (int)Math.round((to.getDuration() - minDuration) / step)]++;
        }
        for (int i = 0; i < lrY.length; ++i) {
            lrX[i] = ((minDuration + (maxDuration - minDuration) / lrY.length * i) / scale * 100.0);
        }
        SmoothingCubicSpline scs = new SmoothingCubicSpline(lrX, lrY, 0.1);
        int smoothPts = counts.length * 4;
        double k = (double)counts.length / smoothPts;
        for (double z = minDuration; z < maxDuration; z += step * k) {
            durationData.add((float)Math.max(0.0, scs.evaluate(z / scale * 100.0)));
            domainAxisLabels.add((float)z);
        }

        root.set("series", series);
        root.put("integerRangeAxis", true);
        root.put("domainAxisItemName", "Number of Builds");
        root.set("domainAxisLabels", domainAxisLabels);
        //root.put("rangeMax", 0);
        //root.put("rangeMin", 0);
        return root;
    }

    private ObjectNode computeBuildMapJson(List<HistoryTestResultSummary> history) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode buildMap = mapper.createObjectNode();
        for (HistoryTestResultSummary h : history) {
            Run<?,?> r = h.getRun();
            String fdn = r.getDisplayName();
            ObjectNode buildObj = mapper.createObjectNode();
            buildObj.put("url", h.getUrl());
            buildMap.set(fdn, buildObj);
        }
        return buildMap;
    }
    private ObjectNode computeTrendJsons(List<HistoryTestResultSummary> history) {
        Collections.reverse(history);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.set("duration", computeDurationTrendJson(history));
        root.set("result", computeResultTrendJson(history));
        root.set("distribution", computeDistributionJson(history));
        root.set("buildMap", computeBuildMapJson(history));
        return root;
    }

    /*private LinesChartModel createTestDurationResultTrend(int start, int end, ChartModelConfiguration chartModelConfiguration) {
        TestResultImpl pluggableStorage = getPluggableStorage();

        if (pluggableStorage != null) {
            return new TestResultDurationChart().create(pluggableStorage.getTestDurationResultSummary());
        }

        return new TestResultDurationChart().create(createBuildHistory(testObject, start, end), chartModelConfiguration);
    }*/

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
        // Something weird happens on Javascript side, preventing the Json from being serialized again there, so provide it as JSON escaped string
        private String trendChartJson;
        private String trendChartJsonStr; 

        public HistoryTableResult(List<HistoryTestResultSummary> historySummaries, ObjectNode json) {
            this.descriptionAvailable = historySummaries.stream()
            .anyMatch(summary -> summary.getDescription() != null);
            this.historySummaries = historySummaries;
            ObjectMapper mapper = new ObjectMapper();
            this.trendChartJson = json.toString();
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
        return new HistoryTableResult(trs, computeTrendJsons(trs));
    }
    static int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), Math.max(4, (int)(Runtime.getRuntime().availableProcessors() * 0.75 * 0.75)));
    static ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, (int)(Runtime.getRuntime().availableProcessors() * 0.75 * 0.75)));
    private List<HistoryTestResultSummary> getHistoryFromFileStorage(int start, int end) {
        TestObject testObject = getTestObject();
        RunList<?> builds = testObject.getRun().getParent().getBuilds();
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

                return new HistoryTestResultSummary(build, resultInRun, resultInRun.getDuration(),
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
