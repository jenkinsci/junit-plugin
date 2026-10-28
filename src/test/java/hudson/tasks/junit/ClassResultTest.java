package hudson.tasks.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.tasks.test.TestResult;
import org.junit.jupiter.api.Test;

class ClassResultTest {

    @Test
    void testFindCorrespondingResult() {
        ClassResult classResult = new ClassResult(null, "com.example.ExampleTest");

        CaseResult caseResult = new CaseResult(null, "testCase", null);

        classResult.add(caseResult);

        TestResult result = classResult.findCorrespondingResult("extraprefix.com.example.ExampleTest.testCase");
        assertEquals(caseResult, result);
    }

    @Test
    void testFindCorrespondingResultWhereClassResultNameIsNotSubstring() {
        ClassResult classResult = new ClassResult(null, "aaaa");

        CaseResult caseResult = new CaseResult(null, "tc_bbbb", null);

        classResult.add(caseResult);

        TestResult result = classResult.findCorrespondingResult("tc_bbbb");
        assertEquals(caseResult, result);
    }

    @Test
    void testFindCorrespondingResultWhereClassResultNameIsLastInCaseResultName() {
        ClassResult classResult = new ClassResult(null, "aaaa");

        CaseResult caseResult = new CaseResult(null, "tc_aaaa", null);

        classResult.add(caseResult);

        TestResult result = classResult.findCorrespondingResult("tc_aaaa");
        assertEquals(caseResult, result);
    }
}
