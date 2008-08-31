package org.codehaus.mojo.versions;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;

import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Displays the available updates for a project.
 *
 * @author <a href="mailto:stephen.alan.connolly@gmail.com">Stephen Connolly</a>
 * @goal display-dependency-updates
 * @requires-project
 * @dontrequiresDependencyResolution test
 * @description Displays all dependencies that have newer versions available.
 */
public class DisplayDependencyUpdatesMojo
    extends AbstractVersionsUpdaterMojo
{
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        Set dependencies = new TreeSet( new Comparator()
        {
            public int compare( Object o1, Object o2 )
            {
                Dependency d1 = (Dependency) o1;
                Dependency d2 = (Dependency) o2;

                int r = d1.getGroupId().compareTo( d2.getGroupId() );
                if ( r == 0 )
                {
                    r = d1.getArtifactId().compareTo( d2.getArtifactId() );
                }
                if ( r == 0 )
                {
                    String v1 = d1.getVersion();
                    String v2 = d2.getVersion();
                    if ( v1 == null )
                    {
                        // hope I got the +1/-1 the right way around
                        return v2 == null ? 0 : -1;
                    }
                    if ( v2 == null )
                    {
                        return 1;
                    }
                    r = v1.compareTo( v2 );
                }
                return r;
            }
        } );
        dependencies.addAll( getProject().getDependencies() );
        List updates = new ArrayList();
        Iterator i = dependencies.iterator();
        while ( i.hasNext() )
        {
            Dependency dependency = (Dependency) i.next();
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String version = dependency.getVersion();
            getLog().debug( "Checking " + groupId + ":" + artifactId + " for updates newer than " + version );

            VersionRange versionRange = null;
            try
            {
                versionRange = VersionRange.createFromVersionSpec( version );
            }
            catch ( InvalidVersionSpecificationException e )
            {
                throw new MojoExecutionException( "Invalid version range specification: " + version, e );
            }

            Artifact artifact = artifactFactory.createDependencyArtifact( groupId, artifactId, versionRange,
                                                                          dependency.getType(),
                                                                          dependency.getClassifier(),
                                                                          dependency.getScope() );

            ArtifactVersion artifactVersion = findLatestVersion( artifact, versionRange, null );

            if ( artifactVersion != null &&
                getVersionComparator().compare( new DefaultArtifactVersion( version ), artifactVersion ) < 0 )
            {
                String newVersion = artifactVersion.toString();
                StringBuilder buf = new StringBuilder();
                buf.append( groupId ).append( ':' );
                buf.append( artifactId );
                buf.append( ' ' );
                int padding = 68 - version.length() - newVersion.length() - 4;
                while ( buf.length() < padding )
                {
                    buf.append( '.' );
                }
                buf.append( ' ' );
                buf.append( version );
                buf.append( " -> " );
                buf.append( newVersion );
                updates.add( buf.toString() );
            }
        }
        getLog().info( "" );
        if ( updates.isEmpty() )
        {
            getLog().info( "All dependencies are using the latest versions." );
        }
        else
        {
            getLog().info( "The following dependency updates are available:" );
            i = updates.iterator();
            while ( i.hasNext() )
            {
                getLog().info( "  " + i.next() );
            }
        }
        getLog().info( "" );
    }

    private static String getPluginGroupId( Object plugin )
    {
        return plugin instanceof ReportPlugin
            ? ( (ReportPlugin) plugin ).getGroupId()
            : ( (Plugin) plugin ).getGroupId();
    }

    private static String getPluginArtifactId( Object plugin )
    {
        return plugin instanceof ReportPlugin
            ? ( (ReportPlugin) plugin ).getArtifactId()
            : ( (Plugin) plugin ).getArtifactId();
    }

    private static String getPluginVersion( Object plugin )
    {
        return plugin instanceof ReportPlugin
            ? ( (ReportPlugin) plugin ).getVersion()
            : ( (Plugin) plugin ).getVersion();
    }

    protected void update( ModifiedPomXMLEventReader pom )
        throws MojoExecutionException, MojoFailureException, XMLStreamException
    {
        // do nothing
    }
}