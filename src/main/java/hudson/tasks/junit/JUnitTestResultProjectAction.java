package hudson.tasks.junit;

import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.tasks.test.TestResultProjectAction;


public class JUnitTestResultProjectAction extends TestResultProjectAction<TestResultAction> {

    public JUnitTestResultProjectAction(Job<?, ?> job) {
        super(job);
    }

    @Deprecated
    public JUnitTestResultProjectAction(AbstractProject<?, ?> project) {
        super(project);
    }


    @Override
    public Class<TestResultAction> getTestResultActionClass() {
        return TestResultAction.class;
    }
}
