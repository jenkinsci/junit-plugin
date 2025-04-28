package io.jenkins.plugins.junit.checks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.FilePath;
import hudson.model.Result;
import hudson.tasks.junit.TestResultTest;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import java.util.List;
import java.util.Objects;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class JUnitChecksPublisherTest {

    @TestExtension
    public static final CapturingChecksPublisher.Factory PUBLISHER_FACTORY = new CapturingChecksPublisher.Factory();

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        this.rule = rule;
    }

    @AfterEach
    void clearPublishedChecks() {
        PUBLISHER_FACTORY.getPublishedChecks().clear();
    }

    private ChecksDetails getDetail() {
        List<ChecksDetails> details = getDetails();
        assertThat(details.size(), is(1));
        return details.get(0);
    }

    private List<ChecksDetails> getDetails() {
        return PUBLISHER_FACTORY.getPublishedChecks();
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void extractChecksDetailsPassingTestResults() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            touch 'test-result.xml'
                            def results = junit(testResults: '*.xml')
                            assert results.totalCount == 6
                          }
                        }
                        """,
                true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        ChecksDetails checksDetails = getDetail();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.SUCCESS));
        assertThat(checksDetails.getName().get(), is("Tests / first"));

        ChecksOutput output = checksDetails.getOutput().get();

        assertThat(output.getTitle().get(), is("passed: 6"));
        assertThat(output.getSummary().get(), is("passed: 6"));
        assertThat(output.getText().get(), is(""));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void extractChecksDetailsFailingSingleTestResult() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            def results = junit(testResults: '*.xml')
                            assert results.totalCount == 6
                          }
                        }
                        """,
                true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-errror-details.xml"));

        rule.buildAndAssertStatus(Result.FAILURE, j);

        ChecksDetails checksDetails = getDetail();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.FAILURE));
        assertThat(checksDetails.getName().get(), is("Tests / first"));

        ChecksOutput output = checksDetails.getOutput().get();

        assertThat(output.getTitle().get(), is("some.package.somewhere.WhooHoo.testHudsonReporting failed"));
        assertThat(output.getSummary().get(), is("some.package.somewhere.WhooHoo.testHudsonReporting failed"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void extractChecksDetailsFailingMultipleTests() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            def results = junit(testResults: '*.xml')
                            assert results.totalCount == 6
                          }
                        }
                        """,
                true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-20090516.xml"));

        rule.buildAndAssertStatus(Result.FAILURE, j);

        ChecksDetails checksDetails = getDetail();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.FAILURE));
        assertThat(checksDetails.getName().get(), is("Tests / first"));

        ChecksOutput output = checksDetails.getOutput().get();

        assertThat(output.getTitle().get(), is("failed: 3, passed: 5"));
        assertThat(output.getSummary().get(), is("failed: 3, passed: 5"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void extractChecksDetailsCustomCheckName() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            touch 'test-result.xml'
                            def results = junit(testResults: '*.xml', checksName: 'Custom Checks Name')
                            assert results.totalCount == 6
                          }
                        }
                        """,
                true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        ChecksDetails checksDetails = getDetail();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.SUCCESS));

        assertThat(checksDetails.getName().get(), is("Custom Checks Name"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void extractChecksDetailsNoStageContext() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        node {
                          touch 'test-result.xml'
                          def results = junit(testResults: '*.xml')
                          assert results.totalCount == 6
                        }
                        """,
                true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        ChecksDetails checksDetails = getDetail();

        assertThat(checksDetails.getName().get(), is("Tests"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void extractChecksDetailsNestedStages() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') { stage('second') {
                          node {
                            def results = junit(testResults: '*.xml')
                            assert results.totalCount == 6
                          }
                        }}
                        """,
                true));
        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        ChecksDetails checksDetails = getDetail();

        assertThat(checksDetails.getName().get(), is("Tests / first / second"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void extractChecksDetailsEmptySuite() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            def results = junit(testResults: '*.xml', allowEmptyResults: true)
                            assert results.totalCount == 0
                          }
                        }
                        """,
                true));

        rule.buildAndAssertSuccess(j);

        ChecksDetails checksDetails = getDetail();

        assertThat(checksDetails.getConclusion(), is(ChecksConclusion.SUCCESS));

        ChecksOutput output = checksDetails.getOutput().get();

        assertThat(output.getTitle().get(), is("No test results found"));
        assertThat(output.getText().get(), is(""));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void withChecksContext() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            touch 'test-result.xml'
                            withChecks('With Checks') {
                              def results = junit '*.xml'
                              assert results.totalCount == 6
                            }
                          }
                        }
                        """,
                true));

        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        List<ChecksDetails> checksDetails = getDetails();

        assertThat(checksDetails.size(), is(2));

        assertThat(checksDetails.get(0).getName().get(), is("With Checks"));
        assertThat(checksDetails.get(0).getStatus(), is(ChecksStatus.IN_PROGRESS));
        assertThat(checksDetails.get(0).getConclusion(), is(ChecksConclusion.NONE));

        assertThat(checksDetails.get(1).getName().get(), is("With Checks"));
        assertThat(checksDetails.get(1).getStatus(), is(ChecksStatus.COMPLETED));
        assertThat(checksDetails.get(1).getConclusion(), is(ChecksConclusion.SUCCESS));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void withChecksContextDeclarative() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        pipeline {
                          agent any
                          stages {
                            stage('first') {
                              steps {
                                withChecks('With Checks') {
                                  junit(testResults: '*.xml')
                                }
                              }
                            }
                          }
                        }""",
                true));

        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        List<ChecksDetails> checksDetails = getDetails();

        assertThat(checksDetails.size(), is(2));

        assertThat(checksDetails.get(0).getName().get(), is("With Checks"));
        assertThat(checksDetails.get(0).getStatus(), is(ChecksStatus.IN_PROGRESS));
        assertThat(checksDetails.get(0).getConclusion(), is(ChecksConclusion.NONE));

        assertThat(checksDetails.get(1).getName().get(), is("With Checks"));
        assertThat(checksDetails.get(1).getStatus(), is(ChecksStatus.COMPLETED));
        assertThat(checksDetails.get(1).getConclusion(), is(ChecksConclusion.SUCCESS));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void withChecksContextWithCustomName() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
        j.setDefinition(new CpsFlowDefinition(
                """
                        stage('first') {
                          node {
                            touch 'test-result.xml'
                            withChecks('With Checks') {
                              def results = junit(testResults: '*.xml', checksName: 'Custom Checks Name')
                              assert results.totalCount == 6
                            }
                          }
                        }
                        """,
                true));

        FilePath ws = rule.jenkins.getWorkspaceFor(j);
        FilePath testFile = Objects.requireNonNull(ws).child("test-result.xml");
        testFile.copyFrom(TestResultTest.class.getResource("junit-report-1463.xml"));

        rule.buildAndAssertSuccess(j);

        List<ChecksDetails> checksDetails = getDetails();

        assertThat(checksDetails.size(), is(2));

        assertThat(checksDetails.get(0).getName().get(), is("With Checks"));
        assertThat(checksDetails.get(0).getStatus(), is(ChecksStatus.IN_PROGRESS));
        assertThat(checksDetails.get(0).getConclusion(), is(ChecksConclusion.NONE));

        assertThat(checksDetails.get(1).getName().get(), is("Custom Checks Name"));
        assertThat(checksDetails.get(1).getStatus(), is(ChecksStatus.COMPLETED));
        assertThat(checksDetails.get(1).getConclusion(), is(ChecksConclusion.SUCCESS));
    }
}
