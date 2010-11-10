package no.citrus.maven.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.SurefireHelper;
import org.apache.maven.plugin.surefire.SurefirePlugin;
import org.apache.maven.surefire.booter.ForkConfiguration;
import org.apache.maven.surefire.booter.SurefireBooter;
import org.apache.maven.surefire.booter.SurefireBooterForkException;
import org.apache.maven.surefire.booter.SurefireExecutionException;
import org.apache.maven.surefire.report.BriefConsoleReporter;
import org.apache.maven.surefire.report.BriefFileReporter;
import org.apache.maven.surefire.report.ConsoleReporter;
import org.apache.maven.surefire.report.DetailedConsoleReporter;
import org.apache.maven.surefire.report.FileReporter;
import org.apache.maven.surefire.report.ForkingConsoleReporter;
import org.apache.maven.surefire.report.XMLReporter;
import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.util.StringUtils;


/**
 * Goal which touches a timestamp file.
 * @extendsPlugin maven-surefire-plugin
 * @goal test
 * @phase test
 */
public class CitrusSurefirePlugin extends SurefirePlugin {

    public CitrusSurefirePlugin(){
    	super();
    }
    
    
    
	private static final String BRIEF_REPORT_FORMAT = "brief";

	private static final String PLAIN_REPORT_FORMAT = "plain";

	
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		//super.execute();
		if ( verifyParameters() )
        {
            SurefireBooter surefireBooter = constructSurefireBooter();

            getLog().info(
                StringUtils.capitalizeFirstLetter( getPluginName() ) + " report directory: " + getReportsDirectory() );

            int result;
            try
            {
                result = surefireBooter.run();
            }
            catch ( SurefireBooterForkException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
            catch ( SurefireExecutionException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }

            if ( getOriginalSystemProperties() != null && !surefireBooter.isForking() )
            {
                // restore system properties, only makes sense when not forking..
                System.setProperties( getOriginalSystemProperties() );
            }
            Reporter.run();
            SurefireHelper.reportExecution( this, result, getLog() );
            
        }
		
	}



