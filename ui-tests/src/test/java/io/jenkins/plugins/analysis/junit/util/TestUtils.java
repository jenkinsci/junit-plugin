package io.jenkins.plugins.analysis.junit.util;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JUnitPublisher;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class TestUtils {

    public static Build createFreeStyleJobWithResources(AbstractJUnitTest abstractJUnitTestBaseClass, List<String> resourcePaths, String expectedBuildResult) {
        FreeStyleJob j = abstractJUnitTestBaseClass.jenkins.jobs.create();
        FixedCopyJobDecorator fixedCopyJob = new FixedCopyJobDecorator(j);
        fixedCopyJob.getJob().configure();
        for (String resourcePath : resourcePaths) {
            fixedCopyJob.copyResource(abstractJUnitTestBaseClass.resource(resourcePath));
        }
        fixedCopyJob.getJob().addPublisher(JUnitPublisher.class).testResults.set("*.xml");
        fixedCopyJob.getJob().save();

        Build build = fixedCopyJob.getJob().startBuild();
        assertThat(build.getResult()).isEqualTo(expectedBuildResult);
        build.open();
        return build;
    }

    public static <ElementType> void assertElementInCollection(Collection<ElementType> collection, Predicate<ElementType>...predicates) {
        assertThat(Stream.of(predicates).allMatch(predicate -> collection.stream()
                .filter(predicate)
                .findAny()
                .isPresent())).isTrue();
    }
}
