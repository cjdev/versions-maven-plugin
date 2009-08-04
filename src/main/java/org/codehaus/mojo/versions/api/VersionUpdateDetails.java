package org.codehaus.mojo.versions.api;

/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import org.apache.maven.artifact.versioning.ArtifactVersion;

/**
 * Information about version updates.
 *
 * @author connollys
 * @since Aug 4, 2009 10:07:12 AM
 */
public interface VersionUpdateDetails
{
    ArtifactVersion getNextIncremental();

    ArtifactVersion getLatestIncremental();

    ArtifactVersion getNextMinor();

    ArtifactVersion getLatestMinor();

    ArtifactVersion getNextMajor();

    ArtifactVersion getLatestMajor();

    ArtifactVersion getNextVersion();

    ArtifactVersion[] getAll();
}