	@Override
	protected SurefireBooter constructSurefireBooter()
	throws MojoExecutionException, MojoFailureException {

		SurefireBooter surefireBooter = new SurefireBooter();

		Artifact surefireArtifact = (Artifact) getPluginArtifactMap().get( "org.apache.maven.surefire:surefire-booter" );
		if ( surefireArtifact == null )
		{
			throw new MojoExecutionException( "Unable to locate surefire-booter in the list of plugin artifacts" );
		}

		surefireArtifact.isSnapshot(); // MNG-2961: before Maven 2.0.8, fixes getBaseVersion to be -SNAPSHOT if needed

		Artifact junitArtifact;
		Artifact testNgArtifact;
		try
		{
			addArtifact( surefireBooter, surefireArtifact );

			junitArtifact = (Artifact) getProjectArtifactMap().get( getJunitArtifactName() );
			// SUREFIRE-378, junit can have an alternate artifact name
			if ( junitArtifact == null && "junit:junit".equals( getJunitArtifactName() ) )
			{
				junitArtifact = (Artifact) getProjectArtifactMap().get( "junit:junit-dep" );
			}

			// TODO: this is pretty manual, but I'd rather not require the plugin > dependencies section right now
			testNgArtifact = (Artifact) getProjectArtifactMap().get( getTestNGArtifactName() );

			if ( testNgArtifact != null )
			{
				VersionRange range = VersionRange.createFromVersionSpec( "[4.7,)" );
				if ( !range.containsVersion( new DefaultArtifactVersion( testNgArtifact.getVersion() ) ) )
				{
					throw new MojoFailureException(
							"TestNG support requires version 4.7 or above. You have declared version "
							+ testNgArtifact.getVersion() );
				}

				convertTestNGParameters();

				if ( this.getTestClassesDirectory() != null )
				{
					getProperties().setProperty( "testng.test.classpath", getTestClassesDirectory().getAbsolutePath() );
				}

				addArtifact( surefireBooter, testNgArtifact );

				// The plugin uses a JDK based profile to select the right testng. We might be explicity using a
				// different one since its based on the source level, not the JVM. Prune using the filter.
				addProvider( surefireBooter, "surefire-testng", surefireArtifact.getBaseVersion(), testNgArtifact );
			}
			else if ( junitArtifact != null && isAnyJunit4( junitArtifact ) )
			{
				if ( isAnyConcurrencySelected() && isJunit47Compatible( junitArtifact ) )
				{
					convertJunitCoreParameters();
					addProvider( surefireBooter, "surefire-junit47", surefireArtifact.getBaseVersion(), null );
				}
				else
				{
					addProvider( surefireBooter, "surefire-junit4", surefireArtifact.getBaseVersion(), null );
				}
			}
			else
			{
				// add the JUnit provider as default - it doesn't require JUnit to be present,
				// since it supports POJO tests.
				addProvider( surefireBooter, "surefire-junit", surefireArtifact.getBaseVersion(), null );
			}
		}
		catch ( ArtifactNotFoundException e )
		{
			throw new MojoExecutionException(
					"Unable to locate required surefire provider dependency: " + e.getMessage(), e );
		}
		catch ( InvalidVersionSpecificationException e )
		{
			throw new MojoExecutionException( "Error determining the TestNG version requested: " + e.getMessage(), e );
		}
		catch ( ArtifactResolutionException e )
		{
			throw new MojoExecutionException( "Error to resolving surefire provider dependency: " + e.getMessage(), e );
		}

		if ( getSuiteXmlFiles() != null && getSuiteXmlFiles().length > 0 && getTest() == null )
		{
			if ( testNgArtifact == null )
			{
				throw new MojoExecutionException( "suiteXmlFiles is configured, but there is no TestNG dependency" );
			}

			// TODO: properties should be passed in here too
			surefireBooter.addTestSuite( "org.apache.maven.surefire.testng.TestNGXmlTestSuite",
					new Object[]{ getSuiteXmlFiles(), getTestSourceDirectory().getAbsolutePath(),
					testNgArtifact.getVersion(), testNgArtifact.getClassifier(),
					getProperties(), getReportsDirectory() } );
		}
		else
		{
			List includes;
			List excludes;

			if ( getTest() != null )
			{
				// Check to see if we are running a single test. The raw parameter will
				// come through if it has not been set.

				// FooTest -> **/FooTest.java

				includes = new ArrayList();

				excludes = new ArrayList();

				if ( getFailIfNoTests() == null )
				{
					setFailIfNoTests( Boolean.TRUE );
				}

				String[] testRegexes = StringUtils.split( getTest(), "," );

				for ( int i = 0; i < testRegexes.length; i++ )
				{
					String testRegex = testRegexes[i];
					if ( testRegex.endsWith( ".java" ) )
					{
						testRegex = testRegex.substring( 0, testRegex.length() - 5 );
					}
					// Allow paths delimited by '.' or '/'
					testRegex = testRegex.replace( '.', '/' );
					includes.add( "**/" + testRegex + ".java" );
				}
			}
			else
			{
				includes = this.getIncludes();

				excludes = this.getExcludes();

				// defaults here, qdox doesn't like the end javadoc value
				// Have to wrap in an ArrayList as surefire expects an ArrayList instead of a List for some reason
				if ( includes == null || includes.size() == 0 )
				{
					includes = new ArrayList( Arrays.asList( getDefaultIncludes() ) );
				}
				if ( excludes == null || excludes.size() == 0 )
				{
					excludes = new ArrayList( Arrays.asList( new String[]{"**/*$*"} ) );
				}
			}

			if ( testNgArtifact != null )
			{
				surefireBooter.addTestSuite( "org.apache.maven.surefire.testng.TestNGDirectoryTestSuite",
						new Object[]{ getTestClassesDirectory(), includes, excludes,
						getTestSourceDirectory().getAbsolutePath(), testNgArtifact.getVersion(),
						testNgArtifact.getClassifier(), getProperties(),
						getReportsDirectory() } );
				System.out.println("TestNG");
			}
			else
			{
				String junitDirectoryTestSuite;
				if ( isAnyConcurrencySelected() && isJunit47Compatible( junitArtifact ) )
				{
					junitDirectoryTestSuite = "org.apache.maven.surefire.junitcore.JUnitCoreDirectoryTestSuite";
					getLog().info( "Concurrency config is " + getProperties().toString() );
					surefireBooter.addTestSuite( junitDirectoryTestSuite,
							new Object[]{ getTestClassesDirectory(), includes, excludes,
							getProperties() } );
					System.out.println("JunitCore");
				}
				else
				{
					if ( isAnyJunit4( junitArtifact ) )
					{
						junitDirectoryTestSuite = "no.citrus.maven.junit.CitrusJUnit4DirectoryTestSuite";
						System.out.println("Junit4");
					}
					else
					{
						// fall back to JUnit, which also contains POJO support. Also it can run
						// classes compiled against JUnit since it has a dependency on JUnit itself.
						junitDirectoryTestSuite = "no.citrus.maven.junit.CitrusJUnitDirectoryTestSuite";
						System.out.println("JUnit3");
					}
					surefireBooter.addTestSuite( junitDirectoryTestSuite,
							new Object[]{ getTestClassesDirectory(), includes, excludes} );
				}
			}
		}

		List classpathElements = null;
		try
		{
			classpathElements = generateTestClasspath();
		}
		catch ( DependencyResolutionRequiredException e )
		{
			throw new MojoExecutionException( "Unable to generate test classpath: " + e, e );
		}

		getLog().debug( "Test Classpath :" );

		for ( Iterator i = classpathElements.iterator(); i.hasNext(); )
		{
			String classpathElement = (String) i.next();

			getLog().debug( "  " + classpathElement );

			surefireBooter.addClassPathUrl( classpathElement );
		}

		Toolchain tc = getToolchain();

		if ( tc != null )
		{
			getLog().info( "Toolchain in " + getPluginName() + "-plugin: " + tc );
			if ( isForkModeNever() )
			{
				setForkMode( ForkConfiguration.FORK_ONCE );
			}
			if ( getJvm() != null )
			{
				getLog().warn( "Toolchains are ignored, 'executable' parameter is set to " + getJvm() );
			}
			else
			{
				setJvm( tc.findTool( "java" ) ); //NOI18N
			}
		}

		// ----------------------------------------------------------------------
		// Forkingmvn no.citrus:citrus-maven-plugin:1.0-SNAPSHOT:
		// ----------------------------------------------------------------------

		ForkConfiguration fork = new ForkConfiguration();

		fork.setForkMode( getForkMode() );

		processSystemProperties( !fork.isForking() );

		if ( getLog().isDebugEnabled() )System.out.println("construct surefirebooter");
		{
			showMap( getInternalSystemProperties(), "system property" );
		}

		if ( fork.isForking() )
		{
			setUseSystemClassLoader( getUseSystemClassLoader() == null ? Boolean.TRUE : getUseSystemClassLoader() );
			fork.setUseSystemClassLoader( getUseSystemClassLoader().booleanValue() );
			fork.setUseManifestOnlyJar( isUseManifestOnlyJar() );

			fork.setSystemProperties( getInternalSystemProperties() );

			if ( "true".equals( getDebugForkedProcess() ) )
			{
				setDebugForkedProcess(
				"-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005" );
			}

			fork.setDebugLine( getDebugForkedProcess() );

			if ( getJvm() == null || "".equals( getJvm() ) )
			{
				// use the same JVM as the one used to run Maven (the "java.home" one)
				setJvm( System.getProperty( "java.home" ) + File.separator + "bin" + File.separator + "java" );
				getLog().debug( "Using JVM: " + getJvm() );
			}

			fork.setJvmExecutable( getJvm() );

			if ( getWorkingDirectory() != null )
			{
				fork.setWorkingDirectory( getWorkingDirectory() );
			}
			else
			{
				fork.setWorkingDirectory( getBasedir() );
			}

			fork.setArgLine( getArgLine() );

			fork.setEnvironmentVariables( getEnvironmentVariables() );

			if ( getLog().isDebugEnabled() )
			{
				showMap( getEnvironmentVariables(), "environment variable" );

				fork.setDebug( true );
			}

			if ( getArgLine() != null )
			{
				List args = Arrays.asList( getArgLine().split( " " ) );
				if ( args.contains( "-da" ) || args.contains( "-disableassertions" ) )
				{
					setEnableAssertions( false );
				}
			}
		}

		surefireBooter.setFailIfNoTests( getFailIfNoTests() == null ? false : getFailIfNoTests().booleanValue() );

		surefireBooter.setForkedProcessTimeoutInSeconds( getForkedProcessTimeoutInSeconds() );

		surefireBooter.setRedirectTestOutputToFile( isRedirectTestOutputToFile() );

		surefireBooter.setForkConfiguration( fork );

		surefireBooter.setChildDelegation( isChildDelegation() );

		surefireBooter.setEnableAssertions( isEnableAssertions() );

		surefireBooter.setReportsDirectory( getReportsDirectory() );

		addReporters( surefireBooter, fork.isForking() );
        System.out.println("construct surefirebooter");
		return surefireBooter;
		// TODO Auto-generated method stub
		//return super.constructSurefireBooter();
	}

	
	
