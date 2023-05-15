package hudson.tasks.junit.rot13;

import hudson.tasks.junit.TestAction;

public abstract class Rot13CipherAction extends TestAction {

    private final String ciphertext;

    public Rot13CipherAction(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    @Override
    public final String getIconFileName() {
        return null;
    }

    @Override
    public final String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    public String getCiphertext() {
        return ciphertext;
    }

}
