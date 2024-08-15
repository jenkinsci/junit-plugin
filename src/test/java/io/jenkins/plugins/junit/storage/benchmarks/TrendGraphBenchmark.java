package io.jenkins.plugins.junit.storage.benchmarks;

import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.test.TestResultProjectAction;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@JmhBenchmark
public class TrendGraphBenchmark {
    @State(Scope.Benchmark)
    public static class JenkinsState extends JmhBenchmarkState {

        WorkflowJob lastJob;

        public static final String SIMPLE_TEST_RESULT = "node {\n" +
            "  writeFile file: 'x.xml', text: '''<testsuite name='sweet' time='200.0'>" +
            "<testcase classname='Klazz' name='test1' time='198.0'><error message='failure'/></testcase>" +
            "<testcase classname='Klazz' name='test2' time='2.0'/>" +
            "<testcase classname='other.Klazz' name='test3'><skipped message='Not actually run.'/></testcase>" +
            "</testsuite>'''\n" +
            "  def s = junit 'x.xml'\n" +
            "  echo(/summary: fail=$s.failCount skip=$s.skipCount pass=$s.passCount total=$s.totalCount/)\n" +
            "  writeFile file: 'x.xml', text: '''<testsuite name='supersweet'>" +
            "<testcase classname='another.Klazz' name='test1'><error message='another failure'/></testcase>" +
            "</testsuite>'''\n" +
            "  s = junit 'x.xml'\n" +
            "  echo(/next summary: fail=$s.failCount skip=$s.skipCount pass=$s.passCount total=$s.totalCount/)\n" +
            "}";

        @Override
        public void setup() throws Exception {
            Jenkins.get().setNumExecutors(10);
            createLotsOfRuns("a", 1000);
            createLotsOfRuns("b", 1000);
            createLotsOfRuns("c", 1000);
            createLotsOfRuns("d", 1000);
            
            System.out.println("Next build number: " + lastJob.getNextBuildNumber());
        }

        private void createLotsOfRuns(String jobName, int runCount) throws java.io.IOException, InterruptedException, ExecutionException {
            Jenkins jenkins = Jenkins.get();
            lastJob = jenkins.createProject(WorkflowJob.class, jobName);
            lastJob.setDefinition(new CpsFlowDefinition(
                    SIMPLE_TEST_RESULT, true));
            List<QueueTaskFuture<WorkflowRun>> queueTaskFutures = new java.util.ArrayList<>(runCount);
            for (int i = 0; i < runCount; i++) {
                QueueTaskFuture<WorkflowRun> e = lastJob.scheduleBuild2(0);
                Objects.requireNonNull(e).waitForStart();
                queueTaskFutures.add(e);
                if (i % 10 == 0) {
                    Thread.sleep(100);
                }
            }
            System.out.println("Count of futures is: " + queueTaskFutures.size());
            queueTaskFutures.forEach(future -> {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            });
        }

    }
    
    @Benchmark
    public void benchmark(JenkinsState cascState, Blackhole blackhole) {
        TestResultProjectAction action = cascState.lastJob.getAction(TestResultProjectAction.class);
        blackhole.consume(action.getBuildTrendModel());
    }
}
