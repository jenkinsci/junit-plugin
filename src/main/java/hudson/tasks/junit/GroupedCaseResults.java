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
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.export.Exported;

/**
 * Case results with similar error message.
 */
public class GroupedCaseResults {
    private static final Logger LOGGER = Logger.getLogger(GroupedCaseResults.class.getName());
    
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

    public boolean similar(CaseResult cr, double minDist) {
    	
		if(StringUtils.getJaroWinklerDistance(repErrorMessage, cr.getShortErrorMessage()) >= minDist) {
			return true;
		}else {
			return false;
		}
    }
}
