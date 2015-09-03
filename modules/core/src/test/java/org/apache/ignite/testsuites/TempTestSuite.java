package org.apache.ignite.testsuites;

import java.util.Set;
import junit.framework.TestSuite;
import org.apache.ignite.internal.processors.cache.IgniteDaemonNodeMarshallerCacheTest;

/**
 * Created by GridAdmin1234 on 9/3/2015.
 */
public class TempTestSuite extends TestSuite {
    /**
     * @return Test suite.
     * @throws Exception Thrown in case of the failure.
     */
    public static TestSuite suite() throws Exception {
        return suite(null);
    }

    /**
     * @param ignoredTests Tests don't include in the execution.
     * @return Test suite.
     * @throws Exception Thrown in case of the failure.
     */
    public static TestSuite suite(Set<Class> ignoredTests) throws Exception {
        TestSuite suite = new TestSuite("Ignite Basic Test Suite");

        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);
        suite.addTestSuite(IgniteDaemonNodeMarshallerCacheTest.class);

        return suite;
    }
}
