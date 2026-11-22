package io.jenkins.plugins.junit.checks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
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

    @TestExtension("buildLockIsNotHeldWhilePublishingChecks")
    public static final HangingPublishFactory HANGING_PUBLISH = new HangingPublishFactory();

    @TestExtension("parallelStepDoesNotChangeChecksBeingPublished")
    public static final PausingLookupFactory PAUSING_LOOKUP = new PausingLookupFactory();

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
            assertTrue(HANGING_PUBLISH.entered.await(60, TimeUnit.SECONDS), "checks were never published");
            Future<?> lock = executor.submit(() -> {
                synchronized (run) {
                    return null;
                }
            });
            lock.get(10, TimeUnit.SECONDS); // TimeoutException if publishing holds the build lock
        } finally {
            HANGING_PUBLISH.release.countDown();
            executor.shutdownNow();
        }

        rule.assertBuildStatus(Result.UNSTABLE, rule.waitForCompletion(run));
        ChecksDetails details = HANGING_PUBLISH.published;
        assertThat(details, notNullValue());
        assertThat(details.getName().orElseThrow(), is("Tests"));
        assertThat(details.getConclusion(), is(ChecksConclusion.FAILURE));
    }

    /**
     * The first junit step's result is also the build's merged result, which a parallel junit step changes.
     * The checks for the first step must still show only its own results.
     */
    @Issue("jenkins-infra/helpdesk#5379")
    @Test
    void parallelStepDoesNotChangeChecksBeingPublished(JenkinsRule rule) throws Exception {
        WorkflowJob job = rule.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("""
                parallel a: {
                  node {
                    writeFile file: 'a/TEST-a.xml', text: '''<testsuite name="a" tests="2">
                      <testcase classname="A" name="passes"/>
                      <testcase classname="A" name="failsInA"><failure message="a">trace a</failure></testcase>
                    </testsuite>'''
                    junit 'a/TEST-a.xml'
                  }
                }, b: {
                  semaphore 'b'
                  node {
                    writeFile file: 'b/TEST-b.xml', text: '''<testsuite name="b" tests="1">
                      <testcase classname="B" name="failsInB"><failure message="b">trace b</failure></testcase>
                    </testsuite>'''
                    junit 'b/TEST-b.xml'
                  }
                }
                """, true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();

        try {
            // branch a has recorded its results and is looking up a checks publisher
            assertTrue(PAUSING_LOOKUP.paused.await(60, TimeUnit.SECONDS), "branch a never published checks");
            SemaphoreStep.success("b/1", null);
            for (int i = 0; i < 600 && !PAUSING_LOOKUP.published.containsKey("Tests / b"); i++) {
                Thread.sleep(100);
            }
            assertThat("branch b published checks", PAUSING_LOOKUP.published.containsKey("Tests / b"), is(true));
        } finally {
            PAUSING_LOOKUP.release.countDown();
        }

        rule.assertBuildStatus(Result.UNSTABLE, rule.waitForCompletion(run));
        ChecksDetails a = PAUSING_LOOKUP.published.get("Tests / a");
        assertThat(a, notNullValue());
        assertThat(a.getOutput().orElseThrow().getTitle().orElseThrow(), is("A.failsInA failed"));
        String text = a.getOutput().orElseThrow().getText().orElseThrow();
        assertThat(text, containsString("failsInA"));
        assertThat(text, not(containsString("failsInB")));
    }

    /** Stands in for a checks publisher whose call to the SCM host hangs until released. */
    public static class HangingPublishFactory extends ChecksPublisherFactory {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile ChecksDetails published;

        @Override
        protected Optional<ChecksPublisher> createPublisher(Run<?, ?> run, TaskListener listener) {
            return Optional.of(new ChecksPublisher() {
                @Override
                public void publish(ChecksDetails details) {
                    entered.countDown();
                    await(release);
                    published = details;
                }
            });
        }
    }

    /** Pauses the first publisher lookup until released, and records what each step publishes by checks name. */
    public static class PausingLookupFactory extends ChecksPublisherFactory {
        final CountDownLatch paused = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);
        final Map<String, ChecksDetails> published = new ConcurrentHashMap<>();

        @Override
        protected Optional<ChecksPublisher> createPublisher(Run<?, ?> run, TaskListener listener) {
            if (first.getAndSet(false)) {
                paused.countDown();
                await(release);
            }
            return Optional.of(new ChecksPublisher() {
                @Override
                public void publish(ChecksDetails details) {
                    published.put(details.getName().orElseThrow(), details);
                }
            });
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
