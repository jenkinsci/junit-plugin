package hudson.tasks.junit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests various configuration of junit plugin wrt. size of data to keep.
 */
@RunWith(Parameterized.class)
public class TrimStdioTest {

    private final static String STDIO = stdio();

    private final static int STDIO_LEN = STDIO.length();

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Parameterized.Parameter(value = 0)
    public String name;

    @Parameterized.Parameter(value = 1)
    public PluginConfig config;

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>(5);
        list.add(new Object[] { "defaults", PluginConfig.defaults() });
        list.add(new Object[] { "keep all", PluginConfig.defaults(true) });
        list.add(new Object[] { "maxPassed=10, maxFailed=100", new PluginConfig(false, 10, 100)});
        list.add(new Object[] { "maxPassed=0, maxFailed=all", new PluginConfig(false, 0, -1)});
        return list;
    }

    private static String stdio() {
        String nl = String.format("%n");
        StringBuilder builder = new StringBuilder();
        builder.append("First line is intact.").append(nl);
        for (int i = 0; i < 10; i++) {
            builder.append("Line #").append(i).append(" might be elided because we reach the limit.").append(nl);
        }
        builder.append("Last line is intact.").append(nl);
        return builder.toString();
    }

    private File createResults() throws IOException {
        File f = root.newFile("test.xml");
        PrintWriter pw = new PrintWriter(f, "UTF-8");
        try {
            pw.println("<testsuites name='x'>");
            pw.println("<testsuite failures='1' errors='0' tests='1' name='x'>");
            pw.println("<testcase name='x' classname='x'><error>oops</error></testcase>");
            pw.print("<system-out><![CDATA[");
            pw.print(STDIO);
            pw.println("]]></system-out>");
            pw.print("<system-err><![CDATA[");
            pw.print(STDIO);
            pw.print("]]></system-err>");
            pw.println("</testsuite>");
            pw.println("<testsuite failures='0' errors='0' tests='1' name='s'>");
            pw.println("<testcase name='s' classname='s' />");
            pw.print("<system-out><![CDATA[");
            pw.print(STDIO);
            pw.println("]]></system-out>");
            pw.print("<system-err><![CDATA[");
            pw.print(STDIO);
            pw.print("]]></system-err>");
            pw.println("</testsuite>");
            pw.println("</testsuites>");
            pw.flush();
        } finally {
            pw.close();
        }
        return f;
    }

    private void checkStdout(String stdout, int maxSize) {
        if (!config.isKeepLongStdio() && maxSize == 0) {
            assertNull(stdout);
            return;
        }

        assertNotNull(stdout);

        int outl = stdout.length();

        if (config.isKeepLongStdio() || maxSize < 0 || maxSize > STDIO_LEN) {
            assertEquals(stdout, STDIO_LEN, outl);
            return;
        }

        assertEquals(stdout, maxSize + 29, outl);
    }

    /**
     * Check size of both stdout and stderr wrt. maxsize.
     */
    private void checkBoth(String stdout, String stderr, int maxSize) {
        if (!config.isKeepLongStdio() && maxSize == 0) {
            assertNull(stderr);
            assertNull(stdout);
            return;
        }

        assertNotNull(stderr);
        assertNotNull(stdout);

        int outl = stdout.length();
        int errl = stderr.length();

        if (config.isKeepLongStdio() || maxSize < 0 || maxSize > STDIO_LEN) {
            assertEquals(stdout, STDIO_LEN, outl);
            assertEquals(stderr, STDIO_LEN, errl);
            return;
        }

        assertEquals(stdout, maxSize + 29, outl);
        assertEquals(stderr, maxSize + 29, errl);
    }

    @Test
    public void trimStdio() throws Exception {
        List<SuiteResult> results = SuiteResult.parse(createResults(), config);
        assertEquals(2, results.size());

        // failure
        SuiteResult failure = results.get(0);
        assertNotNull(failure);
        assertTrue(failure.getCase("x").isFailed());
        checkBoth(failure.getStdout(), failure.getStderr(), config.getMaxFailedSize());

        // success
        SuiteResult success = results.get(1);
        assertNotNull(success);
        assertTrue(success.getCase("s").isPassed());
        checkBoth(success.getStdout(), success.getStderr(), config.getMaxSucceededSize());
    }

    @Test
    public void trimStdioSurefire() throws Exception {
        File f = root.newFile("TEST-abcd.xml");
        PrintWriter pw = new PrintWriter(f, "UTF-8");
        try {
            pw.println("<testsuites name='x'>");
            pw.println("<testsuite failures='1' errors='0' tests='1' name='x'>");
            pw.println("<testcase name='x' classname='x'><error>oops</error></testcase>");
            pw.println("</testsuite>");
            pw.println("<testsuite failures='0' errors='0' tests='1' name='s'>");
            pw.println("<testcase name='s' classname='s' />");
            pw.println("</testsuite>");
            pw.println("</testsuites>");
            pw.flush();
        } finally {
            pw.close();
        }

        File data = new File(f.getParentFile(), "abcd-output.txt");
        pw = new PrintWriter(data, "UTF-8");
        try {
            pw.print(STDIO);
            pw.flush();
        } finally {
            pw.close();
        }

        List<SuiteResult> results = SuiteResult.parse(f, config);
        assertEquals(2, results.size());

        // failure
        SuiteResult failure = results.get(0);
        assertNotNull(failure);
        assertTrue(failure.getCase("x").isFailed());
        assertNull(failure.getStderr());
        checkStdout(failure.getStdout(), config.getMaxFailedSize());

        // success
        SuiteResult success = results.get(1);
        assertNotNull(success);
        assertTrue(success.getCase("s").isPassed());
        assertNull(success.getStderr());
        checkStdout(success.getStdout(), config.getMaxSucceededSize());
    }
}
