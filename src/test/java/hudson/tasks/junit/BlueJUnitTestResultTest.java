package hudson.tasks.junit;

import com.google.common.collect.Lists;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.blueocean.commons.ServiceException.NotFoundException;
import io.jenkins.blueocean.rest.Reachable;
import io.jenkins.blueocean.rest.factory.BlueTestResultFactory;
import io.jenkins.blueocean.rest.hal.Link;
import io.jenkins.blueocean.rest.model.BlueTestResult;
import io.jenkins.blueocean.rest.model.BlueTestResult.State;
import io.jenkins.blueocean.rest.model.BlueTestResult.Status;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlueJUnitTestResultTest {

    private static final Reachable REACHABLE = new Reachable() {
        @Override
        public Link getLink() {
            return new Link("foo");
        }
    };

    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @Test
    public void testFactoryReturnsCaseResults() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject("testRemoteApi");
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("junit.xml").copyFrom(
                        getClass().getResource("junit-report-20090516.xml"));
                return true;
            }
        });
        p.getPublishersList().add(new JUnitResultArchiver("*.xml"));
        Run run = rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());

        BlueTestResultFactory.Result resolved = BlueTestResultFactory.resolve(run, REACHABLE);
        assertNotNull(resolved.results);
        assertNotNull(resolved.summary);
        assertEquals(Lists.newArrayList(resolved.results).size(), 8);
        assertEquals(resolved.summary.getExistingFailedTotal(), 3);
        assertEquals(resolved.summary.getFailedTotal(), 3);
        assertEquals(resolved.summary.getFixedTotal(), 0);
        assertEquals(resolved.summary.getPassedTotal(), 5);
        assertEquals(resolved.summary.getRegressionsTotal(), 0);
        assertEquals(resolved.summary.getSkippedTotal(), 0);
        assertEquals(resolved.summary.getTotal(), 8);

        BlueTestResult next = resolved.results.iterator().next();
        assertEquals("hudson.tasks.junit.BlueJUnitTestResult:junit%2Forg.twia.vendor%2FVendorManagerTest%2FtestGetVendorFirmKeyForVendorRep", next.getId());
        assertEquals("testGetVendorFirmKeyForVendorRep – org.twia.vendor.VendorManagerTest", next.getName());
        assertEquals(1, next.getAge());
        assertEquals(null, next.getErrorDetails());
        assertEquals(ERROR, next.getErrorStackTrace());
        String expectedLink = "foo/tests/hudson.tasks.junit.BlueJUnitTestResult%3Ajunit%252Forg.twia.vendor%252FVendorManagerTest%252FtestGetVendorFirmKeyForVendorRep/";
        assertEquals(expectedLink, next.getLink().toString());
        assertEquals(Status.FAILED, next.getStatus());
        assertEquals(State.UNKNOWN, next.getTestState());

        boolean noStdOut = false;
        try {
            assertEquals(null, next.getStdOut());
        } catch (NotFoundException e) {
            noStdOut = true;
        }
        assertTrue(noStdOut);


        boolean noStdErr = false;
        try {
            assertEquals(null, next.getStdErr());
        } catch (NotFoundException e) {
            noStdErr = true;
        }
        assertTrue(noStdErr);
    }

    @Test
    public void testDoesNotHaveTestResultAction() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        Run run = rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());

        BlueTestResultFactory.Result resolved = BlueTestResultFactory.resolve(run, REACHABLE);
        assertNull(resolved.results);
        assertNull(resolved.summary);
    }

    private final static String ERROR = "java.lang.NullPointerException\n" +
            "       at org.twia.vendor.VendorManagerTest.testGetVendorFirmKeyForVendorRep(VendorManagerTest.java:104)\n" +
            "       at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
            "       at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)\n" +
            "       at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
            "       at java.lang.reflect.Method.invoke(Method.java:585)\n" +
            "       at junit.framework.TestCase.runTest(TestCase.java:166)\n" +
            "       at org.unitils.UnitilsJUnit3.runTest(UnitilsJUnit3.java:171)\n" +
            "       at junit.framework.TestCase.runBare(TestCase.java:140)\n" +
            "       at org.unitils.UnitilsJUnit3.runBare(UnitilsJUnit3.java:138)\n" +
            "       at junit.framework.TestResult$1.protect(TestResult.java:106)\n" +
            "       at junit.framework.TestResult.runProtected(TestResult.java:124)\n" +
            "       at junit.framework.TestResult.run(TestResult.java:109)\n" +
            "       at junit.framework.TestCase.run(TestCase.java:131)\n" +
            "       at org.unitils.UnitilsJUnit3.run(UnitilsJUnit3.java:101)\n" +
            "       at junit.framework.TestSuite.runTest(TestSuite.java:173)\n" +
            "       at junit.framework.TestSuite.run(TestSuite.java:168)\n" +
            "       at junit.framework.TestSuite.runTest(TestSuite.java:173)\n" +
            "       at junit.framework.TestSuite.run(TestSuite.java:168)\n" +
            "       at junit.textui.TestRunner.doRun(TestRunner.java:74)\n" +
            "       at org.twia.junit.CustomTestRunner.run(CustomTestRunner.java:76)\n" +
            "       at org.twia.test.ejb.JunitCallerEJB.testSuites(JunitCallerEJB.java:136)\n" +
            "       at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
            "       at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)\n" +
            "       at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
            "       at java.lang.reflect.Method.invoke(Method.java:585)\n" +
            "       at org.jboss.invocation.Invocation.performCall(Invocation.java:359)\n" +
            "       at org.jboss.ejb.StatelessSessionContainer$ContainerInterceptor.invoke(StatelessSessionContainer.java:237)\n" +
            "       at org.jboss.resource.connectionmanager.CachedConnectionInterceptor.invoke(CachedConnectionInterceptor.java:158)\n" +
            "       at org.jboss.ejb.plugins.StatelessSessionInstanceInterceptor.invoke(StatelessSessionInstanceInterceptor.java:169)\n" +
            "       at org.jboss.ejb.plugins.CallValidationInterceptor.invoke(CallValidationInterceptor.java:63)\n" +
            "       at org.jboss.ejb.plugins.AbstractTxInterceptor.invokeNext(AbstractTxInterceptor.java:121)\n" +
            "       at org.jboss.ejb.plugins.TxInterceptorCMT.runWithTransactions(TxInterceptorCMT.java:315)\n" +
            "       at org.jboss.ejb.plugins.TxInterceptorCMT.invoke(TxInterceptorCMT.java:181)\n" +
            "       at org.jboss.ejb.plugins.SecurityInterceptor.invoke(SecurityInterceptor.java:168)\n" +
            "       at org.jboss.ejb.plugins.LogInterceptor.invoke(LogInterceptor.java:205)\n" +
            "       at org.jboss.ejb.plugins.ProxyFactoryFinderInterceptor.invoke(ProxyFactoryFinderInterceptor.java:138)\n" +
            "       at org.jboss.ejb.SessionContainer.internalInvoke(SessionContainer.java:648)\n" +
            "       at org.jboss.ejb.Container.invoke(Container.java:960)\n" +
            "       at sun.reflect.GeneratedMethodAccessor289.invoke(Unknown Source)\n" +
            "       at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
            "       at java.lang.reflect.Method.invoke(Method.java:585)\n" +
            "       at org.jboss.mx.interceptor.ReflectedDispatcher.invoke(ReflectedDispatcher.java:155)\n" +
            "       at org.jboss.mx.server.Invocation.dispatch(Invocation.java:94)\n" +
            "       at org.jboss.mx.server.Invocation.invoke(Invocation.java:86)\n" +
            "       at org.jboss.mx.server.AbstractMBeanInvoker.invoke(AbstractMBeanInvoker.java:264)\n" +
            "       at org.jboss.mx.server.MBeanServerImpl.invoke(MBeanServerImpl.java:659)\n" +
            "       at org.jboss.invocation.unified.server.UnifiedInvoker.invoke(UnifiedInvoker.java:231)\n" +
            "       at sun.reflect.GeneratedMethodAccessor288.invoke(Unknown Source)\n" +
            "       at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
            "       at java.lang.reflect.Method.invoke(Method.java:585)\n" +
            "       at org.jboss.mx.interceptor.ReflectedDispatcher.invoke(ReflectedDispatcher.java:155)\n" +
            "       at org.jboss.mx.server.Invocation.dispatch(Invocation.java:94)\n" +
            "       at org.jboss.mx.server.Invocation.invoke(Invocation.java:86)\n" +
            "       at org.jboss.mx.server.AbstractMBeanInvoker.invoke(AbstractMBeanInvoker.java:264)\n" +
            "       at org.jboss.mx.server.MBeanServerImpl.invoke(MBeanServerImpl.java:659)\n" +
            "       at javax.management.MBeanServerInvocationHandler.invoke(MBeanServerInvocationHandler.java:201)\n" +
            "       at $Proxy16.invoke(Unknown Source)\n" +
            "       at org.jboss.remoting.ServerInvoker.invoke(ServerInvoker.java:795)\n" +
            "       at org.jboss.remoting.transport.socket.ServerThread.processInvocation(ServerThread.java:573)\n" +
            "       at org.jboss.remoting.transport.socket.ServerThread.dorun(ServerThread.java:387)\n" +
            "       at org.jboss.remoting.transport.socket.ServerThread.run(ServerThread.java:166)\n";
}
