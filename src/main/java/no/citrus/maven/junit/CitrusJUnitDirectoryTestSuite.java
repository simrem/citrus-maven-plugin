package no.citrus.maven.junit;

import junit.framework.Test;


import org.apache.maven.surefire.junit.JUnitTestSet;
import org.apache.maven.surefire.suite.AbstractDirectoryTestSuite;
import org.apache.maven.surefire.testset.PojoTestSet;
import org.apache.maven.surefire.testset.SurefireTestSet;
import org.apache.maven.surefire.testset.TestSetFailedException;

import java.io.File;
import java.util.ArrayList;

public class CitrusJUnitDirectoryTestSuite extends CitrusAbstractDirectoryTestSuite {

	
	public CitrusJUnitDirectoryTestSuite( File basedir, ArrayList includes, ArrayList excludes )
    {
        super( basedir, includes, excludes );
    }

    protected SurefireTestSet createTestSet( Class testClass, ClassLoader classLoader )
        throws TestSetFailedException
    {
        Class junitClass = null;
        try
        {
            junitClass = classLoader.loadClass( Test.class.getName() );
        }
        catch ( NoClassDefFoundError e)
        {
            // ignore this
        }
        catch ( ClassNotFoundException e )
        {
            // ignore this
        }

        SurefireTestSet testSet;
        if ( junitClass != null && junitClass.isAssignableFrom( testClass ) )
        {
            testSet = new JUnitTestSet( testClass );
        }
        else if (classHasPublicNoArgConstructor( testClass ))
        {
            testSet = new PojoTestSet( testClass );
        }
        else
        {
            testSet = null;
        }
        return testSet;
    }

    private boolean classHasPublicNoArgConstructor( Class testClass )
    {
        try
        {
            testClass.getConstructor( new Class[0] );
            return true;
        }
        catch ( Exception e )
        {
            return false;
        }
    }
	

}
