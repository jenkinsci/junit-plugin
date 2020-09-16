/* global jQuery3, view, echartsJenkinsApi */
(function ($) {
    redrawTrendCharts();
    storeAndRestoreCarousel('trend-carousel');

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

    /**
     * Store and restore the selected carousel image in browser's local storage.
     * Additionally, the trend chart is redrawn.
     *
     * @param {String} carouselId - ID of the carousel
     */
    function storeAndRestoreCarousel (carouselId) {
        const carousel = $('#' + carouselId);
        carousel.on('slid.bs.carousel', function (e) {
            localStorage.setItem(carouselId, e.to);
            const chart = $(e.relatedTarget).find('>:first-child')[0].echart;
            if (chart) {
                chart.resize();
            }
        });
        const activeCarousel = localStorage.getItem(carouselId);
        if (activeCarousel) {
            carousel.carousel(parseInt(activeCarousel));
        }
    }
})(jQuery3);
