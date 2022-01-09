package io.jenkins.plugins.analysis.junit;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.WithPlugins;

/**
 * Tests the detail view of a build's failed Unit tests.
 *
 * @author MichaelMüller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildDetailTest {

    @Test
    public void verifyDetailWithFailures() {
        // TODO: verify listed failures, failure count, error details + stack trace by test
    }

    @Test
    public void verifyDetailNoFailures() {
        // TODO: verify listed failures (0), failure count (0), test count (1)
    }

    @Test
    public void verifyDetailWithPreviousTests() {
        // TODO: verify change since last build
    }
}
