package io.jenkins.plugins.analysis.junit.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JUnitPublisher;
import org.jenkinsci.test.acceptance.po.Job;

import io.jenkins.plugins.analysis.junit.JUnitJobConfiguration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class TestUtils {

    /**
     * Creates a freestyle Job with resources and runs build with expected build result.
     * @param abstractJUnitTestBaseClass the caller's test class
     * @param resourcePaths resource paths of test result reports
     * @param expectedBuildResult expected build results for assertion
     * @return created and ran build
     */
    public static Build createFreeStyleJobAndRunBuild(AbstractJUnitTest abstractJUnitTestBaseClass,
            List<String> resourcePaths, String expectedBuildResult) {
        return createFreeStyleJobAndRunBuild(abstractJUnitTestBaseClass, resourcePaths, expectedBuildResult,
                false, false, false);
    }

    /**
     * Creates a freestyle Job with resources and runs build with expected build result.
     * @param abstractJUnitTestBaseClass the caller's test class
     * @param resourcePaths resource paths of test result reports
     * @param expectedBuildResult expected build results for assertion
     * @param setSkipMarkingBuildAsUnstableOnTestFailure configures freestyle job to skip marking build as unstable on test failure
     * @param setAllowEmptyResults configures freestyle job to allow empty test result reports
     * @param setRetainLogStandardOutputError configures freestyle job to retain log standard output error
     * @return created and ran build
     */
    public static Build createFreeStyleJobAndRunBuild(AbstractJUnitTest abstractJUnitTestBaseClass,
            List<String> resourcePaths, String expectedBuildResult,
            Boolean setSkipMarkingBuildAsUnstableOnTestFailure, Boolean setAllowEmptyResults,
            Boolean setRetainLogStandardOutputError) {
        Build build = getCreatedFreeStyleJobWithResources(abstractJUnitTestBaseClass, resourcePaths,
                setSkipMarkingBuildAsUnstableOnTestFailure, setAllowEmptyResults,
                setRetainLogStandardOutputError).startBuild();
        assertThat(build.getResult()).isEqualTo(expectedBuildResult);
        build.open();
        return build;
    }

    /**
     * Creates a freestyle Job with resources.
     * @param abstractJUnitTestBaseClass the caller's test class
     * @param resourcePaths resource paths of test result reports
     * @param setSkipMarkingBuildAsUnstableOnTestFailure configures freestyle job to skip marking build as unstable on test failure
     * @param setAllowEmptyResults configures freestyle job to allow empty test result reports
     * @param setRetainLogStandardOutputError configures freestyle job to retain log standard output error
     * @return created freestyle job.
     */
    public static Job getCreatedFreeStyleJobWithResources(AbstractJUnitTest abstractJUnitTestBaseClass,
            List<String> resourcePaths,
            Boolean setSkipMarkingBuildAsUnstableOnTestFailure, Boolean setAllowEmptyResults,
            Boolean setRetainLogStandardOutputError) {
        FreeStyleJob j = abstractJUnitTestBaseClass.jenkins.jobs.create();
        FixedCopyJobDecorator fixedCopyJob = new FixedCopyJobDecorator(j);
        fixedCopyJob.getJob().configure();
        for (String resourcePath : resourcePaths) {
            fixedCopyJob.copyResource(abstractJUnitTestBaseClass.resource(resourcePath));
        }
        JUnitJobConfiguration publisher = fixedCopyJob.getJob().addPublisher(JUnitJobConfiguration.class);
        publisher.testResults.set("*.xml");

        publisher.setSkipMarkingBuildAsUnstableOnTestFailure(setSkipMarkingBuildAsUnstableOnTestFailure);
        publisher.setRetainLogStandardOutputError(setRetainLogStandardOutputError);
        publisher.setAllowEmptyResults(setAllowEmptyResults);

        fixedCopyJob.getJob().save();

        return fixedCopyJob.getJob();
    }

    /**
     * Creates a freestyle and runs two consecutive builds with different test result report which increases failure count
     * in the second build.
     * @param abstractJUnitTestBaseClass the caller's test class
     * @return second build
     */
    public static Build createTwoBuildsWithIncreasedTestFailures(AbstractJUnitTest abstractJUnitTestBaseClass) {
        Job job = getCreatedFreeStyleJobWithResources(abstractJUnitTestBaseClass,
                Arrays.asList("/failure/three_failed_two_succeeded.xml", "/failure/four_failed_one_succeeded.xml"),
                false, false, false);

        job.startBuild().shouldBeUnstable();

        job.configure();
        job.editPublisher(JUnitJobConfiguration.class, (publisher) -> {
            publisher.testResults.set("four_failed_one_succeeded.xml");
        });

        job.startBuild().shouldBeUnstable().openStatusPage();
        return job.getLastBuild();
    }

    /**
     * Asserts given predicates within the given collection.
     * @param collection collection to be asserted
     * @param predicates assertion criteria
     * @param <ElementType> the type of elements in this collection
     */
    public static <ElementType> void assertElementInCollection(Collection<ElementType> collection,
            Predicate<ElementType>... predicates) {
        assertThat(Stream.of(predicates).allMatch(predicate -> collection.stream()
                .filter(predicate)
                .findAny()
                .isPresent())).isTrue();
    }
}
