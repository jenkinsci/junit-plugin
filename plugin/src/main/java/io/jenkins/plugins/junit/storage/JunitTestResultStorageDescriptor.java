package io.jenkins.plugins.junit.storage;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

@Restricted(Beta.class)
public class JunitTestResultStorageDescriptor extends Descriptor<JunitTestResultStorage> {
    public static DescriptorExtensionList<JunitTestResultStorage, JunitTestResultStorageDescriptor> all() {
        return Jenkins.get().getDescriptorList(JunitTestResultStorage.class);
    }
}
