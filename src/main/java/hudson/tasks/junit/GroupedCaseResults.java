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

import java.util.ArrayList;
import java.util.List;

import hudson.util.EditDistance;


/**
 * Case results with similar error message.
 */
public class GroupedCaseResults {
	/**
	 * Representative error message for this group, actually one of error messages.
	 */
	private final String repErrorMessage;

	/**
	 * Case results which have similar error message.
	 */
	private final List<CaseResult> cases = new ArrayList<CaseResult>();


	public GroupedCaseResults(String repErrorMessage) {
		this.repErrorMessage = repErrorMessage;
	}

	public String getRepErrorMessage() {
		return repErrorMessage;
	}

	public String getId() {
		return cases.get(0).getId();
	}

	public List<CaseResult> getChildren() {
		return cases;
	}

	public boolean hasChildren() {
		return ((cases != null) && (cases.size() > 0));
	}

	public void add(CaseResult r) {
		cases.add(r);
	}

	public CaseResult getCaseResult(String name) {
		for (CaseResult c : cases) {
			if(c.getSafeName().equals(name))
				return c;
		}
		return null;
	}

	public int getCount() {
		return cases.size();
	}

	// http://stackoverflow.com/questions/955110/similarity-string-comparison-in-java
	public boolean similar(CaseResult cr, double minDiff) {
		
		String longer = repErrorMessage;
		String shorter = cr.getShortErrorMessage();
		if(longer.length() < shorter.length()) {		// longer should always have greater lengths.
			longer = cr.getShortErrorMessage();
			shorter = repErrorMessage;
		}

		int longerLength = longer.length();
		if(longerLength == 0) {
			return true;		// both strings are zero length.
		}

		// editDistance(): The more different two strings are, the longer their distance is.
		// 0 (toally different) <= diff <= 1 (same)
		float diff = (longerLength - EditDistance.editDistance(longer, shorter)) / (float)(longerLength);

		if(diff >= minDiff) {
			return true;
		}else {
			return false;
		}
	}
}
