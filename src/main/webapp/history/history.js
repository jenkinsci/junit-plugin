/* global jQuery3, view, echartsJenkinsApi */
(function ($) {
    redrawTrendCharts();

    /**
     * Activate tooltips.
     */
    $(function () {
        $('[data-toggle="tooltip"]').tooltip();
    });

    /**
     * Redraws the trend charts. Reads the last selected X-Axis type from the browser local storage and
     * redraws the trend charts.
     */
    function redrawTrendCharts() {

        /**
         * Creates a build trend chart that shows the number of issues per tool.
         * Requires that a DOM <div> element exists with the ID '#tools-trend-chart'.
         */
        view.getTestDurationTrend(function (lineModel) {
            echartsJenkinsApi.renderZoomableTrendChart('test-duration-trend-chart', lineModel.responseJSON, redrawTrendCharts);
        });
        
        /**
         * Creates a build trend chart that shows the number of issues for a couple of builds.
         * Requires that a DOM <div> element exists with the ID '#severities-trend-chart'.
         */
        view.getTestResultTrend(function (lineModel) {
            echartsJenkinsApi.renderZoomableTrendChart('test-result-trend-chart', lineModel.responseJSON, redrawTrendCharts);
        });
    }
})(jQuery3);
