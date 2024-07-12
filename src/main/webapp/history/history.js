/* global jQuery3, bootstrap5, view, echartsJenkinsApi */
(function ($) {
    $(document).ready(function ($) {
        const trendConfigurationDialogId = 'chart-configuration-test-history';

        $('#' + trendConfigurationDialogId).on('hidden.bs.modal', function () {
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
        
        function renderTrendChart(chartDivId, model, settingsDialogId, chartClickedEventHandler) {
            const chartPlaceHolder = document.getElementById(chartDivId);
            const chart = echarts.init(chartPlaceHolder);
            chartPlaceHolder.echart = chart;
            
            const textColor = getComputedStyle(document.body).getPropertyValue('--darkreader-text--text-color') || getComputedStyle(document.body).getPropertyValue('--text-color') || '#222';
            const showSettings = document.getElementById(settingsDialogId);
            let darkMode = getComputedStyle(document.body).getPropertyValue('--darkreader-bg--background')
            darkMode = darkMode !== undefined && darkMode !== null && darkMode !== ''
            console.log('darkMode: ' + darkMode)
            let series = model.duration.series.concat(model.result.series)
            series.forEach(s => s.emphasis = {
                disabled: true
            });
            const options = {
                animation: false,
                darkMode: darkMode,
                //backgroundColor: getComputedStyle(document.body).getPropertyValue('--darkreader-bg--background') || getComputedStyle(document.body).getPropertyValue('--bs-body-bg') || '#fff',
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
                    width: '80%',
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

        function renderDistributionChart(chartDivId, model, settingsDialogId, chartClickedEventHandler) {
            const chartPlaceHolder = document.getElementById(chartDivId);
            const chart = echarts.init(chartPlaceHolder);
            chartPlaceHolder.echart = chart;
            
            const textColor = getComputedStyle(document.body).getPropertyValue('--darkreader-text--text-color') || getComputedStyle(document.body).getPropertyValue('--text-color') || '#222';
            const showSettings = document.getElementById(settingsDialogId);
            let darkMode = getComputedStyle(document.body).getPropertyValue('--darkreader-bg--background')
            darkMode = darkMode !== undefined && darkMode !== null && darkMode !== ''

            console.log('darkMode: ' + darkMode)
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
            console.log('trendChartJsonStr=' + trendChartJsonStr)
            /**
             * Creates the charts that show the test results, duration and distribution across a number of builds.
             */
            // TODO: Improve ECharts plugin to allow more direct interaction with ECharts
            renderTrendChart('test-trend-chart', trendChartJson, trendConfigurationDialogId, 
                function (buildDisplayName) {
                    console.log(buildDisplayName + ' clicked on chart')
                    if (trendChartJson.buildMap[buildDisplayName]) {
                        window.open(rootUrl + trendChartJson.buildMap[buildDisplayName].url);
                    }
                });
            renderDistributionChart('test-distribution-chart', trendChartJson, trendConfigurationDialogId, 
                function (buildDisplayName) {
                    console.log(buildDisplayName + ' clicked on chart')
                });
        }
        jQuery3(window).resize(function () {
            document.getElementById('test-trend-chart').echart.resize();
            document.getElementById('test-distribution-chart').echart.resize();
        });
    })
})(jQuery3);
