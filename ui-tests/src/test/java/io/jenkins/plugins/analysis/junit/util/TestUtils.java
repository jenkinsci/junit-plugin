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
     *
     * @param abstractJUnitTestBaseClass the caller's test class
     * @param resourcePaths resource paths of test result reports
     * @param expectedBuildResult expected build results for assertion
     * @return created and ran build
     */
    public static Build createFreeStyleJobAndRunBuild(AbstractJUnitTest abstractJUnitTestBaseClass,
            List<String> resourcePaths, String expectedBuildResult) {
        Build build = getCreatedFreeStyleJobWithResources(abstractJUnitTestBaseClass, resourcePaths).startBuild();
        assertThat(build.getResult()).isEqualTo(expectedBuildResult);
        build.open();
        return build;
    }

    /**
     * Creates a freestyle Job with resources.
     *
     * @param abstractJUnitTestBaseClass the caller's test class
     * @param resourcePaths resource paths of test result reports
     * @return created freestyle job.
     */
    public static Job getCreatedFreeStyleJobWithResources(AbstractJUnitTest abstractJUnitTestBaseClass,
            List<String> resourcePaths) {
        FreeStyleJob j = abstractJUnitTestBaseClass.jenkins.jobs.create();
        FixedCopyJobDecorator fixedCopyJob = new FixedCopyJobDecorator(j);
        fixedCopyJob.getJob().configure();
        for (String resourcePath : resourcePaths) {
            fixedCopyJob.copyResource(abstractJUnitTestBaseClass.resource(resourcePath));
        }
        JUnitPublisher publisher = fixedCopyJob.getJob().addPublisher(JUnitPublisher.class);
        publisher.testResults.set("*.xml");

        fixedCopyJob.getJob().save();
        return fixedCopyJob.getJob();
    }

    /**
     * Creates a freestyle Job with resources.
     *
     * @param abstractJUnitTestBaseClass the caller's test class
     * @param resourcePaths resource paths of test result reports
     * @param setSkipMarkingBuildAsUnstableOnTestFailure configures freestyle job to skip marking build as unstable on test failure
     * @param setAllowEmptyResults configures freestyle job to allow empty test result reports
     * @param setRetainLogStandardOutputError configures freestyle job to retain log standard output error
     * @return created freestyle job.
     */
    public static Job getCreatedFreeStyleJobWithResources(AbstractJUnitTest abstractJUnitTestBaseClass,
            List<String> resourcePaths, Boolean setSkipMarkingBuildAsUnstableOnTestFailure,
            Boolean setRetainLogStandardOutputError, Boolean setAllowEmptyResults) {
        FreeStyleJob j = abstractJUnitTestBaseClass.jenkins.jobs.create();
        j.configure();
        for (String resourcePath : resourcePaths) {
            j.copyResource(abstractJUnitTestBaseClass.resource(resourcePath));
        }

        JUnitJobConfiguration publisher = j.addPublisher(JUnitJobConfiguration.class);
        publisher.testResults.set("*.xml");

        publisher.setSkipMarkingBuildAsUnstableOnTestFailure(setSkipMarkingBuildAsUnstableOnTestFailure);
        publisher.setRetainLogStandardOutputError(setRetainLogStandardOutputError);
        publisher.setAllowEmptyResults(setAllowEmptyResults);

        j.save();
        return j;
    }

    /**
     * Creates a freestyle and runs two consecutive builds with different test result report which increases failure count
     * in the second build.
     *
     * @param abstractJUnitTestBaseClass the caller's test class
     * @return second build
     */
    public static Build createTwoBuildsWithIncreasedTestFailures(AbstractJUnitTest abstractJUnitTestBaseClass) {
        FreeStyleJob j = abstractJUnitTestBaseClass.jenkins.jobs.create();
        FixedCopyJobDecorator fixedCopyJob = new FixedCopyJobDecorator(j);
        fixedCopyJob.getJob().configure();
        fixedCopyJob.copyResource(abstractJUnitTestBaseClass.resource("/failure/three_failed_two_succeeded.xml"));
        fixedCopyJob.copyResource(abstractJUnitTestBaseClass.resource("/failure/four_failed_one_succeeded.xml"));
        fixedCopyJob.getJob().addPublisher(JUnitPublisher.class).testResults.set("three_failed_two_succeeded.xml");
        fixedCopyJob.getJob().save();
        fixedCopyJob.getJob().startBuild().shouldBeUnstable();

        fixedCopyJob.getJob().configure();
        fixedCopyJob.getJob().editPublisher(JUnitPublisher.class, (publisher) -> {
            publisher.testResults.set("four_failed_one_succeeded.xml");
        });

        fixedCopyJob.getJob().startBuild().shouldBeUnstable().openStatusPage();
        return fixedCopyJob.getJob().getLastBuild();
    }

    /**
     * Asserts given predicates within the given collection.
     *
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
