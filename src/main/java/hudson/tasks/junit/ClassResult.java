/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, id:cactusman, Tom Huybrechts, Yahoo!, Inc.
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

import hudson.Util;
import hudson.model.Run;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.TestObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static hudson.tasks.junit.TestResult.nullSafeEq;

/**
 * Cumulative test result of a test class.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ClassResult extends TabulatedResult implements Comparable<ClassResult> {
    private final String className; // simple name
    private transient String safeName;

    private final List<CaseResult> cases = new ArrayList<CaseResult>();

    private int passCount,failCount,skipCount;
    
    private float duration; 

    private final PackageResult parent;

    private transient String archiveId;

    ClassResult(PackageResult parent, String className) {
        this.parent = parent;
        this.className = className;
    }

    @Override
    public Run<?, ?> getRun() {
        return (parent==null ? null: parent.getRun());
    }

    public PackageResult getParent() {
        return parent;
    }

    @Override
    public ClassResult getPreviousResult() {
        if(parent==null)   return null;
        TestResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        if(pr instanceof PackageResult) {
            return ((PackageResult)pr).getClassResult(getName());
    }
        return null;
    }

    @Override
    public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
        String myID = safe(getName());
        String caseName = id;
        int base = id.indexOf(myID);
        if (base > 0) {
            int caseNameStart = base + myID.length() + 1;
			if (id.length() > caseNameStart) {
            	caseName = id.substring(caseNameStart);
            }
        } 
        CaseResult child = getCaseResult(caseName);
        if (child != null) {
            return child;
        }
        return null;
    }

    public String getTitle() {
        return Messages.ClassResult_getTitle(getDisplayName());
    }

    @Override
    public String getChildTitle() {
        return "Class Results";
    }

    @Exported(visibility=999)
    public String getName() {
        int idx = className.lastIndexOf('.');
        if(idx<0)       return className;
        else            return className.substring(idx+1);
    }

    public @Override synchronized String getSafeName() {
        if (safeName != null) {
            return safeName;
        }
        return safeName = uniquifyName(parent.getChildren(), safe(getName()));
    }
    
    public CaseResult getCaseResult(String name) {
        for (CaseResult c : cases) {
            if(c.getSafeName().equals(name))
                return c;
        }
        return null;
    }

    @Override
    public Object getDynamic(String name, StaplerRequest req, StaplerResponse rsp) {
    	CaseResult c = getCaseResult(name);
    	if (c != null) {
            return c;
    	} else {
            return super.getDynamic(name, req, rsp);
    	}
    }


    @Exported(name="child")
    public List<CaseResult> getChildren() {
        return cases;
    }

    public boolean hasChildren() {
        return ((cases != null) && (cases.size() > 0));
    }

    // TODO: wait for stapler 1.60     @Exported
    public float getDuration() {
        return duration; 
    }
    
    @Exported
    public int getPassCount() {
        return passCount;
    }

    @Exported
    public int getFailCount() {
        return failCount;
    }

    @Exported
    public int getSkipCount() {
        return skipCount;
    }

    @Exported(visibility = 2)
    public String getArchiveId() {
        return archiveId;
    }

    public void add(CaseResult r) {
        cases.add(r);
    }

    /**
     * Recount my children.
     */
    @Override
    public void tally() {
        passCount=failCount=skipCount=0;
        duration=0;
        String arId = null;
        for (CaseResult r : cases) {
            r.setClass(this);
            if (r.isSkipped()) {
                skipCount++;
            }
            else if(r.isPassed()) {
                passCount++;
            }
            else {
                failCount++;
            }
            duration += r.getDuration();
            if (arId == null) {
                arId = r.getArchiveId();
            } else {
                if (!nullSafeEq(r.getArchiveId(), arId)) {
                    //All cases are not from the same archiveId, so then no id
                    this.archiveId = null;
                } else {
                    //So far the same
                    this.archiveId = arId;
                }
            }
        }
        if (cases.size() == 1) {
            //Only one case so the archiveId should be set to that
            this.archiveId = cases.get(0).getArchiveId();
        }
    }


    void freeze() {
        this.tally();
        Collections.sort(cases);
    }

    public String getClassName() {
    	return className;
    }

    public int compareTo(ClassResult that) {
        return this.className.compareTo(that.className);
    }

    public String getDisplayName() {
        String arId = Util.fixEmptyAndTrim(this.archiveId);
        if (arId == null) {
            return TestNameTransformer.getTransformedName(getName());
        } else {
            return "[" + arId + "] " + TestNameTransformer.getTransformedName(getName());
        }
    }
    
    /**
     * @since 1.515
     */
    @Override
    public String getFullName() {
    	return getParent().getName() + "." + className;
    }
    
    public String getFullDisplayName() {
        String arId = Util.fixEmptyAndTrim(this.archiveId);
        StringBuilder str = new StringBuilder(getParent().getDisplayName());
        str.append('.').append(TestNameTransformer.getTransformedName(className));
        if (arId != null) {
            StringBuilder s2 = new StringBuilder("[");
            s2.append(arId).append("] ");
            s2.append(str);
            str = s2;
        }
        return str.toString();
    }

    /**
     * Gets the relative path to this test case from the given object.
     */
    @Override
    public String getRelativePathFrom(TestObject it) {
        if(it instanceof CaseResult) {
        	return "..";
        } else {
            return super.getRelativePathFrom(it);
        }
    }
}
