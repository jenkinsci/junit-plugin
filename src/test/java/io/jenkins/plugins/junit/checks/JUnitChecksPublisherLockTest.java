package io.jenkins.plugins.junit.checks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Publishing checks can hang on a slow or broken SCM host, so it must not happen while holding the build lock.
 */
@WithJenkins
public class JUnitChecksPublisherLockTest {

    @TestExtension
    public static final HangingChecksPublisherFactory FACTORY = new HangingChecksPublisherFactory();

    @Issue("jenkins-infra/helpdesk#5379")
    @Test
    void buildLockIsNotHeldWhilePublishingChecks(JenkinsRule rule) throws Exception {
        WorkflowJob job = rule.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'TEST-lock.xml', text: '''<testsuite name="suite" tests="2">
                    <testcase classname="Lock" name="passes"/>
                    <testcase classname="Lock" name="fails"><failure message="boom">trace</failure></testcase>
                  </testsuite>'''
                  junit 'TEST-lock.xml'
                }
                """, true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertTrue(FACTORY.entered.await(60, TimeUnit.SECONDS), "checks were never published");
            Future<?> lock = executor.submit(() -> {
                synchronized (run) {
                    return null;
                }
            });
            lock.get(10, TimeUnit.SECONDS); // TimeoutException if publishing holds the build lock
        } finally {
            FACTORY.release.countDown();
            executor.shutdownNow();
        }

        rule.assertBuildStatus(Result.UNSTABLE, rule.waitForCompletion(run));
        ChecksDetails details = FACTORY.published;
        assertThat(details, notNullValue());
        assertThat(details.getName().orElseThrow(), is("Tests"));
        assertThat(details.getConclusion(), is(ChecksConclusion.FAILURE));
    }

    /** Stands in for a checks publisher whose call to the SCM host hangs until released. */
    public static class HangingChecksPublisherFactory extends ChecksPublisherFactory {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile ChecksDetails published;

        @Override
        protected Optional<ChecksPublisher> createPublisher(Run<?, ?> run, TaskListener listener) {
            return Optional.of(new ChecksPublisher() {
                @Override
                public void publish(ChecksDetails details) {
                    entered.countDown();
                    try {
                        release.await(60, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    published = details;
                }
            });
        }
    }
}
