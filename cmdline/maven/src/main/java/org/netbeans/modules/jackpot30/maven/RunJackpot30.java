/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.netbeans.modules.jackpot30.cmdline.Main;

public abstract class RunJackpot30 extends AbstractMojo {

    protected final void doRun(MavenProject project, boolean apply) throws MojoExecutionException, MojoFailureException {
        try {
            String sourceLevel = "1.5";
            Xpp3Dom sourceLevelConfiguration = Utils.getPluginConfiguration(project, "org.apache.maven.plugins", "maven-compiler-plugin");

            if (sourceLevelConfiguration != null) {
                Xpp3Dom source = sourceLevelConfiguration.getChild("source");

                if (source != null) {
                    sourceLevel = source.getValue();
                }
            }

            String configurationFile = Utils.getJackpotConfigurationFile(project);

            List<String> cmdLine = new ArrayList<String>();

            if (apply)
                cmdLine.add("--apply");
            else
                cmdLine.add("--no-apply");

            cmdLine.addAll(sourceAndCompileClassPaths(Collections.singletonList(project)));
            cmdLine.add("--source");
            cmdLine.add(sourceLevel);

            if (configurationFile != null) {
                cmdLine.add("--config-file");
                cmdLine.add(configurationFile);
            }

            boolean hasSourceRoots = false;

            for (String sr : (List<String>) project.getCompileSourceRoots()) {
                if (!hasSourceRoots && new File(sr).isDirectory()) {
                    hasSourceRoots = true;
                }
                cmdLine.add(sr);
            }

            if (!hasSourceRoots) {
                getLog().debug("jackpot30 analyze: Not source roots to operate on");
                return ;
            }

            Main.compile(cmdLine.toArray(new String[0]));
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } catch (ClassNotFoundException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } catch (DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }

    private static String toClassPathString(List<String> entries) {
        StringBuilder classPath = new StringBuilder();

        for (String root : entries) {
            if (classPath.length() > 0) classPath.append(File.pathSeparatorChar);
            classPath.append(root);
        }

        return classPath.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<String> sourceAndCompileClassPaths(Iterable<? extends MavenProject> projects) throws DependencyResolutionRequiredException {
        List<String> compileSourceRoots = new ArrayList<String>();
        List<String> compileClassPath = new ArrayList<String>();

        for (MavenProject project : projects) {
            compileSourceRoots.addAll((List<String>) project.getCompileSourceRoots());

            for (Resource r : (List<Resource>) project.getResources()) {
                compileSourceRoots.add(r.getDirectory());
            }
            
            compileClassPath.addAll((List<String>) project.getCompileClasspathElements());
        }

        return Arrays.asList("--sourcepath",
                             toClassPathString(compileSourceRoots),
                             "--classpath",
                             toClassPathString(compileClassPath));
    }
}
