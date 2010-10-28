package no.citrus.maven.junit;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

import org.apache.maven.surefire.Surefire;
import org.apache.maven.surefire.report.ReportEntry;
import org.apache.maven.surefire.report.ReporterException;
import org.apache.maven.surefire.report.ReporterManager;
import org.apache.maven.surefire.report.ReporterManagerFactory;
import org.apache.maven.surefire.suite.SurefireTestSuite;
import org.apache.maven.surefire.testset.SurefireTestSet;
import org.apache.maven.surefire.testset.TestSetFailedException;
import org.apache.maven.surefire.util.SurefireDirectoryScanner;

public abstract class CitrusAbstractDirectoryTestSuite implements SurefireTestSuite {

	protected static ResourceBundle bundle = ResourceBundle.getBundle( Surefire.SUREFIRE_BUNDLE_NAME );

    protected LinkedHashMap testSets;

    private int totalTests;
    
    private final SurefireDirectoryScanner surefireDirectoryScanner;


    protected CitrusAbstractDirectoryTestSuite( File basedir, List includes, List excludes )
    {
        this.surefireDirectoryScanner = new SurefireDirectoryScanner(basedir, includes, excludes);
    }

    public Map locateTestSets( ClassLoader classLoader )
        throws TestSetFailedException
    {
        if ( testSets != null )
        {
            throw new IllegalStateException( "You can't call locateTestSets twice" );
        }
        testSets = new LinkedHashMap();

        Class[] locatedClasses = surefireDirectoryScanner.locateTestClasses( classLoader);

        for ( int i = 0; i < locatedClasses.length; i++ )
        {
            Class testClass = locatedClasses[i];
            SurefireTestSet testSet = createTestSet( testClass, classLoader );

                if ( testSet == null )
                {
                    continue;
                }

                if ( testSets.containsKey( testSet.getName() ) )
                {
                    throw new TestSetFailedException( "Duplicate test set '" + testSet.getName() + "'" );
                }
                testSets.put( testSet.getName(), testSet );

                totalTests++;
        }
        for (int i = 0; i < testSets.size(); i++){
        	System.out.println(testSets.keySet().toArray()[i].toString());
        }
        
        LinkedHashMap temp = new LinkedHashMap();
        for ( int i = testSets.size()-1; i >= 0; i--){
        	Object key = testSets.keySet().toArray()[i];
        	temp.put(key, testSets.get(key));
        	temp.put(key.toString() + "1", testSets.get(key));
        }
        for (int i = 0; i < temp.size(); i++){
        	System.out.println(temp.keySet().toArray()[i].toString());
        }
        testSets = temp;
        
        return Collections.unmodifiableMap( testSets );
    }

    protected abstract SurefireTestSet createTestSet( Class testClass, ClassLoader classLoader )
        throws TestSetFailedException;

    public void execute( ReporterManagerFactory reporterManagerFactory, ClassLoader classLoader )
        throws ReporterException, TestSetFailedException
    {
        if ( testSets == null )
        {
            throw new IllegalStateException( "You must call locateTestSets before calling execute" );
        }
        for ( Iterator i = testSets.values().iterator(); i.hasNext(); )
        {
            SurefireTestSet testSet = (SurefireTestSet) i.next();

            executeTestSet( testSet, reporterManagerFactory, classLoader );
        }
    }

    private void executeTestSet( SurefireTestSet testSet, ReporterManagerFactory reporterManagerFactory,
                                 ClassLoader classLoader )
        throws ReporterException, TestSetFailedException
    {

        ReporterManager reporterManager = reporterManagerFactory.createReporterManager();

        String rawString = bundle.getString( "testSetStarting" );

        ReportEntry report = new ReportEntry( this.getClass().getName(), testSet.getName(), rawString );

        reporterManager.testSetStarting( report );

        testSet.execute( reporterManager, classLoader );

        rawString = bundle.getString( "testSetCompletedNormally" );

        report = new ReportEntry( this.getClass().getName(), testSet.getName(), rawString );

        reporterManager.testSetCompleted( report );

        reporterManager.reset();
    }

    public void execute( String testSetName, ReporterManagerFactory reporterManagerFactory, ClassLoader classLoader )
        throws ReporterException, TestSetFailedException
    {
        if ( testSets == null )
        {
            throw new IllegalStateException( "You must call locateTestSets before calling execute" );
        }
        SurefireTestSet testSet = (SurefireTestSet) testSets.get( testSetName );

        if ( testSet == null )
        {
            throw new TestSetFailedException( "Unable to find test set '" + testSetName + "' in suite" );
        }

        executeTestSet( testSet, reporterManagerFactory, classLoader );
    }

    public int getNumTests()
    {
        if ( testSets == null )
        {
            throw new IllegalStateException( "You must call locateTestSets before calling getNumTests" );
        }
        return totalTests;
    }

}
