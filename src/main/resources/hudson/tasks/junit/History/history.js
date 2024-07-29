/* global jQuery3, bootstrap5, echartsJenkinsApi */
var start
var end
var count
var interval
var trendChartJson
var appRootUrl
var testObjectUrl
var resultSeries
var durationSeries
var trendChartId = 'test-trend-chart'

function onBuildWindowChange(selectObj) {
    let idx = selectObj.selectedIndex;
    let c = selectObj.options[idx].value
    document.location = `${appRootUrl}${testObjectUrl}/history?start=${start}&count=${c}&interval=${interval}`
}

function onBuildIntervalChange(selectObj) {
    let idx = selectObj.selectedIndex;
    let i = selectObj.options[idx].value
    document.location = `${appRootUrl}${testObjectUrl}/history?start=${start}&count=${count}&interval=${i}`
}

(function ($) {
    $(document).ready(function ($) {
        let dataEl = document.getElementById("history-data");
        start = dataEl.getAttribute("data-start")
        end = dataEl.getAttribute("data-end")
        count = dataEl.getAttribute("data-count")
        interval = dataEl.getAttribute("data-interval")
        let trendChartJsonStr = dataEl.innerHTML
        trendChartJson = JSON.parse(trendChartJsonStr)
        const rootUrl = document.head.dataset.rooturl
        if (!rootUrl.endsWith("/")) {
            appRootUrl = `${rootUrl}/`
        } else {
            appRootUrl = rootUrl
        }
        testObjectUrl = dataEl.getAttribute("data-testObjectUrl")

        trendChartJsonStr = null
        dataEl.setAttribute("data-trendChartJson", "")

        const trendConfigurationDialogId = 'chart-configuration-test-history';

        $('#' + trendConfigurationDialogId).on('hidden.bs.modal', function () {
            redrawTrendCharts();
        });

        redrawTrendCharts();

        document.getElementById('history-window').value = count
        document.getElementById('history-interval').value = interval

        if (trendChartJson?.status && trendChartJson?.status.buildsWithTestResult < trendChartJson?.status.buildsRequested) {
            let s
            if (trendChartJson.status.hasTimedOut) {
                s = `Too big. Showing ${trendChartJson.status.buildsWithTestResult} results from the most recent ${trendChartJson.status.buildsParsed} out of `
            } else {
                s = `Showing ${trendChartJson.status.buildsWithTestResult} test results out of `
            }
            document.getElementById("history-info").innerHTML = s;
        }
        /**
         * Activate tooltips.
         */
        $(function () {
            $('[data-bs-toggle="tooltip"]').each(function () {
                const tooltip = new bootstrap5.Tooltip($(this)[0]);
                tooltip.enable();
            });
        });

        function filterTrendSeries() {
            let model = trendChartJson
            const chartPlaceHolder = document.getElementById(trendChartId);
            if (resultSeries === undefined) {
                resultSeries = model.result.series
            }
            if (durationSeries === undefined) {
                durationSeries = model.duration.series
            }
            let r = chartPlaceHolder.getBoundingClientRect()
            let aspect = r.width / r.height
            let series = durationSeries.concat(resultSeries)
            if (aspect < 1.75) {
                series = series.filter((s) => !s.preferScreenOrient || s.preferScreenOrient != "landscape")
            }
            series.forEach(s => s.emphasis = {
                disabled: true
            });
            return series
        }

        function renderTrendChart(chartDivId, model, settingsDialogId, chartClickedEventHandler) {
            const chartPlaceHolder = document.getElementById(chartDivId);
            const chart = echarts.init(chartPlaceHolder);
            chartPlaceHolder.echart = chart;
            let style = getComputedStyle(document.body)
            const textColor = style.getPropertyValue('--darkreader-text--text-color') || style.getPropertyValue('--text-color') || '#222';
            const showSettings = document.getElementById(settingsDialogId);
            let darkMode = style.getPropertyValue('--darkreader-bg--background')
            darkMode = darkMode !== undefined && darkMode !== null && darkMode !== ''
            const options = {
                animation: false,
                darkMode: darkMode,
                toolbox: {
                    feature: {
                      dataZoom: {
                        yAxisIndex: 'none'
                      },
                      restore: {},
                      saveAsImage: {
                          name: model.saveAsImage.name
                      }
                    }
                },
                tooltip: {
                    trigger: 'axis',
                    animation: false,
                    axisPointer: {
                        type: 'cross',
                        label: {
                            backgroundColor: '#6a7985'
                        },
                        animation: false
                    },
                    transitionDuration: 0,
                    textStyle: {
                        fontSize: 12,
                    },
                    padding: 5,
                    order: 'seriesAsc',
                    position: [-260, '7%'],
                },
                axisPointer: {
                    snap: true,
                    link: [
                      {
                        xAxisIndex: 'all'
                      }
                    ]
                  },
                dataZoom: [
                    {
                        type: 'inside',
                        xAxisIndex: [0, 1]
                    },
                    {
                        type: 'slider',
                        height: 20,
                        bottom: 5,
                        moveHandleSize: 4,
                        xAxisIndex: [0, 1]
                    }
                ],
                legend: {
                    orient: 'horizontal',
                    type: 'plain',
                    x: 'center',
                    y: 'top',
                    width: '75%',
                    textStyle: {
                        color: textColor
                    },
                    selector: ['all', 'inverse']
                },
                grid: [
                    {
                      left: 80,
                      right: 40,
                      height: '33%',
                      top: '12%',
                    },
                    {
                      left: 80,
                      right: 40,
                      top: '53%',
                      height: '33%'
                    }
                  ],
                xAxis: [
                    {
                        type: 'category',
                        boundaryGap: false,
                        data: model.duration.domainAxisLabels,
                        axisLabel: {
                            color: textColor,
                            show: false
                        }
                    },
                    {
                        type: 'category',
                        gridIndex: 1,
                        boundaryGap: false,
                        data: model.result.domainAxisLabels,
                        axisLabel: {
                            color: textColor
                        }
                    }
                ],
                yAxis: [
                    {
                        type: 'value',
                        min: model.duration.rangeMin ?? 'dataMin',
                        max: model.duration.rangeMax ?? 'dataMax',
                        axisLabel: {
                            color: textColor
                        },
                        minInterval: model.duration.integerRangeAxis ? 1 : null,
                        name: model.duration.yAxis.name,
                        nameLocation: 'middle',
                        nameGap: 60,
                        nameTextStyle: {
                            color: textColor
                        },
                        splitLine: {
                            lineStyle: {
                                color: darkMode ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.2)'
                            }
                        }
                    },
                    {
                        type: 'value',
                        gridIndex: 1,
                        min: model.result.rangeMin ?? 'dataMin',
                        max: model.result.rangeMax ?? 'dataMax',
                        axisLabel: {
                            color: textColor
                        },
                        minInterval: model.result.integerRangeAxis ? 1 : null,
                        name: 'Count',
                        nameLocation: 'middle',
                        nameGap: 60,
                        nameTextStyle: {
                            color: textColor
                        },
                        splitLine: {
                            lineStyle: {
                                color: darkMode ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.2)'
                            }
                        }
                    }
                ],
                series: filterTrendSeries()
            };
            chart.setOption(options);
            chart.resize();
            if (chartClickedEventHandler !== null) {
                chart.getZr().on('click', params => {
                    const offset = 30;
                    if (params.offsetY > offset && chart.getHeight() - params.offsetY > offset) { // skip the legend and data zoom
                        const pointInPixel = [params.offsetX, params.offsetY];
                        const pointInGrid = chart.convertFromPixel('grid', pointInPixel);
                        const buildDisplayName = chart.getModel().get('xAxis')[0].data[pointInGrid[0]]
                        chartClickedEventHandler(buildDisplayName);
                    }
                })
            }
        }

        function renderDistributionChart(chartDivId, model, settingsDialogId, chartClickedEventHandler) {
            const chartPlaceHolder = document.getElementById(chartDivId);
            const chart = echarts.init(chartPlaceHolder);
            chartPlaceHolder.echart = chart;
            let style = getComputedStyle(document.body)
            const textColor = style.getPropertyValue('--darkreader-text--text-color') || style.getPropertyValue('--text-color') || '#222';
            const showSettings = document.getElementById(settingsDialogId);
            let darkMode = style.getPropertyValue('--darkreader-bg--background')
            darkMode = darkMode !== undefined && darkMode !== null && darkMode !== ''

            let series = model.distribution.series
            series.forEach(s => s.emphasis = {
                disabled: true
            });
            const options = {
                animation: false,
                darkMode: darkMode,
                toolbox: {
                    feature: {
                      restore: {},
                      saveAsImage: {
                          name: model.saveAsImage.name + '-distribution'
                      }
                    }
                },
                tooltip: {
                    trigger: 'axis',
                    animation: false,
                    axisPointer: {
                        type: 'cross',
                        label: {
                            backgroundColor: '#6a7985'
                        },
                        animation: false
                    },
                    transitionDuration: 0,
                    textStyle: {
                        fontSize: 12,
                    },
                    padding: 5,
                    order: 'seriesAsc',
                    position: [-260, '7%'],
                },
                axisPointer: {
                    snap: false
                },
                legend: {
                    orient: 'horizontal',
                    type: 'scroll',
                    x: 'center',
                    y: 'top',
                    width: '70%',
                    textStyle: {
                        color: textColor
                    },
                },
                grid: {
                    left: 80,
                    right: 40,
                    height: '57%',
                    top: '20%',
                },
                xAxis: {
                    type: 'category',
                    boundaryGap: false,
                    axisLabel: {
                        color: textColor
                    },
                    data: model.distribution.domainAxisLabels,
                    name: model.distribution.xAxis.name,
                    nameLocation: 'middle',
                    nameGap: 26,
                    nameTextStyle: {
                        color: textColor
                    },
                },
                yAxis: {
                    type: 'value',
                    min: 'dataMin',
                    max: 'dataMax',
                    axisLabel: {
                        color: textColor
                    },
                    name: 'Build Count',
                    nameLocation: 'middle',
                    nameGap: 60,
                    nameTextStyle: {
                        color: textColor
                    },
                    splitLine: {
                        lineStyle: {
                            color: darkMode ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.2)'
                        }
                    }
                },
                series: series
            };
            chart.setOption(options);
            chart.resize();
            if (chartClickedEventHandler !== null) {
                chart.getZr().on('click', params => {
                    const offset = 30;
                    if (params.offsetY > offset && chart.getHeight() - params.offsetY > offset) { // skip the legend and data zoom
                        const pointInPixel = [params.offsetX, params.offsetY];
                        const pointInGrid = chart.convertFromPixel('grid', pointInPixel);
                        const buildDisplayName = chart.getModel().get('xAxis')[0].data[pointInGrid[0]]
                        chartClickedEventHandler(buildDisplayName);
                    }
                })
            }
        }

        function applyCssColors(chartData) {
            let style = getComputedStyle(document.body)
            chartData.series.forEach((s) => {
                if (s?.itemStyle?.color && s.itemStyle.color.startsWith('--')) {
                    s.itemStyle.color = style.getPropertyValue(s.itemStyle.color)
                }
            })
        }
        /**
         * Redraws the trend charts. Reads the last selected X-Axis type from the browser local storage and
         * redraws the trend charts.
         */
        function redrawTrendCharts() {
            applyCssColors(trendChartJson.result)
            applyCssColors(trendChartJson.distribution)
            applyCssColors(trendChartJson.duration)
            /**
             * Creates the charts that show the test results, duration and distribution across a number of builds.
             */
            // TODO: Improve ECharts plugin to allow more direct interaction with ECharts
            renderTrendChart(trendChartId, trendChartJson, trendConfigurationDialogId,
                function (buildDisplayName) {
                    if (trendChartJson.buildMap[buildDisplayName]) {
                        window.open(appRootUrl + trendChartJson.buildMap[buildDisplayName].url);
                    }
                });
            renderDistributionChart('test-distribution-chart', trendChartJson, trendConfigurationDialogId, null);
        }
        jQuery3(window).resize(function () {
            let trendEchart = document.getElementById(trendChartId).echart
            trendEchart.setOption({
                series: filterTrendSeries()
            }, {
                replaceMerge: ['series']
            })
            trendEchart.resize();
            document.getElementById('test-distribution-chart').echart.resize();
        });
    })
})(jQuery3);
