package io.jenkins.plugins.junit.storage;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
@Restricted(Beta.class)
@Symbol("junitTestResultStorage")
public class JunitTestResultStorageConfiguration extends GlobalConfiguration {

    private JunitTestResultStorage storage = new FileJunitTestResultStorage();

    @DataBoundConstructor
    public JunitTestResultStorageConfiguration() {
        load();
    }

    @DataBoundSetter
    public void setStorage(JunitTestResultStorage storage) {
        this.storage = storage;
    }

    public JunitTestResultStorage getStorage() {
        return storage;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public static JunitTestResultStorageConfiguration get() {
        return ExtensionList.lookupSingleton(JunitTestResultStorageConfiguration.class);
    }

    public DescriptorExtensionList<JunitTestResultStorage, JunitTestResultStorageDescriptor> getDescriptors() {
        return JunitTestResultStorageDescriptor.all();
    }
}
