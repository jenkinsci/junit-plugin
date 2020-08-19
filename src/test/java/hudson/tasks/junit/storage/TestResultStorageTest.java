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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultSummary;
import hudson.tasks.junit.TrendTestResultSummary;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.database.Database;
import org.jenkinsci.plugins.database.GlobalDatabaseConfiguration;
import org.jenkinsci.plugins.database.h2.LocalH2Database;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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
                "  writeFile file: 'x.xml', text: '''<testsuite name='sweet' time='200.0'>" +
                    "<testcase classname='Klazz' name='test1' time='198.0'><error message='failure'/></testcase>" +
                    "<testcase classname='Klazz' name='test2' time='2.0'/>" +
                    "<testcase classname='other.Klazz' name='test3'><skipped message='Not actually run.'/></testcase>" +
                    "</testsuite>'''\n" +
                "  def s = junit 'x.xml'\n" +
                "  echo(/summary: fail=$s.failCount skip=$s.skipCount pass=$s.passCount total=$s.totalCount/)\n" +
                "  writeFile file: 'x.xml', text: '''<testsuite name='supersweet'>" +
                    "<testcase classname='another.Klazz' name='test1'><error message='another failure'/></testcase>" +
                    "</testsuite>'''\n" +
                "  s = junit 'x.xml'\n" +
                "  echo(/next summary: fail=$s.failCount skip=$s.skipCount pass=$s.passCount total=$s.totalCount/)\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).get();
        try (Connection connection = requireNonNull(GlobalDatabaseConfiguration.get().getDatabase()).getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + Impl.CASE_RESULTS_TABLE);
             ResultSet result = statement.executeQuery()) {
            printResultSet(result);
        }
        // TODO verify table structure
        r.assertBuildStatus(Result.UNSTABLE, b);
        r.assertLogContains("summary: fail=1 skip=1 pass=1 total=3", b);
        r.assertLogContains("next summary: fail=1 skip=0 pass=0 total=1", b);
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
            assertEquals(2, a.getFailCount());
            assertEquals(1, a.getSkipCount());
            assertEquals(4, a.getTotalCount());
            assertEquals(2, a.getResult().getFailCount());
            assertEquals(1, a.getResult().getSkipCount());
            assertEquals(4, a.getResult().getTotalCount());
            assertEquals(1, a.getResult().getPassCount());
            List<CaseResult> failedTests = a.getFailedTests();
            assertEquals(2, failedTests.size());
            final CaseResult klazzTest1 = failedTests.get(0);
            assertEquals("Klazz", klazzTest1.getClassName());
            assertEquals("test1", klazzTest1.getName());
            assertEquals("failure", klazzTest1.getErrorDetails());
            assertThat(klazzTest1.getDuration(), is(198.0f));
            assertEquals("another.Klazz", failedTests.get(1).getClassName());
            assertEquals("test1", failedTests.get(1).getName());
            assertEquals("another failure", failedTests.get(1).getErrorDetails());

            List<CaseResult> skippedTests = a.getSkippedTests();
            assertEquals(1, skippedTests.size());
            assertEquals("other.Klazz", skippedTests.get(0).getClassName());
            assertEquals("test3", skippedTests.get(0).getName());
            assertEquals("Not actually run.", skippedTests.get(0).getSkippedMessage());

            List<CaseResult> passedTests = a.getPassedTests();
            assertEquals(1, passedTests.size());
            assertEquals("Klazz", passedTests.get(0).getClassName());
            assertEquals("test2", passedTests.get(0).getName());
            
            PackageResult another = a.getResult().byPackage("another");
            List<CaseResult> packageFailedTests = another.getFailedTests();
            assertEquals(1, packageFailedTests.size());
            assertEquals("another.Klazz", packageFailedTests.get(0).getClassName());

            PackageResult other = a.getResult().byPackage("other");
            List<CaseResult> packageSkippedTests = other.getSkippedTests();
            assertEquals(1, packageSkippedTests.size());
            assertEquals("other.Klazz", packageSkippedTests.get(0).getClassName());
            assertEquals("Not actually run.", packageSkippedTests.get(0).getSkippedMessage());

            PackageResult root = a.getResult().byPackage("(root)");
            List<CaseResult> rootPassedTests = root.getPassedTests();
            assertEquals(1, rootPassedTests.size());
            assertEquals("Klazz", rootPassedTests.get(0).getClassName());

            List<TrendTestResultSummary> trendTestResultSummary = requireNonNull(a.getResult().getPluggableStorage()).getTrendTestResultSummary();
            assertThat(trendTestResultSummary, hasSize(1));
            TestResultSummary testResultSummary = trendTestResultSummary.get(0).getTestResultSummary();
            assertThat(testResultSummary.getFailCount(), equalTo(2));
            assertThat(testResultSummary.getPassCount(), equalTo(1));
            assertThat(testResultSummary.getSkipCount(), equalTo(1));
            assertThat(testResultSummary.getTotalCount(), equalTo(4));
            
            // TODO test result summary i.e. failure content
            // TODO getFailedSinceRun, TestResult#getChildren, TestObject#getTestResultAction
            // TODO more detailed Java queries incl. ClassResult
            // TODO CaseResult#getRun: In getOwner(), suiteResult.getParent() is null
            // TODO test healthScaleFactor, descriptions
            // TODO historyAvailable(History.java:72)
            // TODO getByPackage UI isn't working in test report
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

        @FunctionalInterface
        private interface Querier<T> {
            T run(Connection connection) throws SQLException;
        }
        @Override public TestResultImpl load(String job, int build) {
            return new TestResultImpl() {
                private <T> T query(Querier<T> querier, T dflt) {
                    if (!queriesPermitted) {
                        throw new IllegalStateException("Should not have been running any queries yet");
                    }
                    try {
                        Connection connection = connectionSupplier.connection();
                        return querier.run(connection);
                    } catch (SQLException x) {
                        throw new RuntimeException(x);
                    }
                }
                private int getCaseCount(String and) {
                    return query(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + Impl.CASE_RESULTS_TABLE + " WHERE job = ? AND build = ?" + and)) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            try (ResultSet result = statement.executeQuery()) {
                                result.next();
                                int anInt = result.getInt(1);
                                return anInt;
                            }
                        }
                    }, 0);
                }

                private List<CaseResult> retrieveCaseResult(String whereCondition) {
                    return query(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT suite, package, testname, classname, errordetails, skipped, duration FROM " + Impl.CASE_RESULTS_TABLE + " WHERE job = ? AND build = ? AND " + whereCondition)) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            try (ResultSet result = statement.executeQuery()) {

                                List<CaseResult> results = new ArrayList<>();
                                while (result.next()) {
                                    String testName = result.getString("testname");
                                    String packageName = result.getString("package");
                                    String errorDetails = result.getString("errordetails");
                                    String suite = result.getString("suite");
                                    String className = result.getString("classname");
                                    String skipped = result.getString("skipped");
                                    float duration = result.getFloat("duration");

                                    SuiteResult suiteResult = new SuiteResult(suite, null, null, null);
                                    suiteResult.setParent(new TestResult(this));
                                    CaseResult caseResult = new CaseResult(suiteResult, className, testName, errorDetails, skipped, duration);
                                    caseResult.setClass(new ClassResult(new PackageResult(new TestResult(this), packageName), className));
                                    results.add(caseResult);
                                }
                                return results;
                            }
                        }
                    }, emptyList());
                }

                @Override
                public List<PackageResult> getAllPackageResults() {
                    return query(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT DISTINCT package FROM " + Impl.CASE_RESULTS_TABLE + " WHERE job = ? AND build = ?")) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            try (ResultSet result = statement.executeQuery()) {

                                List<PackageResult> results = new ArrayList<>();
                                while (result.next()) {
                                    String packageName = result.getString("package");

                                    results.add(new PackageResult(new TestResult(this), packageName));
                                }
                                return results;
                            }
                        }
                    }, emptyList());
                }

                @Override
                public List<TrendTestResultSummary> getTrendTestResultSummary() {
                    return query(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT build, sum(case when errorDetails is not null then 1 else 0 end) as failCount, sum(case when skipped is not null then 1 else 0 end) as skipCount, sum(case when errorDetails is null and skipped is null then 1 else 0 end) as passCount FROM " +  Impl.CASE_RESULTS_TABLE +  " WHERE job = ? group by build;")) {
                            statement.setString(1, job);
                            try (ResultSet result = statement.executeQuery()) {

                                List<TrendTestResultSummary> trendTestResultSummaries = new ArrayList<>();
                                while (result.next()) {
                                    int buildNumber = result.getInt("build");
                                    int passed = result.getInt("passCount");
                                    int failed = result.getInt("failCount");
                                    int skipped = result.getInt("skipCount");
                                    int total = passed + failed + skipped;

                                    trendTestResultSummaries.add(new TrendTestResultSummary(buildNumber, new TestResultSummary(failed, skipped, passed, total)));
                                }
                                return trendTestResultSummaries;
                            }
                        }
                    }, emptyList());
                }

                @Override
                public PackageResult getPackageResult(String packageName) {
                    return new PackageResult(new TestResult(this), packageName);
                }

                @Override
                public ClassResult getClassResult(String name) {
                    return query(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT package, classname FROM " + Impl.CASE_RESULTS_TABLE + " WHERE job = ? AND build = ? AND classname = ?")) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            statement.setString(3, name);
                            try (ResultSet result = statement.executeQuery()) {

                                if (result.next()) {
                                    String packageName = result.getString("package");
                                    String className = result.getString("classname");

                                    PackageResult packageResult = new PackageResult(new TestResult(this), packageName);
                                    return new ClassResult(packageResult, className);
                                }
                                return null;
                            }
                        }
                    }, null);

                }

                @Override
                public Run<?, ?> getFailedSinceRun(CaseResult caseResult) {
                    return query(connection -> {
                        int lastPassingBuildNumber;
                        Job<?, ?> theJob = Objects.requireNonNull(Jenkins.get().getItemByFullName(job, Job.class));
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT build " +
                                        "FROM " + Impl.CASE_RESULTS_TABLE + " " +
                                        "WHERE job = ? " +
                                        "AND build < ? " +
                                        "AND suite = ? " +
                                        "AND package = ? " +
                                        "AND classname = ? " +
                                        "AND testname = ? " +
                                        "AND errordetails IS NULL " +
                                        "ORDER BY BUILD DESC " +
                                        "LIMIT 1"
                        )) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            statement.setString(3, caseResult.getSuiteResult().getName());
                            statement.setString(4, caseResult.getPackageName());
                            statement.setString(5, caseResult.getClassName());
                            statement.setString(6, caseResult.getName());
                            try (ResultSet result = statement.executeQuery()) {
                                boolean hasPassed = result.next();
                                if (!hasPassed) {
                                    return theJob.getBuildByNumber(1);
                                }
                                
                                lastPassingBuildNumber = result.getInt("build");
                            }
                        }
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT build " +
                                        "FROM " + Impl.CASE_RESULTS_TABLE + " " +
                                        "WHERE job = ? " +
                                        "AND build > ? " +
                                        "AND suite = ? " +
                                        "AND package = ? " +
                                        "AND classname = ? " +
                                        "AND testname = ? " +
                                        "AND errordetails is NOT NULL " +
                                        "ORDER BY BUILD ASC " +
                                        "LIMIT 1"
                        )
                        ) {
                            statement.setString(1, job);
                            statement.setInt(2, lastPassingBuildNumber);
                            statement.setString(3, caseResult.getSuiteResult().getName());
                            statement.setString(4, caseResult.getPackageName());
                            statement.setString(5, caseResult.getClassName());
                            statement.setString(6, caseResult.getName());

                            try (ResultSet result = statement.executeQuery()) {
                                result.next();

                                int firstFailingBuildAfterPassing = result.getInt("build");
                                return theJob.getBuildByNumber(firstFailingBuildAfterPassing);
                            }
                        }
                    }, null);

                }

                @Override
                public List<CaseResult> getFailedTestsByPackage(String packageName) {
                    return getByPackage(packageName, "AND errorDetails IS NOT NULL");
                }

                private List<CaseResult> getByPackage(String packageName, String filter) {
                    return query(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT suite, testname, classname, errordetails, duration, skipped FROM " + Impl.CASE_RESULTS_TABLE + " WHERE job = ? AND build = ? AND package = ? " + filter)) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            statement.setString(3, packageName);
                            try (ResultSet result = statement.executeQuery()) {

                                List<CaseResult> results = new ArrayList<>();
                                while (result.next()) {
                                    String testName = result.getString("testname");
                                    String errorDetails = result.getString("errordetails");
                                    String suite = result.getString("suite");
                                    String className = result.getString("classname");
                                    String skipped = result.getString("skipped");
                                    float duration = result.getFloat("duration");

                                    SuiteResult suiteResult = new SuiteResult(suite, null, null, null);
                                    suiteResult.setParent(new TestResult(this));
                                    results.add(new CaseResult(suiteResult, className, testName, errorDetails, skipped, duration));
                                }
                                return results;
                            }
                        }
                    }, emptyList());
                }


                private List<CaseResult> getCaseResults(String column) {
                    return retrieveCaseResult(column + " IS NOT NULL");
                }
                
                @Override
                public CaseResult getCaseResult(String testName) {
                    return query(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT suite, testname, package, classname, errordetails, skipped, duration FROM " + Impl.CASE_RESULTS_TABLE + " WHERE job = ? AND build = ? AND testname = ?")) {
                            statement.setString(1, job);
                            statement.setInt(2, build);
                            statement.setString(3, testName);
                            try (ResultSet result = statement.executeQuery()) {

                                CaseResult caseResult = null;
                                if (result.next()) {
                                    String resultTestName = result.getString("testname");
                                    String errorDetails = result.getString("errordetails");
                                    String packageName = result.getString("package");
                                    String suite = result.getString("suite");
                                    String className = result.getString("classname");
                                    String skipped = result.getString("skipped");
                                    float duration = result.getFloat("duration");

                                    SuiteResult suiteResult = new SuiteResult(suite, null, null, null);
                                    suiteResult.setParent(new TestResult(this));
                                    caseResult = new CaseResult(suiteResult, className, resultTestName, errorDetails, skipped, duration);
                                    caseResult.setClass(new ClassResult(new PackageResult(new TestResult(this), packageName), className));
                                }
                                return caseResult;
                            }
                        }
                    }, null);


                }
                
                @Override public int getFailCount() {
                    int caseCount = getCaseCount(" AND errorDetails IS NOT NULL");
                    return caseCount;
                }
                @Override public int getSkipCount() {
                    int caseCount = getCaseCount(" AND skipped IS NOT NULL");
                    return caseCount;
                }
                @Override public int getPassCount() {
                    int caseCount = getCaseCount(" AND errorDetails IS NULL AND skipped IS NULL");
                    return caseCount;
                }
                @Override public int getTotalCount() {
                    int caseCount = getCaseCount("");
                    return caseCount;
                }

                @Override
                public List<CaseResult> getFailedTests() {
                    List<CaseResult> errordetails = getCaseResults("errordetails");
                    return errordetails;
                }

                @Override
                public List<CaseResult> getSkippedTests() {
                    List<CaseResult> errordetails = getCaseResults("skipped");
                    return errordetails;
                }

                @Override
                public List<CaseResult> getSkippedTestsByPackage(String packageName) {
                    return getByPackage(packageName, "AND skipped IS NOT NULL");
                }

                @Override
                public List<CaseResult> getPassedTests() {
                    List<CaseResult> errordetails = retrieveCaseResult("errordetails IS NULL AND skipped IS NULL");
                    return errordetails;
                }

                @Override
                public List<CaseResult> getPassedTestsByPackage(String packageName) {
                    return getByPackage(packageName, "AND errordetails IS NULL AND skipped IS NULL");
                }

                @NonNull
                @Override 
                public TestResult getResultByNodes(@NonNull List<String> nodeIds) {
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
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + CASE_RESULTS_TABLE + " (job, build, suite, package, className, testName, errorDetails, skipped, duration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        int count = 0;
                        for (SuiteResult suiteResult : result.getSuites()) {
                            for (CaseResult caseResult : suiteResult.getCases()) {
                                statement.setString(1, job);
                                statement.setInt(2, build);
                                statement.setString(3, suiteResult.getName());
                                statement.setString(4, caseResult.getPackageName());
                                statement.setString(5, caseResult.getClassName());
                                statement.setString(6, caseResult.getName());
                                String errorDetails = caseResult.getErrorDetails();
                                if (errorDetails != null) {
                                    statement.setString(7, errorDetails);
                                } else {
                                    statement.setNull(7, Types.VARCHAR);
                                }
                                if (caseResult.isSkipped()) {
                                    statement.setString(8, Util.fixNull(caseResult.getSkippedMessage()));
                                } else {
                                    statement.setNull(8, Types.VARCHAR);
                                }
                                statement.setFloat(9, caseResult.getDuration());
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
                        // TODO this and joined tables: errorStackTrace, stdout, stderr, duration, nodeId, enclosingBlocks, enclosingBlockNames, etc.
                        statement.execute("CREATE TABLE " + CASE_RESULTS_TABLE + "(job varchar(255), build int, suite varchar(255), package varchar(255), className varchar(255), testName varchar(255), errorDetails varchar(255), skipped varchar(255), duration numeric)");
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
                System.out.print("\t|\t");
            }
            System.out.print(rsmd.getColumnName(i));
        }
        System.out.println();
        while (rs.next()) {
            for (int i = 1; i <= columnsNumber; i++) {
                if (i > 1) {
                    System.out.print("\t|\t");
                }
                System.out.print(rs.getString(i));
            }
            System.out.println();
        }
    }

}
