package io.jenkins.plugins.analysis.junit;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.openqa.selenium.WebElement;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JUnitPublisher;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.jenkinsci.test.acceptance.Matchers.*;

/**
 * @author Kohsuke Kawaguchi
 */
@WithPlugins("junit")
public class JUnitPluginTest extends AbstractJUnitTest {

    @Test
    public void buildSummaryWithNoFailures() {
        FreeStyleJob j = jenkins.jobs.create();
        j.configure();
        j.copyResource(resource("/success/com.simple.project.AppTest.txt"));
        j.copyResource(resource("/success/TEST-com.simple.project.AppTest.xml"));
        j.addPublisher(JUnitPublisher.class).testResults.set("*.xml");
        j.save();

        Build build = j.startBuild().shouldSucceed();
        build.open();

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build, "junit");

        assertThat(buildSummary.getBuildStatus(), is("Success"));
        assertThatJson(buildSummary.getFailedTestNames())
                .isArray()
                .hasSize(0);
    }

    @Test
    public void buildSummaryWithOneFailure() {
        FreeStyleJob j = jenkins.jobs.create();
        j.configure();
        j.copyResource(resource("/failure/com.simple.project.AppTest.txt"));
        j.copyResource(resource("/failure/TEST-com.simple.project.AppTest.xml"));
        j.addPublisher(JUnitPublisher.class).testResults.set("*.xml");
        j.save();

        Build build = j.startBuild();
        assertThat(build.getResult(), is("UNSTABLE"));
        build.open();

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build, "junit");

        assertThat(buildSummary.getBuildStatus(), is("Unstable"));
        assertThatJson(buildSummary.getFailedTestNames())
                .isArray()
                .hasSize(1)
                .contains("com.simple.project.AppTest.testApp");
    }

    @Test
    public void buildSummaryWithMultipleFailures() {
        FreeStyleJob j = jenkins.jobs.create();
        j.configure();
        j.copyResource(resource("/parameterized/junit.xml"));
        j.copyResource(resource("/parameterized/testng.xml"));
        j.addPublisher(JUnitPublisher.class).testResults.set("*.xml");
        j.save();

        Build build = j.startBuild();
        assertThat(build.getResult(), is("UNSTABLE"));
        build.open();

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build, "junit");

        assertThat(buildSummary.getBuildStatus(), is("Unstable"));
        assertThatJson(buildSummary.getFailedTestNames())
                .isArray()
                .hasSize(6)
                .contains("JUnit.testScore[0]")
                .contains("JUnit.testScore[1]")
                .contains("JUnit.testScore[2]")
                .contains("TestNG.testScore");
    }

    private void assertMessage(String test, String content) {
        // Given that there may be several tests with the same name, we assert
        // that at least one of the pages have the requested content
        final List<WebElement> elements = all(by.xpath("//a[text()='%s']", test));
        final List<String> testPages = new ArrayList<String>(elements.size());
        for (WebElement e : elements) {
            testPages.add(e.getAttribute("href"));
        }
        boolean found = false;
        for (String page : testPages) {
            driver.get(page);
            found = hasContent(content).matchesSafely(driver);
            driver.navigate().back();
            if (found) {
                break;
            }
        }
        assertThat("No test found with given content", found);
    }
}
