/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.jackpot30.maven;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.netbeans.modules.jackpot30.cmdline.Main;

/**
 * @goal showgui
 * @author Jan Lahoda
 */
public class ShowGuiJackpot30 extends AbstractMojo {

    /**
     * @parameter property="project"
     * @required
     * @readonly
     */
    private MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (!project.isExecutionRoot()) return;

            String configurationFile = Utils.getJackpotConfigurationFile(project);

            if (configurationFile == null)
                throw new MojoExecutionException("No configuration file specified, cannot show configuration GUI");

            List<String> cmdLine = new ArrayList<String>();

            cmdLine.add("--config-file");
            cmdLine.add(configurationFile);
            cmdLine.addAll(RunJackpot30.sourceAndCompileClassPaths(project.getCollectedProjects()));
            cmdLine.add("--show-gui");
            System.err.println(cmdLine);

            Main.compile(cmdLine.toArray(new String[0]));
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } catch (ClassNotFoundException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } catch (DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }

}
