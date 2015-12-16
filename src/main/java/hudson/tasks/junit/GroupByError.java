/*
 * The MIT License
 *
 * Copyright (c) 2015, Hyunil Shin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks.junit;

import hudson.tasks.test.TestObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * 
 * Group case results by error message.
 */
public class GroupByError {
	private final TestObject testObject;


	/**
	 * All {@link GroupedCaseResults}
	 */
	private HashMap<String, GroupedCaseResults> groups;

	public GroupByError(TestObject testObject) {
		this.testObject = testObject;

	}


	public void group(String minSimilarity) {
		double val = 0.5f;			// 0.5f: default value
		try {
			double tmp = Double.valueOf(minSimilarity);
			if(tmp >= 0.0f && tmp <= 1.0f) {
				val = tmp;
			}
		}catch(NumberFormatException e) {
		}

		group(val);
	}

    private void group(double minSimilarity) {
		groups = new HashMap<String, GroupedCaseResults>();

		List<CaseResult> failedCases = (List<CaseResult>) testObject.getResultInRun(testObject.getRun()).getFailedTests();
		for(CaseResult cr: failedCases) {
			add(cr, minSimilarity);
		}

    }

	private void add(CaseResult cr, double minSimilarity) {
		for(GroupedCaseResults g: groups.values()) {
			// If the similarity of two case results is greater than the parameter (minSimilarity), they are grouped.
			if(g.similar(cr, minSimilarity)) {
				// add the case to the existing group
				g.add(cr);
				return;
			}
		}

		// add the case to a new group
		GroupedCaseResults g = new GroupedCaseResults(cr.getShortErrorMessage());
		g.add(cr);
		groups.put(cr.getShortErrorMessage(), g);
	}

	public TestObject getTestObject() {
		return testObject;
	}


	/**
	 * 
	 * @return The list of representative error messages.
	 */
	public Set<String> getRepresentativeErrorMessages() {
		return groups.keySet();
	}

	/**
	 * 
	 * @return All {@link GroupedCaseResults}.
	 */
	public List<GroupedCaseResults> getGroups() {
		return new ArrayList<GroupedCaseResults>(groups.values());
	}

}