	private ArtifactResolutionResult resolveArtifact( Artifact filteredArtifact, Artifact providerArtifact )
	throws ArtifactResolutionException, ArtifactNotFoundException
	{
		ArtifactFilter filter = null;
		if ( filteredArtifact != null )
		{
			filter = new ExcludesArtifactFilter(
					Collections.singletonList( filteredArtifact.getGroupId() + ":" + filteredArtifact.getArtifactId() ) );
		}

		Artifact originatingArtifact = getArtifactFactory().createBuildArtifact( "dummy", "dummy", "1.0", "jar" );

		return getArtifactResolver().resolveTransitively( Collections.singleton( providerArtifact ), originatingArtifact,
				getLocalRepository(), getRemoteRepositories(),
				getMetadataSource(), filter );
	}

	private void addArtifact( SurefireBooter surefireBooter, Artifact surefireArtifact )
	throws ArtifactNotFoundException, ArtifactResolutionException
	{
		ArtifactResolutionResult result = resolveArtifact( null, surefireArtifact );

		for ( Iterator i = result.getArtifacts().iterator(); i.hasNext(); )
		{
			Artifact artifact = (Artifact) i.next();

			getLog().debug( "Adding to " + getPluginName() + " booter test classpath: " + artifact.getFile().getAbsolutePath() + " Scope: " + artifact.getScope() );

			surefireBooter.addSurefireBootClassPathUrl( artifact.getFile().getAbsolutePath() );
		}
	}
	
