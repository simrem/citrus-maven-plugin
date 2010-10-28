package no.citrus.maven.junit;

import java.io.File;
import java.util.ArrayList;


import org.apache.maven.surefire.testset.SurefireTestSet;
import org.apache.maven.surefire.testset.TestSetFailedException;



public class CitrusJUnit4DirectoryTestSuite extends
		CitrusAbstractDirectoryTestSuite {

	public CitrusJUnit4DirectoryTestSuite(File basedir, ArrayList includes, ArrayList excludes) {
		super(basedir, includes, excludes);
	}
	
    protected SurefireTestSet createTestSet( Class testClass, ClassLoader classLoader )
    throws TestSetFailedException
{
    return new JUnit4TestSet( testClass );
}

}
