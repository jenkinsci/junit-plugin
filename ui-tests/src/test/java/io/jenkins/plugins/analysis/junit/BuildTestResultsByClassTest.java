package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResults;
import io.jenkins.plugins.analysis.junit.testresults.BuildTestResultsByPackage;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.testresults.BuildDetailClassViewAssert.assertThat;
import static io.jenkins.plugins.analysis.junit.testresults.BuildDetailPackageViewAssert.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests the published unit tests results of a build which are filtered by a class.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildTestResultsByClassTest extends AbstractJUnitTest {
    @Test
    public void verifyWithFailures() {
        // TODO: @Michi
    }

    @Test
    public void verifyWithNoFailures() {
        // TODO: @Michi
    }

    @Test
    public void verifyLinkToTestDetail() {
        // TODO: @Michi
    }
}
