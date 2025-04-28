package hudson.tasks.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import hudson.Functions;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Shell;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.test.helper.BuildPage;
import hudson.tasks.test.helper.ProjectPage;
import hudson.tasks.test.helper.WebClientFactory;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class AggregatedTestResultPublisherTest {
    public static final String TEST_PROJECT_NAME = "junit";
    public static final String AGGREGATION_PROJECT_NAME = "aggregated";

    private FreeStyleProject upstreamProject;
    private FreeStyleProject downstreamProject;

    private FreeStyleBuild build;
    private JenkinsRule.WebClient wc;
    private static final String[] singleContents = {"abcdef"};
    private static final String[] singleFiles = {"test.txt"};
    private BuildPage buildPage;
    private ProjectPage projectPage;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        wc = WebClientFactory.createWebClientWithDisabledJavaScript(j);
    }

    @LocalData
    @Test
    void aggregatedTestResultsOnly() throws Exception {
        createUpstreamProjectWithNoTests();
        createDownstreamProjectWithTests();

        buildAndSetupPageObjects();

        projectPage
                .getLatestAggregatedTestReportLink()
                .assertHasLatestAggregatedTestResultText()
                .assertHasTests()
                .follow()
                .hasLinkToTestResultOfBuild(TEST_PROJECT_NAME, 1);

        projectPage.assertNoTestReportLink();

        buildPage
                .getAggregatedTestReportLink()
                .assertHasAggregatedTestResultText()
                .assertHasTests()
                .follow()
                .hasLinkToTestResultOfBuild(TEST_PROJECT_NAME, 1);
        buildPage.assertNoTestReportLink();
    }

    @LocalData
    @Test
    void testResultsOnly() throws Exception {
        createUpstreamProjectWithTests();
        createDownstreamProjectWithNoTests();

        buildAndSetupPageObjects();

        projectPage
                .getLatestTestReportLink()
                .assertHasLatestTestResultText()
                .assertHasTests()
                .follow();
        projectPage
                .getLatestAggregatedTestReportLink()
                .assertHasLatestAggregatedTestResultText()
                .assertNoTests()
                .follow();

        buildPage.getTestReportLink().assertHasTestResultText().assertHasTests().follow();
        buildPage
                .getAggregatedTestReportLink()
                .assertHasAggregatedTestResultText()
                .assertNoTests()
                .follow();
    }

    @LocalData
    @Test
    void testResultsAndAggregatedTestResults() throws Exception {
        createUpstreamProjectWithTests();
        createDownstreamProjectWithTests();

        buildAndSetupPageObjects();

        projectPage
                .getLatestTestReportLink()
                .assertHasLatestTestResultText()
                .assertHasTests()
                .follow();
        projectPage
                .getLatestAggregatedTestReportLink()
                .assertHasLatestAggregatedTestResultText()
                .assertHasTests()
                .follow();

        buildPage.getTestReportLink().assertHasTestResultText().assertHasTests().follow();
        buildPage
                .getAggregatedTestReportLink()
                .assertHasAggregatedTestResultText()
                .assertHasTests()
                .follow()
                .hasLinkToTestResultOfBuild(TEST_PROJECT_NAME, 1);
    }

    private void buildAndSetupPageObjects() throws Exception {
        buildOnce();
        projectPage = new ProjectPage(wc.getPage(upstreamProject));
        buildPage = new BuildPage(wc.getPage(build));
    }

    private void buildOnce() throws Exception {
        build(1);
    }

    private void build(int numberOfDownstreamBuilds) throws Exception {
        build = j.buildAndAssertSuccess(upstreamProject);
        j.waitUntilNoActivity();

        List<AbstractBuild<?, ?>> downstreamBuilds = StreamSupport.stream(
                        build.getDownstreamBuilds(downstreamProject).spliterator(), false)
                .collect(Collectors.toList());
        assertThat(downstreamBuilds, hasSize(numberOfDownstreamBuilds));
    }

    private void createUpstreamProjectWithTests() throws Exception {
        createUpstreamProjectWithNoTests();
        addJUnitResultArchiver(upstreamProject);
    }

    private void createUpstreamProjectWithNoTests() throws Exception {
        upstreamProject = j.createFreeStyleProject(AGGREGATION_PROJECT_NAME);
        addFingerprinterToProject(upstreamProject, singleContents, singleFiles);
        upstreamProject.setQuietPeriod(0);
    }

    private void createDownstreamProjectWithTests() throws Exception {
        createDownstreamProjectWithNoTests();

        addJUnitResultArchiver(downstreamProject);
        j.jenkins.rebuildDependencyGraph();
    }

    private void createDownstreamProjectWithNoTests() throws Exception {
        downstreamProject = j.createFreeStyleProject(TEST_PROJECT_NAME);
        downstreamProject.setQuietPeriod(0);
        addFingerprinterToProject(downstreamProject, singleContents, singleFiles);

        upstreamProject
                .getPublishersList()
                .add(new BuildTrigger(Collections.singletonList(downstreamProject), Result.SUCCESS));
        upstreamProject.getPublishersList().add(new AggregatedTestResultPublisher(null));

        j.jenkins.rebuildDependencyGraph();
    }

    private void addJUnitResultArchiver(FreeStyleProject project) {
        JUnitResultArchiver archiver = new JUnitResultArchiver("*.xml");
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TouchBuilder());
    }

    private void addFingerprinterToProject(FreeStyleProject project, String[] contents, String[] files) {
        StringBuilder targets = new StringBuilder();
        for (int i = 0; i < contents.length; i++) {
            String command = "echo $BUILD_NUMBER " + contents[i] + " > " + files[i];
            project.getBuildersList().add(Functions.isWindows() ? new BatchFile(command) : new Shell(command));
            targets.append(files[i]).append(',');
        }

        project.getPublishersList().add(new Fingerprinter(targets.toString()));
    }
}