	/**
	 * Converts old TestNG configuration parameters over to new properties based configuration
	 * method. (if any are defined the old way)
	 */
	private void convertTestNGParameters()
	{
		if ( getProperties() == null )
		{
			setProperties( new Properties() );
		}

		if ( this.getParallel() != null )
		{
			getProperties().setProperty( "parallel", this.getParallel() );
		}
		if ( this.getExcludedGroups() != null )
		{
			getProperties().setProperty( "excludegroups", this.getExcludedGroups() );
		}
		if ( this.getGroups() != null )
		{
			getProperties().setProperty( "groups", this.getGroups() );
		}

		if ( this.getThreadCount() > 0 )
		{
			getProperties().setProperty( "threadcount", Integer.toString( this.getThreadCount() ) );
		}
		if ( this.getObjectFactory() != null )
		{
			getProperties().setProperty( "objectfactory", this.getObjectFactory() );
		}
	}
	/**
	 * <p/>
	 * Adds Reporters that will generate reports with different formatting.
	 * <p/>
	 * The Reporter that will be added will be based on the value of the parameter useFile, reportFormat, and
	 * printSummary.
	 *
	 * @param surefireBooter The surefire booter that will run tests.
	 * @param forking
	 */
	private void addReporters( SurefireBooter surefireBooter, boolean forking )
	{
		Boolean trimStackTrace = Boolean.valueOf( this.isTrimStackTrace() );
		if ( isUseFile() )
		{
			if ( isPrintSummary() )
			{
				if ( forking )
				{
					surefireBooter.addReport( ForkingConsoleReporter.class.getName(), new Object[]{trimStackTrace} );
				}
				else
				{
					surefireBooter.addReport( ConsoleReporter.class.getName(), new Object[]{trimStackTrace} );
				}
			}

			if ( BRIEF_REPORT_FORMAT.equals( getReportFormat() ) )
			{
				surefireBooter.addReport( BriefFileReporter.class.getName(),
						new Object[]{ getReportsDirectory(), trimStackTrace} );
			}
			else if ( PLAIN_REPORT_FORMAT.equals( getReportFormat() ) )
			{
				surefireBooter.addReport( FileReporter.class.getName(),
						new Object[]{ getReportsDirectory(), trimStackTrace} );
			}
		}
		else
		{
			if ( BRIEF_REPORT_FORMAT.equals( getReportFormat() ) )
			{
				surefireBooter.addReport( BriefConsoleReporter.class.getName(), new Object[]{trimStackTrace} );
			}
			else if ( PLAIN_REPORT_FORMAT.equals( getReportFormat() ) )
			{
				surefireBooter.addReport( DetailedConsoleReporter.class.getName(), new Object[]{trimStackTrace} );
			}
		}

		if ( !isDisableXmlReport() )
		{
			surefireBooter.addReport( XMLReporter.class.getName(), new Object[]{ getReportsDirectory(), trimStackTrace} );
		}
	}
	private void addProvider( SurefireBooter surefireBooter, String provider, String version,
			Artifact filteredArtifact )
	throws ArtifactNotFoundException, ArtifactResolutionException
	{
		Artifact providerArtifact = getArtifactFactory().createDependencyArtifact( "org.apache.maven.surefire", provider,
				VersionRange.createFromVersion( version ),
				"jar", null, Artifact.SCOPE_TEST );
		ArtifactResolutionResult result = resolveArtifact( filteredArtifact, providerArtifact );

		for ( Iterator i = result.getArtifacts().iterator(); i.hasNext(); )
		{
			Artifact artifact = (Artifact) i.next();

			getLog().debug( "Adding to " + getPluginName() + " test classpath: " + artifact.getFile().getAbsolutePath() + " Scope: " + artifact.getScope() );

			surefireBooter.addSurefireClassPathUrl( artifact.getFile().getAbsolutePath() );
		}
	}
	private void showMap( Map map, String setting )
	{
		for ( Iterator i = map.keySet().iterator(); i.hasNext(); )
		{
			String key = (String) i.next();
			String value = (String) map.get( key );
			getLog().debug( "Setting " + setting + " [" + key + "]=[" + value + "]" );
		}
	}
	private boolean isAnyJunit4( Artifact artifact )
	throws MojoExecutionException
	{
		return isWithinVersionSpec( artifact, "[4.0,)" );
	}
	private boolean isJunit47Compatible( Artifact artifact )
	throws MojoExecutionException
	{
		return isWithinVersionSpec( artifact, "[4.7,)" );
	}

