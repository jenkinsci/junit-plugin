package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Tests the the detail view of a failed JUnit test.
 *
 * @author MichaelMüller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class TestDetailTest extends AbstractJUnitTest {

    @Test
    public void verifyDetailNoFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/com.simple.project.AppTest.txt", "/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        JUnitTestDetail testDetail = buildSummary.openBuildDetailView()
                .openClassDetailView("com.simple.project")
                .openTestDetailView("AppTest")
                .openTestDetail("testApp");

        assertThat(testDetail.getTitle()).contains("Passed");
        assertThat(testDetail.getSubTitle()).contains("com.simple.project.AppTest.testApp");

        assertThat(testDetail.getErrorMessage()).isEmpty();
        assertThat(testDetail.getStackTrace()).isEmpty();
        assertThat(testDetail.getStandardOutput()).isEmpty();
    }

    @Test
    public void verifyDetailNoFailuresIncludingStandardOutput() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/com.simple.project.AppTest.txt", "/success/junit-with-long-output.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        JUnitTestDetail testDetail = buildSummary.openBuildDetailView()
                .openClassDetailView("(root)")
                .openTestDetailView("JUnit")
                .openTestDetail("testScore[0]");

        assertThat(testDetail.getTitle()).contains("Passed");
        assertThat(testDetail.getSubTitle()).contains("JUnit.testScore[0]");

        assertThat(testDetail.getErrorMessage()).isEmpty();
        assertThat(testDetail.getStackTrace()).isEmpty();

        assertThat(testDetail.getStandardOutput()).isPresent();
        assertThat(testDetail.getStandardOutput().get()).contains("Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore");
    }

    @Test
    public void verifyDetailWithFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/parameterized/junit.xml", "/parameterized/testng.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        JUnitTestDetail testDetail = buildSummary.openTestDetailView("JUnit.testScore[0]");

        assertThat(testDetail.getTitle()).contains("Failed");
        assertThat(testDetail.getStandardOutput()).isEmpty();
        assertThat(testDetail.getSubTitle()).contains("JUnit.testScore[0]");

        assertThat(testDetail.getErrorMessage()).isPresent();
        assertThat(testDetail.getErrorMessage()).get().isEqualTo("expected:<42> but was:<0>");

        assertThat(testDetail.getStackTrace()).isPresent();
        assertThat(testDetail.getStackTrace()).get().isEqualTo(
                "java.lang.AssertionError: expected:<42> but was:<0>\n"
                        + " at org.junit.Assert.fail(Assert.java:88)\n"
                        + " at org.junit.Assert.failNotEquals(Assert.java:743)\n"
                        + " at org.junit.Assert.assertEquals(Assert.java:118)\n"
                        + " at org.junit.Assert.assertEquals(Assert.java:555)\n"
                        + " at org.junit.Assert.assertEquals(Assert.java:542)\n"
                        + " at JUnit.testScore(JUnitTC.java:21)\n"
                        + " at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n"
                        + " at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)\n"
                        + " at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n"
                        + " at java.lang.reflect.Method.invoke(Method.java:606)\n"
                        + " at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:47)\n"
                        + " at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\n"
                        + " at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:44)\n"
                        + " at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n"
                        + " at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:271)\n"
                        + " at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:70)\n"
                        + " at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:50)\n"
                        + " at org.junit.runners.ParentRunner$3.run(ParentRunner.java:238)\n"
                        + " at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:63)\n"
                        + " at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:236)\n"
                        + " at org.junit.runners.ParentRunner.access$000(ParentRunner.java:53)\n"
                        + " at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:229)\n"
                        + " at org.junit.runners.ParentRunner.run(ParentRunner.java:309)\n"
                        + " at org.junit.runners.Suite.runChild(Suite.java:127)\n"
                        + " at org.junit.runners.Suite.runChild(Suite.java:26)\n"
                        + " at org.junit.runners.ParentRunner$3.run(ParentRunner.java:238)\n"
                        + " at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:63)\n"
                        + " at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:236)\n"
                        + " at org.junit.runners.ParentRunner.access$000(ParentRunner.java:53)\n"
                        + " at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:229)\n"
                        + " at org.junit.runners.ParentRunner.run(ParentRunner.java:309)\n"
                        + " at org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference.run(JUnit4TestReference.java:50)\n"
                        + " at org.eclipse.jdt.internal.junit.runner.TestExecution.run(TestExecution.java:38)\n"
                        + " at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:467)\n"
                        + " at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:683)\n"
                        + " at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.run(RemoteTestRunner.java:390)\n"
                        + " at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:197)");

    }

}
