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
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

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

    @Ignore("TODO")
    @Test public void smokes() throws Exception {
        DumbSlave remote = r.createOnlineSlave(Label.get("remote"));
        //((Channel) remote.getChannel()).addListener(new LoggingChannelListener(Logger.getLogger(TestResultStorageTest.class.getName()), Level.INFO));
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('remote') {\n" +
                "  writeFile file: 'x.xml', text: '''<testsuite name='sweet'><testcase classname='Klazz' name='test1'><error message='failure'/></testcase><testcase classname='Klazz' name='test2'/></testsuite>'''\n" +
                "  junit 'x.xml'\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).get();
        try (Connection connection = GlobalDatabaseConfiguration.get().getDatabase().getDataSource().getConnection();
                ResultSet result = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
            printResultSet(result);
        }
        try (Connection connection = GlobalDatabaseConfiguration.get().getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + Impl.CASE_RESULTS_TABLE);
                ResultSet result = statement.executeQuery()) {
            printResultSet(result);
        }
        r.assertBuildStatus(Result.UNSTABLE, b);
        TestResultAction a = b.getAction(TestResultAction.class);
        assertNotNull(a);
        assertEquals(1, a.getFailCount());
        List<CaseResult> failedTests = a.getFailedTests();
        assertEquals(1, failedTests.size());
        assertEquals("Klazz", failedTests.get(0).getClassName());
        assertEquals("test1", failedTests.get(0).getName());
        assertEquals("failure", failedTests.get(0).getErrorDetails());
        // TODO more detailed Java queries incl. PackageResult / ClassResult
        // TODO verify that there is no junitResult.xml on disk
        // TODO verify that build.xml#//hudson.tasks.junit.TestResultAction is empty except for healthScaleFactor and testData
        // TODO verify table structure
    }

    @TestExtension public static class Impl implements TestResultStorage {

        static final String CASE_RESULTS_TABLE = "caseResults";

        private final ConnectionSupplier connectionSupplier = new LocalConnectionSupplier();

        @Override public RemotePublisher createRemotePublisher(Run<?, ?> build, TaskListener listener) throws IOException {
            try {
                connectionSupplier.connection(); // make sure we start a local server and create table first
            } catch (SQLException x) {
                throw new IOException(x);
            }
            return new RemotePublisherImpl(build.getParent().getFullName(), build.getNumber(), listener);
        }

        @Override public TestResultImpl load(String job, int build) {
            return new TestResultImpl() {
                @Override public int getFailCount() {
                    try {
                        Connection connection = connectionSupplier.connection;
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
            private final TaskListener listener;
            // TODO keep the same supplier and thus Connection open across builds, so long as the database config remains unchanged
            private final ConnectionSupplier connectionSupplier;

            RemotePublisherImpl(String job, int build, TaskListener listener) {
                this.job = job;
                this.build = build;
                this.listener = listener;
                connectionSupplier = new RemoteConnectionSupplier();
            }

            @Override public void publish(TestResult result) throws IOException {
                try {
                    Connection connection = connectionSupplier.connection();
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + CASE_RESULTS_TABLE + " (job, build, suite, className, testName, errorDetails) VALUES (?, ?, ?, ?, ?, ?)")) {
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
                            }
                        }
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
