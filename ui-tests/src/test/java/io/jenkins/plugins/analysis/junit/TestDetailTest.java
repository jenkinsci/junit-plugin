package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

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

        JUnitTestDetail testDetail = new JUnitTestDetail(build, "junit");

        //TODO: Succeeding test details exist? How does the page look like? How to test?
    }

    @Test
    public void verifyDetailWithFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/parameterized/junit.xml", "/parameterized/testng.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build, "junit");
        JUnitTestDetail testDetail = buildSummary.openTestDetailView("JUnit.testScore[0]");

        assertThat(testDetail.getTitle(), containsString("Failed"));
        assertThat(testDetail.getSubTitle(), containsString("JUnit.testScore[0]"));
        assertThat(testDetail.getSubTitle(), containsString("from [0]")); // TODO: How to combine these?
        assertThat(testDetail.getErrorMessage(), equals("expected:<42> but was:<0>"));
        assertThat(testDetail.getStackTrace(), equals( // TODO: whitespace challenge
                "java.lang.AssertionError: expected:<42> but was:<0>\n"
                + "\tat org.junit.Assert.fail(Assert.java:88)\n"
                + "\tat org.junit.Assert.failNotEquals(Assert.java:743)\n"
                + "\tat org.junit.Assert.assertEquals(Assert.java:118)\n"
                + "\tat org.junit.Assert.assertEquals(Assert.java:555)\n"
                + "\tat org.junit.Assert.assertEquals(Assert.java:542)\n"
                + "\tat JUnit.testScore(JUnitTC.java:21)\n"
                + "\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n"
                + "\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)\n"
                + "\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n"
                + "\tat java.lang.reflect.Method.invoke(Method.java:606)\n"
                + "\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:47)\n"
                + "\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\n"
                + "\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:44)\n"
                + "\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n"
                + "\tat org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:271)\n"
                + "\tat org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:70)\n"
                + "\tat org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:50)\n"
                + "\tat org.junit.runners.ParentRunner$3.run(ParentRunner.java:238)\n"
                + "\tat org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:63)\n"
                + "\tat org.junit.runners.ParentRunner.runChildren(ParentRunner.java:236)\n"
                + "\tat org.junit.runners.ParentRunner.access$000(ParentRunner.java:53)\n"
                + "\tat org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:229)\n"
                + "\tat org.junit.runners.ParentRunner.run(ParentRunner.java:309)\n"
                + "\tat org.junit.runners.Suite.runChild(Suite.java:127)\n"
                + "\tat org.junit.runners.Suite.runChild(Suite.java:26)\n"
                + "\tat org.junit.runners.ParentRunner$3.run(ParentRunner.java:238)\n"
                + "\tat org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:63)\n"
                + "\tat org.junit.runners.ParentRunner.runChildren(ParentRunner.java:236)\n"
                + "\tat org.junit.runners.ParentRunner.access$000(ParentRunner.java:53)\n"
                + "\tat org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:229)\n"
                + "\tat org.junit.runners.ParentRunner.run(ParentRunner.java:309)\n"
                + "\tat org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference.run(JUnit4TestReference.java:50)\n"
                + "\tat org.eclipse.jdt.internal.junit.runner.TestExecution.run(TestExecution.java:38)\n"
                + "\tat org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:467)\n"
                + "\tat org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:683)\n"
                + "\tat org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.run(RemoteTestRunner.java:390)\n"
                + "\tat org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:197)"));

    }

}
