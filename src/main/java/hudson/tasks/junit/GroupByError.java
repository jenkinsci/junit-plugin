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

import org.apache.commons.lang3.StringUtils;

/**
 * 
 * Group case results by error message.
 */
public class GroupByError {
	private final TestObject testObject;

	/**
     * Minimum distance for testing whether they are similar.
	 * 0.9f is just personal opinion.
	 */
	private double minDist = 0.9f;
	
	/**
	 * All {@link GroupedCaseResults}
	 */
	private final HashMap<String, GroupedCaseResults> groups;

	public GroupByError(TestObject testObject) {
		this.testObject = testObject;

		groups = new HashMap<String, GroupedCaseResults>();
		
		// generate groups
		List<CaseResult> failedCases = (List<CaseResult>) testObject.getResultInRun(testObject.getRun()).getFailedTests();
		for(CaseResult cr: failedCases) {
			add(cr);
		}
	}
	
	private void add(CaseResult cr) {
		for(GroupedCaseResults g: groups.values()) {
			if(g.similar(cr, minDist)) {
				// add case to the existing group
				g.add(cr);
				return;
			}
		}
	
		// add a new group
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