	private boolean isWithinVersionSpec( Artifact artifact, String versionSpec )
	throws MojoExecutionException
	{
		if ( artifact == null )
		{
			return false;
		}
		try
		{
			VersionRange range = VersionRange.createFromVersionSpec( versionSpec );
			try
			{
				return range.containsVersion( artifact.getSelectedVersion() );
			}
			catch ( NullPointerException e )
			{
				return range.containsVersion( new DefaultArtifactVersion( artifact.getBaseVersion() ) );
			}
		}
		catch ( InvalidVersionSpecificationException e )
		{
			throw new MojoExecutionException( "Bug in junit 4.7 plugin. Please report with stacktrace" );
		}
		catch ( OverConstrainedVersionException e )
		{
			throw new MojoExecutionException( "Bug in junit 4.7 plugin. Please report with stacktrace" );
		}
	}
	
	/**
     * Converts old TestNG configuration parameters over to new properties based configuration
     * method. (if any are defined the old way)
     */
    private void convertJunitCoreParameters()
    {
        if ( getProperties() == null )
        {
            setProperties( new Properties() );
        }

        if ( this.getParallel() != null )
        {
            getProperties().setProperty( "parallel", this.getParallel() );
        }
        if ( this.getThreadCount() > 0 )
        {
            getProperties().setProperty( "threadCount", Integer.toString( this.getThreadCount() ) );
        }
        if ( this.getPerCoreThreadCount() != null )
        {
            getProperties().setProperty( "perCoreThreadCount", getPerCoreThreadCount() );
        }
        if ( this.getUseUnlimitedThreads() != null )
        {
            getProperties().setProperty( "useUnlimitedThreads", getUseUnlimitedThreads() );
        }
        Artifact configurableParallelComputer =
            (Artifact) getProjectArtifactMap().get( "org.jdogma.junit:configurable-parallel-computer" );
        getProperties().setProperty( "configurableParallelComputerPresent",
                                Boolean.toString( configurableParallelComputer != null ) );

    }
}
