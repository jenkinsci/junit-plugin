package io.jenkins.plugins.junit.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.tasks.junit.CaseResult;
import org.junit.jupiter.api.Test;

class CaseResultSummaryTest {

    @Test
    void namesMatchCaseResult() {
        CaseResultSummary summary =
                new CaseResultSummary(3, "suite", "org.example.FooTest", "testBar", false, false, 1.5f);
        CaseResult caseResult =
                new CaseResult(null, "org.example.FooTest", "testBar", null, null, 1.5f, null, null, null);
        assertEquals(caseResult.getPackageName(), summary.getPackageName());
        assertEquals(caseResult.getSimpleName(), summary.getSimpleName());
        assertEquals(caseResult.getName(), summary.getName());
        assertEquals(3, summary.getBuild());
        assertEquals(1.5f, summary.getDuration());
    }

    @Test
    void rootPackageAndMissingName() {
        CaseResultSummary summary = new CaseResultSummary(1, null, "FooTest", "", false, false, 0);
        assertEquals("(root)", summary.getPackageName());
        assertEquals("FooTest", summary.getSimpleName());
        assertEquals("(?)", summary.getName());
    }

    @Test
    void skippedTakesPrecedenceOverFailed() {
        CaseResultSummary summary = new CaseResultSummary(1, null, "a.B", "c", true, true, 0);
        assertTrue(summary.isSkipped());
        assertFalse(summary.isFailed());
        assertFalse(summary.isPassed());
    }
}
