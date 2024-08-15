package hudson.tasks.junit;

import static org.junit.Assert.assertEquals;

import hudson.tasks.test.TestResult;
import org.junit.Test;

public class ClassResultTest {

	@Test
	public void testFindCorrespondingResult() {
		ClassResult classResult = new ClassResult(null, "com.example.ExampleTest");
	
		CaseResult caseResult = new CaseResult(null, "testCase", null);
	
		classResult.add(caseResult);
	
		TestResult result = classResult.findCorrespondingResult("extraprefix.com.example.ExampleTest.testCase");
		assertEquals(caseResult, result);
	}

	@Test
	public void testFindCorrespondingResultWhereClassResultNameIsNotSubstring() {
		ClassResult classResult = new ClassResult(null, "aaaa");
	
		CaseResult caseResult = new CaseResult(null, "tc_bbbb", null);
	
		classResult.add(caseResult);
	
		TestResult result = classResult.findCorrespondingResult("tc_bbbb");
		assertEquals(caseResult, result);
	}

	@Test
	public void testFindCorrespondingResultWhereClassResultNameIsLastInCaseResultName() {
		ClassResult classResult = new ClassResult(null, "aaaa");
	
		CaseResult caseResult = new CaseResult(null, "tc_aaaa", null);
	
		classResult.add(caseResult);
	
		TestResult result = classResult.findCorrespondingResult("tc_aaaa");
		assertEquals(caseResult, result);
	}

}
