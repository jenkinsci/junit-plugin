/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package hudson.tasks.junit.storage;

import com.google.common.collect.ImmutableSet;
import com.thoughtworks.xstream.XStream;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.database.Database;
import org.jenkinsci.plugins.database.GlobalDatabaseConfiguration;
import org.jenkinsci.plugins.database.h2.LocalH2Database;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TestResultStorageTest {
    
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    
    @Rule public JenkinsRule r = new JenkinsRule();

    /**
     * Need {@link LocalH2Database#getAutoServer} so that {@link Impl} can make connections from an agent JVM.
     * @see <a href="http://www.h2database.com/html/features.html#auto_mixed_mode">Automatic Mixed Mode</a>
     */
    @Before public void autoServer() throws Exception {
        LocalH2Database database = (LocalH2Database) GlobalDatabaseConfiguration.get().getDatabase();
        GlobalDatabaseConfiguration.get().setDatabase(new LocalH2Database(database.getPath(), true));
    }

    @Test public void smokes() throws Exception {
        DumbSlave remote = r.createOnlineSlave(Label.get("remote"));
        //((Channel) remote.getChannel()).addListener(new LoggingChannelListener(Logger.getLogger(TestResultStorageTest.class.getName()), Level.INFO));
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('remote') {\n" +
                // TODO test skips
                "  writeFile file: 'x.xml', text: '''<testsuite name='sweet'><testcase classname='Klazz' name='test1'><error message='failure'/></testcase><testcase classname='Klazz' name='test2'/></testsuite>'''\n" +
                "  def s = junit 'x.xml'\n" +
                // TODO test repeated publishing
                "  echo(/summary: fail=$s.failCount skip=$s.skipCount pass=$s.passCount total=$s.totalCount/)\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).get();
        try (Connection connection = GlobalDatabaseConfiguration.get().getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + Impl.CASE_RESULTS_TABLE);
                ResultSet result = statement.executeQuery()) {
            printResultSet(result);
        }
        // TODO verify table structure
        r.assertBuildStatus(Result.UNSTABLE, b);
        r.assertLogContains("summary: fail=1 skip=0 pass=1 total=2", b);
        assertFalse(new File(b.getRootDir(), "junitResult.xml").isFile());
        {
            String buildXml = FileUtils.readFileToString(new File(b.getRootDir(), "build.xml"));
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(buildXml.getBytes(StandardCharsets.UTF_8)));
            NodeList testResultActionList = doc.getElementsByTagName("hudson.tasks.junit.TestResultAction");
            assertEquals(buildXml, 1, testResultActionList.getLength());
            Element testResultAction = (Element) testResultActionList.item(0);
            NodeList childNodes = testResultAction.getChildNodes();
            Set<String> childNames = new TreeSet<>();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);
                if (item instanceof Element) {
                    childNames.add(((Element) item).getTagName());
                }
            }
            assertEquals(buildXml, ImmutableSet.of("healthScaleFactor", "testData", "descriptions"), childNames);
        }
        Impl.queriesPermitted = true;
        {
            TestResultAction a = b.getAction(TestResultAction.class);
            assertNotNull(a);
            assertEquals(1, a.getFailCount());
            /* TODO implement:
            List<CaseResult> failedTests = a.getFailedTests();
            assertEquals(1, failedTests.size());
            assertEquals("Klazz", failedTests.get(0).getClassName());
            assertEquals("test1", failedTests.get(0).getName());
            assertEquals("failure", failedTests.get(0).getErrorDetails());
            */
            // TODO more detailed Java queries incl. PackageResult / ClassResult
            // TODO test healthScaleFactor, descriptions
        }
    }

    @TestExtension public static class Impl implements TestResultStorage {

        static final String CASE_RESULTS_TABLE = "caseResults";

        static boolean queriesPermitted;

        private final ConnectionSupplier connectionSupplier = new LocalConnectionSupplier();

        @Override public RemotePublisher createRemotePublisher(Run<?, ?> build) throws IOException {
            try {
                connectionSupplier.connection(); // make sure we start a local server and create table first
            } catch (SQLException x) {
                throw new IOException(x);
            }
            return new RemotePublisherImpl(build.getParent().getFullName(), build.getNumber());
        }

        @Override public TestResultImpl load(String job, int build) {
            return new TestResultImpl() {
                @Override public int getFailCount() {
                    if (!queriesPermitted) {
                        throw new IllegalStateException("Should not have been running any queries yet");
                    }
                    try {
                        Connection connection = connectionSupplier.connection();
                        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + Impl.CASE_RESULTS_TABLE + " WHERE job = ? and build = ? and errorDetails IS NOT NULL")) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            try (ResultSet result = statement.executeQuery()) {
                                result.next();
                                return result.getInt(1);
                            }
                        }
                    } catch (SQLException x) {
                        x.printStackTrace();
                        return 0;
                    }
                }
                @Override public TestResult getResultByNodes(List<String> nodeIds) {
                    return new TestResult(this); // TODO
                }
            };
        }

        private static class RemotePublisherImpl implements RemotePublisher {

            private final String job;
            private final int build;
            // TODO keep the same supplier and thus Connection open across builds, so long as the database config remains unchanged
            private final ConnectionSupplier connectionSupplier;

            RemotePublisherImpl(String job, int build) {
                this.job = job;
                this.build = build;
                connectionSupplier = new RemoteConnectionSupplier();
            }

            @Override public void publish(TestResult result, TaskListener listener) throws IOException {
                try {
                    Connection connection = connectionSupplier.connection();
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + CASE_RESULTS_TABLE + " (job, build, suite, className, testName, errorDetails) VALUES (?, ?, ?, ?, ?, ?)")) {
                        int count = 0;
                        for (SuiteResult suiteResult : result.getSuites()) {
                            for (CaseResult caseResult : suiteResult.getCases()) {
                                statement.setString(1, job);
                                statement.setInt(2, build);
                                statement.setString(3, suiteResult.getName());
                                statement.setString(4, caseResult.getClassName());
                                statement.setString(5, caseResult.getName());
                                String errorDetails = caseResult.getErrorDetails();
                                if (errorDetails != null) {
                                    statement.setString(6, errorDetails);
                                } else {
                                    statement.setNull(6, Types.VARCHAR);
                                }
                                statement.executeUpdate();
                                count++;
                            }
                        }
                        listener.getLogger().printf("Saved %d test cases into database.%n", count);
                    }
                } catch (SQLException x) {
                    throw new IOException(x);
                }
            }

        }

        static abstract class ConnectionSupplier { // TODO AutoCloseable

            private transient Connection connection;

            protected abstract Database database();

            protected void initialize(Connection connection) throws SQLException {}

            synchronized Connection connection() throws SQLException {
                if (connection == null) {
                    Connection _connection = database().getDataSource().getConnection();
                    initialize(_connection);
                    connection = _connection;
                }
                return connection;
            }

        }

        static class LocalConnectionSupplier extends ConnectionSupplier {

            @Override protected Database database() {
                return GlobalDatabaseConfiguration.get().getDatabase();
            }

            @Override protected void initialize(Connection connection) throws SQLException {
                boolean exists = false;
                try (ResultSet rs = connection.getMetaData().getTables(null, null, CASE_RESULTS_TABLE, new String[] {"TABLE"})) {
                    while (rs.next()) {
                        if (rs.getString("TABLE_NAME").equalsIgnoreCase(CASE_RESULTS_TABLE)) {
                            exists = true;
                            break;
                        }
                    }
                }
                if (!exists) {
                    try (Statement statement = connection.createStatement()) {
                        // TODO this and joined tables: skipped, skippedMessage, errorStackTrace, stdout, stderr, duration, nodeId, enclosingBlocks, enclosingBlockNames, etc.
                        statement.execute("CREATE TABLE " + CASE_RESULTS_TABLE + "(job varchar(255), build int, suite varchar(255), className varchar(255), testName varchar(255), errorDetails varchar(255))");
                        // TODO indices
                    }
                }
            }

        }

        /**
         * Ensures a {@link LocalH2Database} configuration can be sent to an agent.
         */
        static class RemoteConnectionSupplier extends ConnectionSupplier implements SerializableOnlyOverRemoting {

            private static final XStream XSTREAM = new XStream();
            private final String databaseXml;

            RemoteConnectionSupplier() {
                databaseXml = XSTREAM.toXML(GlobalDatabaseConfiguration.get().getDatabase());
            }

            @Override protected Database database() {
                return (Database) XSTREAM.fromXML(databaseXml);
            }

        }

    }

    // https://gist.github.com/mikbuch/299568988fa7997cb28c7c84309232b1
    private static void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnsNumber = rsmd.getColumnCount();
        for (int i = 1; i <= columnsNumber; i++) {
            if (i > 1) {
                System.out.print(" | ");
            }
            System.out.print(rsmd.getColumnName(i));
        }
        System.out.println();
        while (rs.next()) {
            for (int i = 1; i <= columnsNumber; i++) {
                if (i > 1) {
                    System.out.print(" | ");
                }
                System.out.print(rs.getString(i));
            }
            System.out.println();
        }
    }

}
