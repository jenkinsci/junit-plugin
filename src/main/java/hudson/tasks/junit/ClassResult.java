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

import hudson.model.Run;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestResult;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;

/**
 * Cumulative test result of a test class.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ClassResult extends TabulatedResult implements Comparable<ClassResult> {
    private final String className; // simple name
    private transient String safeName;

    private final Set<CaseResult> cases = new TreeSet<CaseResult>();

    private int passCount, failCount, skipCount;

    private float duration;

    private long startTime;

    private final PackageResult parent;

    public ClassResult(PackageResult parent, String className) {
        this.parent = parent;
        this.className = className;
        this.startTime = -1;
    }

    @Override
    public Run<?, ?> getRun() {
        return parent == null ? null : parent.getRun();
    }

    @Override
    public PackageResult getParent() {
        return parent;
    }

    @Override
    public ClassResult getPreviousResult() {
        if (parent == null) {
            return null;
        }
        TestResult pr = parent.getPreviousResult();
        if (pr == null) {
            return null;
        }
        if (pr instanceof PackageResult) {
            return ((PackageResult) pr).getClassResult(getName());
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
        return getCaseResult(caseName);
    }

    @Override
    public String getTitle() {
        return Messages.ClassResult_getTitle(getDisplayName());
    }

    @Override
    public String getChildTitle() {
        return "Class Results";
    }

    @Override
    public String getChildType() {
        return "case";
    }

    @Exported(visibility = 999)
    @Override
    public String getName() {
        int idx = className.lastIndexOf('.');
        if (idx < 0) {
            return className;
        } else {
            return className.substring(idx + 1);
        }
    }

    public @Override synchronized String getSafeName() {
        if (safeName != null) {
            return safeName;
        }
        return safeName = uniquifyName(parent.getChildren(), safe(getName()));
    }

    public CaseResult getCaseResult(String name) {
        for (CaseResult c : cases) {
            if (c.getSafeName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public Object getDynamic(String name, StaplerRequest2 req, StaplerResponse2 rsp) {
        CaseResult c = getCaseResult(name);
        if (c != null) {
            return c;
        } else {
            return super.getDynamic(name, req, rsp);
        }
    }

    @Exported(name = "child")
    @Override
    public Collection<CaseResult> getChildren() {
        return cases;
    }

    @Override
    public boolean hasChildren() {
        return (cases != null) && (cases.size() > 0);
    }

    // TODO: wait for stapler 1.60     @Exported
    @Override
    public float getDuration() {
        return duration;
    }

    public long getStartTime() {
        return startTime;
    }

    @Exported
    @Override
    public int getPassCount() {
        return passCount;
    }

    @Exported
    @Override
    public int getFailCount() {
        return failCount;
    }

    @Exported
    @Override
    public int getSkipCount() {
        return skipCount;
    }

    public void add(CaseResult r) {
        if (startTime == -1) {
            startTime = r.getStartTime();
        } else if (r.getStartTime() != -1) {
            startTime = Math.min(startTime, r.getStartTime());
        }
        cases.add(r);
    }

    /**
     * Recount my children.
     */
    @Override
    public void tally() {
        passCount = failCount = skipCount = 0;
        duration = 0;
        for (CaseResult r : cases) {
            r.setClass(this);
            if (r.isSkipped()) {
                skipCount++;
            } else if (r.isPassed()) {
                passCount++;
            } else {
                failCount++;
            }
            duration += r.getDuration();
        }
    }

    void freeze() {
        this.tally();
    }

    public String getClassName() {
        return className;
    }

    @Override
    public int compareTo(ClassResult that) {
        if (this.equals(that)) {
            return 0;
        }
        int r = this.className.compareTo(that.className);
        if (r != 0) {
            return r;
        }
        // Only equals is exact reference
        return System.identityHashCode(this) >= System.identityHashCode(that) ? 1 : -1;
    }

    // Method overridden to provide explicit declaration of the equivalence relation used
    // as Comparable is also implemented
    @Override
    public boolean equals(Object obj) {
        return (this == obj);
    }

    // Method overridden to provide explicit declaration of the equivalence relation used
    // as Comparable is also implemented
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String getDisplayName() {
        return TestNameTransformer.getTransformedName(getName());
    }

    /**
     * @since 1.515
     */
    @Override
    public String getFullName() {
        return getParent().getName() + "." + className;
    }

    @Override
    public String getFullDisplayName() {
        return getParent().getDisplayName() + "." + TestNameTransformer.getTransformedName(className);
    }

    /**
     * Gets the relative path to this test case from the given object.
     */
    @Override
    public String getRelativePathFrom(TestObject it) {
        if (it instanceof CaseResult) {
            return "..";
        } else {
            return super.getRelativePathFrom(it);
        }
    }

    public void setStartTime(long start) {
        this.startTime = start;
    }

    private static final long serialVersionUID = 1L;
}
