/* global jQuery3, bootstrap5, view, echartsJenkinsApi */
(function ($) {
    $(document).ready(function ($) {
        const trendConfigurationDialogId = 'chart-configuration-test-history';

        document.getElementById(trendConfigurationDialogId).on('hidden.bs.modal', function () {
            redrawTrendCharts();
        });

        redrawTrendCharts();

        /**
         * Activate tooltips.
         */
        $(function () {
            $('[data-bs-toggle="tooltip"]').each(function () {
                const tooltip = new bootstrap5.Tooltip($(this)[0]);
                tooltip.enable();
            });
        });

        /**
         * Redraws the trend charts. Reads the last selected X-Axis type from the browser local storage and
         * redraws the trend charts.
         */
        function redrawTrendCharts() {
            //const configuration = JSON.stringify(echartsJenkinsApi.readFromLocalStorage('jenkins-echarts-chart-configuration-test-history'));
            let configuration = JSON.stringify({
                numberOfBuilds: end - start + 1,
                "numberOfDays":"0",
                "buildAsDomain":"true"
            });
            console.log('configuration=' + configuration + ";" + JSON.stringify(start) + ";" + JSON.stringify(end))
            console.log('durationTrendChartJsonStr=' + durationTrendChartJsonStr)
            console.log('resultTrendChartJsonStr=' + resultTrendChartJsonStr)
            /**
             * Creates a build trend chart that shows the test duration across a number of builds.
             * Requires that a DOM <div> element exists with the ID '#test-duration-trend-chart'.
             */
            /*view.getTestDurationTrend(start, end, configuration, function (lineModel) {
                let response = JSON.parse(responseJSON)
                echartsJenkinsApi.renderConfigurableZoomableTrendChart('test-duration-trend-chart', lineModel.responseJSON, trendConfigurationDialogId, 
                    function (buildDisplayName) {
                        console.log(buildDisplayName + ' clicked on chart')
                        window.open(response.buildMap[buildDisplayName].url, '_blank');
                    });
            });*/
            // TODO: Improve ECharts plugin to allow more direct interaction with ECharts
            echartsJenkinsApi.renderConfigurableZoomableTrendChart('test-duration-trend-chart', durationTrendChartJsonStr, trendConfigurationDialogId, 
                function (buildDisplayName) {
                    console.log(buildDisplayName + ' clicked on chart')
                    window.open(rootUrl + buildMap[buildDisplayName].url);
                });
            echartsJenkinsApi.renderConfigurableZoomableTrendChart('test-result-trend-chart', resultTrendChartJsonStr, trendConfigurationDialogId, 
                function (buildDisplayName) {
                    console.log(buildDisplayName + ' clicked on chart')
                    window.open(rootUrl + buildMap[buildDisplayName].url);
                });
        }
    })
})(jQuery3);
