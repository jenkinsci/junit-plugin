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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import umontreal.ssj.functionfit.SmoothingCubicSpline;

/**
 * History of {@link hudson.tasks.test.TestObject} over time.
 *
 * @since 1.320
 */
@Restricted(NoExternalUse.class)
public class History {
    private static final JacksonFacade JACKSON_FACADE = new JacksonFacade();
    private final TestObject testObject;

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        return JACKSON_FACADE.toJson(
                createTestResultTrend(start, end, ChartModelConfiguration.fromJson(configuration)));
    }

    private LinesChartModel createTestResultTrend(int start, int end, ChartModelConfiguration chartModelConfiguration) {
        TestResultImpl pluggableStorage = getPluggableStorage();
        if (pluggableStorage != null) {
            return new TestResultTrendChart().create(pluggableStorage.getTrendTestResultSummary());
        }
        return new TestResultTrendChart()
                .createFromTestObject(createBuildHistory(testObject, start, end), chartModelConfiguration);
    }

    private ObjectNode computeDurationTrendJson(List<HistoryTestResultSummary> history) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode domainAxisLabels = MAPPER.createArrayNode();
        ArrayNode series = MAPPER.createArrayNode();
        ObjectNode durationSeries = MAPPER.createObjectNode();
        series.add(durationSeries);
        durationSeries.put("type", "line");
        durationSeries.put("symbol", "circle");
        durationSeries.put("symbolSize", "6");
        durationSeries.put("sampling", "lttb");
        ArrayNode durationData = MAPPER.createArrayNode();
        durationSeries.set("data", durationData);
        ObjectNode durationStyle = MAPPER.createObjectNode();
        durationSeries.set("itemStyle", durationStyle);
        durationStyle.put("color", "rgba(160, 173, 177, 0.6)");
        ObjectNode durationAreaStyle = MAPPER.createObjectNode();
        durationSeries.set("areaStyle", durationAreaStyle);
        durationAreaStyle.put("normal", true);
        ObjectNode durationMarkLine = MAPPER.createObjectNode();
        durationSeries.set("markLine", durationMarkLine);
        ArrayNode durationMarkData = MAPPER.createArrayNode();
        durationMarkLine.set("data", durationMarkData);
        ObjectNode durationAvgMark = MAPPER.createObjectNode();
        ObjectNode hideLabel = MAPPER.createObjectNode();
        hideLabel.put("show", false);
        ObjectNode dashLineStyle = MAPPER.createObjectNode();
        dashLineStyle.put("dashOffset", 50);
        dashLineStyle.put("color", "rgba(128, 128, 128, 0.1)");
        ArrayNode lightDashType = MAPPER.createArrayNode();
        lightDashType.add(5);
        lightDashType.add(10);
        dashLineStyle.set("type", lightDashType);
        durationAvgMark.put("type", "average");
        durationAvgMark.put("name", "Avg");
        durationAvgMark.set("label", hideLabel);
        durationAvgMark.set("lineStyle", dashLineStyle);
        durationMarkData.add(durationAvgMark);

        float maxDuration = (float) 0.0;
        for (HistoryTestResultSummary h : history) {
            if (maxDuration < h.getDuration()) {
                maxDuration = h.getDuration();
            }
        }
        ObjectNode yAxis = MAPPER.createObjectNode();
        double mul;
        double roundMul = 1.0;
        String durationStr;
        if (maxDuration < 1e-3) {
            durationStr = "Microseconds";
            mul = 1e6;
        } else if (maxDuration < 1) {
            durationStr = "Milliseconds";
            mul = 1e3;
        } else if (maxDuration < 90) {
            durationStr = "Seconds";
            roundMul = 1000.0;
            mul = 1.0;
        } else if (maxDuration < 90 * 60) {
            durationStr = "Minutes";
            mul = 1.0d / 60.0d;
            roundMul = 100.0;
        } else {
            durationStr = "Hours";
            mul = 1.0d / 3600.0d;
            roundMul = 100.0;
        }
        yAxis.put("name", "Duration (" + durationStr.toLowerCase() + ")");
        durationSeries.put("name", durationStr);

        int index = 0;
        ObjectNode skippedStyle = MAPPER.createObjectNode();
        skippedStyle.put("color", "gray");
        ObjectNode okStyle = MAPPER.createObjectNode();
        okStyle.put("color", "rgba(50, 200, 50, 0.8)");
        float tmpMax = 0;
        double[] lrX = new double[history.size()];
        double[] lrY = new double[history.size()];
        for (HistoryTestResultSummary h : history) {
            lrX[index] = index;
            Run<?, ?> r = h.getRun();
            String fdn = r.getDisplayName();
            domainAxisLabels.add(fdn);
            ObjectNode durationColor = MAPPER.createObjectNode();
            double duration = Math.round(mul * h.getDuration() * roundMul) / roundMul;
            tmpMax = Math.max((float) duration, tmpMax);
            lrY[index] = duration;
            durationColor.put("value", duration);
            if (h.getPassCount() > 0 && h.getFailCount() == 0 && h.getSkipCount() == 0) {
                durationColor.set("itemStyle", okStyle);
            } else {
                if (h.getFailCount() > 0) {
                    ObjectNode failedStyle = MAPPER.createObjectNode();
                    double k = Math.min(1.0, h.getFailCount() / (h.getTotalCount() * 0.02));
                    failedStyle.put("color", "rgba(255, 100, 100, " + (0.5 + 0.5 * k) + ")");
                    durationColor.set("itemStyle", failedStyle);
                } else {
                    durationColor.set("itemStyle", skippedStyle);
                }
            }
            durationData.add(durationColor);
            ++index;
        }

        if (EXTRA_GRAPH_MATH_ENABLED) {
            createLinearTrend(
                    series,
                    history,
                    lrX,
                    lrY,
                    "Trend of " + durationStr,
                    "rgba(0, 120, 255, 0.5)",
                    0,
                    0,
                    roundMul); // "--blue"
            createSplineTrend(
                    series,
                    history,
                    lrX,
                    lrY,
                    "Smooth of " + durationStr,
                    "rgba(120, 50, 255, 0.5)",
                    0,
                    0,
                    roundMul); // "--indigo"
        }
        root.set("series", series);
        root.set("domainAxisLabels", domainAxisLabels);
        root.put("integerRangeAxis", true);
        root.put("domainAxisItemName", "Build");
        if (tmpMax > 50) {
            root.put("rangeMax", (int) Math.ceil(tmpMax));
        } else if (tmpMax > 0.0) {
            root.put("rangeMax", tmpMax);
        } else {
            root.put("rangeMin", 0);
        }
        root.set("yAxis", yAxis);
        return root;
    }

    private void createLinearTrend(
            ArrayNode series,
            List<HistoryTestResultSummary> history,
            double[] lrX,
            double[] lrY,
            String title,
            String color,
            int xAxisIndex,
            int yAxisIndex,
            double roundMul) {
        if (history.size() < 3) {
            return;
        }

        double[] cs = SimpleLinearRegression.coefficients(lrX, lrY);
        double intercept = cs[0];
        double slope = cs[1];

        ObjectNode lrSeries = MAPPER.createObjectNode();
        series.add(lrSeries);
        lrSeries.put("name", title);
        lrSeries.put("preferScreenOrient", "landscape");
        lrSeries.put("type", "line");
        lrSeries.put("symbol", "circle");
        lrSeries.put("symbolSize", 0);
        lrSeries.put("xAxisIndex", xAxisIndex);
        lrSeries.put("yAxisIndex", yAxisIndex);
        ArrayNode lrData = MAPPER.createArrayNode();
        lrSeries.set("data", lrData);
        ObjectNode lrStyle = MAPPER.createObjectNode();
        lrSeries.set("itemStyle", lrStyle);
        lrStyle.put("color", color);
        ObjectNode lrAreaStyle = MAPPER.createObjectNode();
        lrSeries.set("areaStyle", lrAreaStyle);
        lrAreaStyle.put("color", "rgba(0, 0, 0, 0)");

        if (roundMul < 10.0) {
            roundMul = 10.0;
        }
        for (int index = 0; index < history.size(); ++index) {
            // Use float to reduce JSON size.
            lrData.add((float) (Math.round((intercept + index * slope) * roundMul) / roundMul));
        }
    }

    private void createSplineTrend(
            ArrayNode series,
            List<HistoryTestResultSummary> history,
            double[] lrX,
            double[] lrY,
            String title,
            String color,
            int xAxisIndex,
            int yAxisIndex,
            double roundMul) {
        if (history.size() < 200) {
            return;
        }
        double rho = Math.min(1.0, 1.0 / Math.max(1, (history.size() / 2)));
        if (rho > 0.75) {
            return; // Too close to linear
        }
        SmoothingCubicSpline scs = new SmoothingCubicSpline(lrX, lrY, 0.001);
        ObjectNode lrSeries = MAPPER.createObjectNode();
        series.add(lrSeries);
        lrSeries.put("name", title);
        lrSeries.put("preferScreenOrient", "landscape");
        lrSeries.put("type", "line");
        lrSeries.put("symbol", "circle");
        lrSeries.put("symbolSize", 0);
        lrSeries.put("xAxisIndex", xAxisIndex);
        lrSeries.put("yAxisIndex", yAxisIndex);
        ArrayNode lrData = MAPPER.createArrayNode();
        lrSeries.set("data", lrData);
        ObjectNode lrStyle = MAPPER.createObjectNode();
        lrSeries.set("itemStyle", lrStyle);
        lrStyle.put("color", color);
        ObjectNode lrAreaStyle = MAPPER.createObjectNode();
        lrSeries.set("areaStyle", lrAreaStyle);
        lrAreaStyle.put("color", "rgba(0, 0, 0, 0)");

        if (roundMul < 10.0) {
            roundMul = 10.0;
        }
        for (int index = 0; index < history.size(); ++index) {
            // Use float to reduce JSON size.
            lrData.add((float) (Math.round(scs.evaluate(index) * roundMul) / roundMul));
        }
    }

    static boolean EXTRA_GRAPH_MATH_ENABLED =
            Boolean.parseBoolean(System.getProperty(History.class.getName() + ".EXTRA_GRAPH_MATH_ENABLED", "true"));

    private ObjectNode computeResultTrendJson(List<HistoryTestResultSummary> history) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode domainAxisLabels = MAPPER.createArrayNode();
        ArrayNode series = MAPPER.createArrayNode();

        ObjectNode okSeries = MAPPER.createObjectNode();
        okSeries.put("name", "Passed");
        okSeries.put("xAxisIndex", 1);
        okSeries.put("yAxisIndex", 1);
        okSeries.put("type", "line");
        okSeries.put("symbol", "circle");
        okSeries.put("symbolSize", "0");
        okSeries.put("sampling", "lttb");
        ArrayNode okData = MAPPER.createArrayNode();
        okSeries.set("data", okData);
        ObjectNode okStyle = MAPPER.createObjectNode();
        okSeries.set("itemStyle", okStyle);
        okStyle.put("color", "--success-color"); // "rgba(50, 200, 50, 0.8)");
        okSeries.put("stack", "stacked");
        ObjectNode okAreaStyle = MAPPER.createObjectNode();
        okSeries.set("areaStyle", okAreaStyle);
        okAreaStyle.put("normal", true);

        ObjectNode okMarkLine = MAPPER.createObjectNode();
        okSeries.set("markLine", okMarkLine);
        ArrayNode okMarkData = MAPPER.createArrayNode();
        okMarkLine.set("data", okMarkData);
        ObjectNode avgMark = MAPPER.createObjectNode();
        ObjectNode hideLabel = MAPPER.createObjectNode();
        hideLabel.put("show", false);
        ObjectNode dashLineStyle = MAPPER.createObjectNode();
        dashLineStyle.put("dashOffset", 50);
        dashLineStyle.put("color", "rgba(128, 128, 128, 0.1)");
        ArrayNode lightDashType = MAPPER.createArrayNode();
        lightDashType.add(5);
        lightDashType.add(10);
        dashLineStyle.set("type", lightDashType);
        avgMark.put("type", "average");
        avgMark.put("name", "Avg");
        avgMark.set("label", hideLabel);
        avgMark.set("lineStyle", dashLineStyle);
        okMarkData.add(avgMark);

        ObjectNode failSeries = MAPPER.createObjectNode();
        failSeries.put("name", "Failed");
        failSeries.put("type", "line");
        failSeries.put("symbol", "circle");
        failSeries.put("symbolSize", "0");
        failSeries.put("sampling", "lttb");
        failSeries.put("xAxisIndex", 1);
        failSeries.put("yAxisIndex", 1);
        ArrayNode failData = MAPPER.createArrayNode();
        failSeries.set("data", failData);
        ObjectNode failStyle = MAPPER.createObjectNode();
        failSeries.set("itemStyle", failStyle);
        failStyle.put("color", "--light-red"); // "rgba(200, 50, 50, 0.8)");
        failSeries.put("stack", "stacked");
        ObjectNode failAreaStyle = MAPPER.createObjectNode();
        failSeries.set("areaStyle", failAreaStyle);
        failAreaStyle.put("normal", true);

        ObjectNode skipSeries = MAPPER.createObjectNode();
        skipSeries.put("name", "Skipped");
        skipSeries.put("type", "line");
        skipSeries.put("symbol", "circle");
        skipSeries.put("symbolSize", "0");
        skipSeries.put("sampling", "lttb");
        skipSeries.put("xAxisIndex", 1);
        skipSeries.put("yAxisIndex", 1);
        ArrayNode skipData = MAPPER.createArrayNode();
        skipSeries.set("data", skipData);
        ObjectNode skipStyle = MAPPER.createObjectNode();
        skipSeries.set("itemStyle", skipStyle);
        skipStyle.put("color", "rgba(160, 173, 177, 0.6)");
        skipSeries.put("stack", "stacked");
        ObjectNode skipAreaStyle = MAPPER.createObjectNode();
        skipSeries.set("areaStyle", skipAreaStyle);
        skipAreaStyle.put("normal", true);

        ObjectNode totalSeries = MAPPER.createObjectNode();
        totalSeries.put("name", "Total");
        totalSeries.put("type", "line");
        totalSeries.put("symbol", "circle");
        totalSeries.put("symbolSize", "0");
        totalSeries.put("sampling", "lttb");
        totalSeries.put("xAxisIndex", 1);
        totalSeries.put("yAxisIndex", 1);
        ArrayNode totalData = MAPPER.createArrayNode();
        totalSeries.set("data", totalData);
        ObjectNode lineStyle = MAPPER.createObjectNode();
        totalSeries.set("lineStyle", lineStyle);
        lineStyle.put("width", 1);
        lineStyle.put("type", "dashed");
        ObjectNode totalStyle = MAPPER.createObjectNode();
        totalSeries.set("itemStyle", totalStyle);
        totalStyle.put("color", "--light-blue"); // "rgba(0, 255, 255, 0.6)");

        ObjectNode totalAreaStyle = MAPPER.createObjectNode();
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
            lrX[index] = index;
            Run<?, ?> r = h.getRun();
            String fdn = r.getDisplayName();
            domainAxisLabels.add(fdn);
            lrY[index] = h.getPassCount();
            okData.add(h.getPassCount());
            skipData.add(h.getSkipCount());
            failData.add(h.getFailCount());
            totalData.add(h.getTotalCount());
            if (maxTotalCount < h.getTotalCount()) {
                maxTotalCount = h.getTotalCount();
            }
            ++index;
        }

        if (EXTRA_GRAPH_MATH_ENABLED) {
            createLinearTrend(
                    series,
                    history,
                    lrX,
                    lrY,
                    "Trend of Passed",
                    "rgba(50, 50, 255, 0.5)",
                    1,
                    1,
                    10.0); // "--dark-blue"
            createSplineTrend(
                    series, history, lrX, lrY, "Smooth of Passed", "rgba(255, 50, 255, 0.5)", 1, 1, 10.0); // "--purple"
        }

        root.set("series", series);
        root.set("domainAxisLabels", domainAxisLabels);
        root.put("integerRangeAxis", true);
        root.put("domainAxisItemName", "Build");
        root.put("rangeMin", 0);
        return root;
    }

    private ObjectNode computeDistributionJson(List<HistoryTestResultSummary> history) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode series = MAPPER.createArrayNode();
        ArrayNode domainAxisLabels = MAPPER.createArrayNode();

        ObjectNode durationSeries = MAPPER.createObjectNode();
        durationSeries.put("name", "Build Count");
        durationSeries.put("type", "line");
        durationSeries.put("symbol", "circle");
        durationSeries.put("symbolSize", "0");
        durationSeries.put("sampling", "lttb");
        ArrayNode durationData = MAPPER.createArrayNode();
        durationSeries.set("data", durationData);
        ObjectNode durationStyle = MAPPER.createObjectNode();
        durationSeries.set("itemStyle", durationStyle);
        durationStyle.put("color", "--success-color"); // "rgba(50, 200, 50, 0.8)");
        durationSeries.put("stack", "stacked");
        ObjectNode durAreaStyle = MAPPER.createObjectNode();
        durationSeries.set("areaStyle", durAreaStyle);
        durAreaStyle.put("color", "rgba(0,0,0,0)");
        durationSeries.put("smooth", true);
        series.add(durationSeries);

        double maxDuration = 0, minDuration = Double.MAX_VALUE;
        for (HistoryTestResultSummary h : history) {
            if (maxDuration < h.getDuration()) {
                maxDuration = h.getDuration();
            }
            if (minDuration > h.getDuration()) {
                minDuration = h.getDuration();
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
            int idx = smoothBuffer + (int) Math.round((h.getDuration() - minDuration) / step);
            int idx2 = Math.max(0, Math.min(idx, lrY.length - 1));
            lrY[idx2]++;
        }
        for (int i = 0; i < lrY.length; ++i) {
            lrX[i] = ((minDuration + (maxDuration - minDuration) / lrY.length * i) / scale * 100.0);
        }

        ObjectNode xAxis = MAPPER.createObjectNode();
        double mul;
        double roundMul = 1000.0;
        if (maxDuration < 1e-3) {
            xAxis.put("name", "Duration (microseconds)");
            mul = 1e6;
        } else if (maxDuration < 1) {
            xAxis.put("name", "Duration (milliseconds)");
            mul = 1e3;
        } else if (maxDuration < 90) {
            xAxis.put("name", "Duration (seconds)");
            mul = 1.0;
        } else if (maxDuration < 90 * 60) {
            xAxis.put("name", "Duration (minutes)");
            mul = 1.0d / 60.0d;
            roundMul = 100.0;
        } else {
            xAxis.put("name", "Duration (hours)");
            mul = 1.0d / 3600.0d;
            roundMul = 100.0;
        }

        int maxBuilds = 0;
        SmoothingCubicSpline scs = new SmoothingCubicSpline(lrX, lrY, 0.1);
        int smoothPts = counts.length * 4;
        double k = (double) counts.length / smoothPts;
        final double splineRoundMul = 1000.0;
        for (double z = minDuration; z < maxDuration; z += step * k) {
            double v = Math.round(splineRoundMul * Math.max(0.0, scs.evaluate(z / scale * 100.0))) / splineRoundMul;
            durationData.add((float) v);
            maxBuilds = Math.max(maxBuilds, (int) Math.ceil(v));
            // Use float for smaller JSONs.
            domainAxisLabels.add((float) (Math.round(mul * z * roundMul) / roundMul));
        }

        root.set("series", series);
        root.put("integerRangeAxis", true);
        root.put("domainAxisItemName", "Number of Builds");
        root.set("domainAxisLabels", domainAxisLabels);
        root.set("xAxis", xAxis);
        if (maxBuilds >= 10) {
            root.put("rangeMax", maxBuilds);
        }
        return root;
    }

    private ObjectNode computeBuildMapJson(List<HistoryTestResultSummary> history) {
        ObjectNode buildMap = MAPPER.createObjectNode();
        for (HistoryTestResultSummary h : history) {
            Run<?, ?> r = h.getRun();
            String fdn = r.getDisplayName();
            ObjectNode buildObj = MAPPER.createObjectNode();
            buildObj.put("url", h.getUrl());
            buildMap.set(fdn, buildObj);
        }
        return buildMap;
    }

    private ObjectNode computeTrendJsons(HistoryParseResult parseResult) {
        List<HistoryTestResultSummary> history = parseResult.historySummaries;
        Collections.reverse(history);
        ObjectNode root = MAPPER.createObjectNode();
        root.set("duration", computeDurationTrendJson(history));
        root.set("result", computeResultTrendJson(history));
        root.set("distribution", computeDistributionJson(history));
        root.set("buildMap", computeBuildMapJson(history));
        ObjectNode saveAsImage = MAPPER.createObjectNode();
        if (!history.isEmpty()) {
            saveAsImage.put(
                    "name",
                    "test-history-" + history.get(0).getRun().getParent().getFullName() + "-"
                            + history.get(0).getRun().getNumber() + "-"
                            + history.get(history.size() - 1).getRun().getNumber());
        } else {
            saveAsImage.put("name", "test-history");
        }
        root.set("saveAsImage", saveAsImage);
        ObjectNode status = MAPPER.createObjectNode();
        status.put("hasTimedOut", parseResult.hasTimedOut);
        status.put("buildsRequested", parseResult.buildsRequested);
        status.put("buildsParsed", parseResult.buildsParsed);
        status.put("buildsWithTestResult", parseResult.buildsWithTestResult);
        root.set("status", status);
        return root;
    }

    private TestObjectIterable createBuildHistory(final TestObject testObject, int start, int end) {
        HistoryTableResult r = retrieveHistorySummary(start, end);
        if (!r.getHistorySummaries().isEmpty()) {
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
            pluggableStorage =
                    ((ClassResult) testObject).getParent().getParent().getPluggableStorage();
        } else if (testObject instanceof CaseResult) {
            pluggableStorage = ((CaseResult) testObject)
                    .getParent()
                    .getParent()
                    .getParent()
                    .getPluggableStorage();
        }
        return pluggableStorage;
    }

    public static class HistoryTableResult {
        private final boolean descriptionAvailable;
        private final List<HistoryTestResultSummary> historySummaries;
        private final String trendChartJson;
        public HistoryParseResult parseResult;

        public HistoryTableResult(HistoryParseResult parseResult, ObjectNode json) {
            this.historySummaries = parseResult.historySummaries;
            this.descriptionAvailable =
                    this.historySummaries.stream().anyMatch(summary -> summary.getDescription() != null);
            this.trendChartJson = json.toString();
            this.parseResult = parseResult;
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
    }

    public static class HistoryParseResult {
        List<HistoryTestResultSummary> historySummaries;
        int buildsRequested;
        int buildsParsed;
        int buildsWithTestResult;
        int start;
        int end;
        int interval;
        boolean hasTimedOut;

        public HistoryParseResult(
                List<HistoryTestResultSummary> historySummaries,
                int buildsRequested,
                int buildsParsed,
                int buildsWithTestResult,
                boolean hasTimedOut,
                int start,
                int end,
                int interval) {
            this.buildsRequested = buildsRequested;
            this.historySummaries = historySummaries;
            this.buildsParsed = buildsParsed;
            this.buildsWithTestResult = buildsWithTestResult;
            this.hasTimedOut = hasTimedOut;
            this.start = start;
            this.end = end;
            this.interval = interval;
        }

        public HistoryParseResult(
                List<HistoryTestResultSummary> historySummaries, int buildsRequested, int start, int end) {
            this(historySummaries, buildsRequested, -1, -1, false, start, end, 1);
        }
    }

    // Handle multiple consecutive requests to same data from Jelly.
    private final Object cachedResultLock = new Object();
    private SoftReference<HistoryTableResult> cachedResult = new SoftReference<>(null);

    public HistoryTableResult retrieveHistorySummary(int start, int end) {
        return retrieveHistorySummary(start, end, 1);
    }

    public HistoryTableResult retrieveHistorySummary(int start, int end, int interval) {
        synchronized (cachedResultLock) {
            HistoryTableResult result = cachedResult.get();
            if (result != null
                    && result.parseResult.start == start
                    && result.parseResult.end == end
                    && result.parseResult.interval == interval) {
                return result;
            }
            TestResultImpl pluggableStorage = getPluggableStorage();
            HistoryParseResult parseResult;
            if (pluggableStorage != null) {
                int offset = start;
                if (start > 1000 || start < 0) {
                    offset = 0;
                }
                List<HistoryTestResultSummary> historySummary = pluggableStorage.getHistorySummary(offset);

                parseResult = new HistoryParseResult(historySummary, end - start + 1, start, end);
            } else {
                parseResult = getHistoryFromFileStorage(start, end, interval);
            }
            result = new HistoryTableResult(parseResult, computeTrendJsons(parseResult));
            cachedResult = new SoftReference<>(result);
            return result;
        }
    }

    static int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), Math.max(4, (int)
            (Runtime.getRuntime().availableProcessors() * 0.75 * 0.75)));
    static ExecutorService executor =
            Executors.newFixedThreadPool(Math.max(4, (int) (Runtime.getRuntime().availableProcessors() * 0.75 * 0.75)));
    static long MAX_TIME_ELAPSED_RETRIEVING_HISTORY_NS =
            SystemProperties.getLong(History.class.getName() + ".MAX_TIME_ELAPSED_RETRIEVING_HISTORY_MS", 15000L)
                    * 1000000L;
    static int MAX_THREADS_RETRIEVING_HISTORY =
            SystemProperties.getInteger(History.class.getName() + ".MAX_THREADS_RETRIEVING_HISTORY", -1);

    private HistoryParseResult getHistoryFromFileStorage(int start, int end, int interval) {
        TestObject testObject = getTestObject();
        RunList<?> builds = testObject.getRun().getParent().getBuilds();
        final int requestedCount = end - start;
        final AtomicBoolean hasTimedOut = new AtomicBoolean(false);
        final AtomicInteger parsedCount = new AtomicInteger(0);
        final long startedNs = java.lang.System.nanoTime();
        final AtomicInteger orderedCount = new AtomicInteger(0);
        List<HistoryTestResultSummary> history = builds.stream()
                .skip(start)
                .limit(requestedCount)
                .filter(build -> {
                    if (interval == 1) {
                        return true;
                    }
                    int n = orderedCount.getAndIncrement();
                    return (n % interval) == 0;
                })
                .collect(ParallelCollectors.parallel(
                        build -> {
                            // Do not navigate too far or for too long, we need to finish the request this year and have
                            // to think about RAM
                            if ((java.lang.System.nanoTime() - startedNs) > MAX_TIME_ELAPSED_RETRIEVING_HISTORY_NS) {
                                hasTimedOut.set(true);
                                return null;
                            }
                            parsedCount.incrementAndGet();
                            hudson.tasks.test.TestResult resultInRun = testObject.getResultInRun(build);
                            if (resultInRun == null) {
                                return null;
                            }

                            return new HistoryTestResultSummary(
                                    build,
                                    resultInRun.getDuration(),
                                    resultInRun.getFailCount(),
                                    resultInRun.getSkipCount(),
                                    resultInRun.getPassCount(),
                                    resultInRun.getDescription());
                        },
                        executor,
                        MAX_THREADS_RETRIEVING_HISTORY < 1
                                ? parallelism
                                : Math.min(parallelism, MAX_THREADS_RETRIEVING_HISTORY)))
                .join()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new HistoryParseResult(
                history, requestedCount, parsedCount.get(), history.size(), hasTimedOut.get(), start, end, interval);
    }

    @SuppressWarnings("unused") // Called by jelly view
    public static int asInt(String s, int defaultValue) {
        if (s == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // https://en.wikipedia.org/wiki/Simple_linear_regression
    static class SimpleLinearRegression {
        static double[] coefficients(double[] xs, double[] ys) {
            int n = xs.length;
            if (n < 2) {
                throw new IllegalArgumentException("At least two data points are required, but got: " + xs.length);
            }
            if (xs.length != ys.length) {
                throw new IllegalArgumentException("Array lengths do not match:" + xs.length + " vs " + ys.length);
            }
            double sumX = 0;
            double sumY = 0;
            double sumXX = 0;
            double sumXY = 0;
            for (int i = 0; i < n; i++) {
                double x = xs[i];
                double y = ys[i];
                sumX += x;
                sumY += y;
                sumXX += x * x;
                sumXY += x * y;
            }
            if (Math.abs(sumXX) < 10 * Double.MIN_VALUE) {
                // Avoid returning +/- infinity in case the x values are too close together.
                return new double[] {Double.NaN, Double.NaN};
            }
            double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
            double intercept = sumY / n - slope / n * sumX;
            return new double[] {intercept, slope};
        }
    }
}
