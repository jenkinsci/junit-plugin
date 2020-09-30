# JUnit Plugin for Jenkins

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/junit.svg)](https://plugins.jenkins.io/junit)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/junit-plugin.svg?label=release)](https://github.com/jenkinsci/junit-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/junit.svg?color=blue)](https://plugins.jenkins.io/junit)

The JUnit plugin provides a publisher that consumes XML test reports generated during the builds and provides some graphical visualization of the historical test results 
(see [JUnit graph](https://wiki.jenkins.io/display/JENKINS/JUnit+graph) for a sample) 
as well as a web UI for viewing test reports, tracking failures, and so on. 
Jenkins understands the JUnit test report XML format (which is also used by TestNG). 
When this option is configured, Jenkins can provide useful information about test results, such as trends.

The plugin also provides a generic API for other unit-test publisher plugins in Jenkins. This functionality was part of the Jenkins Core until it was split out to this plugin in version in 1.577.

## Configuration

The JUnit publisher is configured at the job level by adding a Publish JUnit test result report post build action. The configuration parameters include:

* **Test report XMLs:** Specify the path to JUnit XML files in the Ant glob syntax, such as `**/build/test-reports/*.xml`. 
  Be sure not to include any non-report files into this pattern. 
  You can specify multiple patterns of files separated by commas. 
  The base directory of the fileset is the workspace root.
* **Retain long standard output/error:** If checked, any standard output or error from a test suite will be retained in the test results after the build completes. 
  (This refers only to additional messages printed to console, not to a failure stack trace). 
  Such output is always kept if the test failed, but by default lengthy output from passing tests is truncated to save space. 
  Check this option if you need to see every log message from even passing tests, but beware that Jenkins's memory consumption can substantially increase as a result, even if you never look at the test results!
* **Health report amplification factor:** The amplification factor to apply to test failures when computing the test result contribution to the build health score. 
  The default factor is 1.0. A factor of 0.0 will disable the test result contribution to build health score, and, as an example, a factor of 0.5 means that 10% of tests failing will score 95% health. 
  The factor is persisted with the build results, so changes will only be reflected in new builds.
* **Allow empty results:** If checked, the default behavior of failing a build on missing test result files or empty test results is changed to not affect the status of the build. 
  Please note that this setting make it harder to spot misconfigured jobs or build failures where the test tool does not exit with an error code when not producing test report files.
