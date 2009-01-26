package org.codehaus.mojo.versions;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.stream.XMLStreamException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.sink.Sink;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;

/**
 * This report summarizes all project dependencies for which newer versions may exist. For convenience, the new versions
 * are segregated by incremental, minor, and major changes, since each tends to have a different level of effort (and
 * risk) involved when upgrading.
 *
 * @author Matthew Beermann <matthew.beermann@cerner.com>
 * @goal display-dependency-updates-report
 * @requiresDependencyResolution runtime
 * @requiresProject true
 * @since 1.0-alpha-3
 */
public class DisplayDependencyUpdatesReport
    extends AbstractVersionsUpdaterMojo
    implements MavenReport
{
    /**
     * A list of groupId:artifactId keys, which indicate that the corresponding artifact(s) should be omitted from the
     * report (even when showAll is true). For example:<br/>
     * &lt;excludes&gt;<br/>
     * &nbsp;&nbsp;&nbsp;&nbsp;&lt;exclude&gt;com.oracle:ojdbc14&lt;/exclude&gt;<br/>
     * &lt;/excludes&gt;
     *
     * @parameter expression="${excludes}"
     */
    protected ArrayList excludes;

    /**
     * Directory where reports will go.
     *
     * @parameter expression="${project.reporting.outputDirectory}"
     * @required
     */
    protected File reportOutputDirectory;

    /**
     * If true, show <i>all</i> unexcluded dependencies in the report - even those that have no updates available.
     *
     * @parameter expression="${showAll}" default-value="false"
     * @required
     */
    protected Boolean showAll;

    protected void update( ModifiedPomXMLEventReader pom )
        throws MojoExecutionException, MojoFailureException, XMLStreamException
    {
        throw new UnsupportedOperationException();
    }

    public boolean canGenerateReport()
    {
        return true;
    }

    public void generate( Sink sink, Locale locale )
        throws MavenReportException
    {
        Map artifacts = new TreeMap();
        for ( Iterator it = getProject().getArtifacts().iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();
            ArtifactVersion currentVersion = new DefaultArtifactVersion( artifact.getVersion() );
            if ( isExcluded( artifact ) )
            {
                continue;
            }

            try
            {
                // Find the latest version, accepting major changes
                // Range: [current,)
                ArtifactVersion latestMajor = findLatestVersion( artifact, VersionRange.createFromVersionSpec(
                    "[" + artifact.getVersion() + ",)" ), allowSnapshots );

                // Find the latest version, accepting minor changes
                // Range: [current, (currentMajor+1).0.0)
                ArtifactVersion latestMinor = findLatestVersion( artifact, VersionRange.createFromVersionSpec(
                    "[" + artifact.getVersion() + "," + ( currentVersion.getMajorVersion() + 1 ) + ".0.0)" ),
                                                                 allowSnapshots );

                // Find the latest version, accepting incremental changes
                // Range: [current, (currentMajor).(currentMinor+1).0)
                ArtifactVersion latestIncremental = findLatestVersion( artifact, VersionRange.createFromVersionSpec(
                    "[" + artifact.getVersion() + "," + currentVersion.getMajorVersion() + "."
                        + ( currentVersion.getMinorVersion() + 1 ) + ".0)" ), allowSnapshots );

                // Add the results of our search to the collection
                MultiVersionSummary summary =
                    new MultiVersionSummary( artifact, currentVersion, latestMajor, latestMinor, latestIncremental );
                if ( hasUpdates( summary ) )
                {
                    artifacts.put( artifact.getId(), summary );
                }
            }
            catch ( Exception e )
            {
                // If something goes haywire, warn, but continue on our merry way
                getLog().warn( "Problem encountered while searching for newer versions:", e );
            }
        }

        // Last but not least, send everything we've gathered off to be rendered
        DisplayDependencyUpdatesRenderer renderer =
            new DisplayDependencyUpdatesRenderer( sink, artifacts, getVersionComparator() );
        renderer.render();
    }

    public String getCategoryName()
    {
        return MavenReport.CATEGORY_PROJECT_INFORMATION;
    }

    public String getDescription( Locale locale )
    {
        return "A report summarizing newer versions of the project's dependencies that may be available.";
    }

    public String getName( Locale locale )
    {
        return "Dependency Updates";
    }

    public String getOutputName()
    {
        return "versions";
    }

    public File getReportOutputDirectory()
    {
        return reportOutputDirectory;
    }

    protected boolean hasUpdates( MultiVersionSummary summary )
    {
        // If we've been told to show them all, well, show them all
        if ( Boolean.TRUE.equals( showAll ) )
        {
            return true;
        }

        // Err on the side of caution for dependencies we don't understand
        ArtifactVersion currentVersion = summary.getCurrentVersion();
        if ( currentVersion.toString().equals( currentVersion.getQualifier() ) )
        {
            return true;
        }

        // Are any of the versions we found larger than the one we have now?
        Comparator comparator = getVersionComparator();
        if ( summary.getLatestIncremental() != null
            && comparator.compare( currentVersion, summary.getLatestIncremental() ) < 0 )
        {
            return true;
        }
        else if ( summary.getLatestMinor() != null
            && comparator.compare( currentVersion, summary.getLatestMinor() ) < 0 )
        {
            return true;
        }
        else if ( summary.getLatestMajor() != null
            && comparator.compare( currentVersion, summary.getLatestMajor() ) < 0 )
        {
            return true;
        }

        // If we made it this far, then the answer is apparently "no"...
        return false;
    }

    protected boolean isExcluded( Artifact artifact )
    {
        if ( excludes == null || excludes.size() == 0 )
        {
            return false;
        }

        String candidate = artifact.getGroupId() + ":" + artifact.getArtifactId();
        if ( excludes.contains( candidate ) )
        {
            return true;
        }

        return false;
    }

    public boolean isExternalReport()
    {
        return false;
    }

    public void setReportOutputDirectory( File reportOutputDirectory )
    {
        this.reportOutputDirectory = reportOutputDirectory;
    }

    protected static class MultiVersionSummary
    {
        private final Artifact artifact;

        private final ArtifactVersion currentVersion, latestMajor, latestMinor, latestIncremental;

        public MultiVersionSummary( Artifact artifact, ArtifactVersion currentVersion, ArtifactVersion latestMajor,
                                    ArtifactVersion latestMinor, ArtifactVersion latestIncremental )
        {
            this.artifact = artifact;
            this.currentVersion = currentVersion;
            this.latestMajor = latestMajor;
            this.latestMinor = latestMinor;
            this.latestIncremental = latestIncremental;
        }

        /**
         * @return the artifact
         */
        public Artifact getArtifact()
        {
            return artifact;
        }

        /**
         * @return the currentVersion
         */
        public ArtifactVersion getCurrentVersion()
        {
            return currentVersion;
        }

        /**
         * @return the latestMajor
         */
        public ArtifactVersion getLatestMajor()
        {
            return latestMajor;
        }

        /**
         * @return the latestMinor
         */
        public ArtifactVersion getLatestMinor()
        {
            return latestMinor;
        }

        /**
         * @return the latestIncremental
         */
        public ArtifactVersion getLatestIncremental()
        {
            return latestIncremental;
        }
    }
}
