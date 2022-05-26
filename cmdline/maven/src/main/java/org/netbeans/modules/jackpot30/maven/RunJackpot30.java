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

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            boolean failOnWarnings = Utils.getJackpotFailOnWarnings(project);

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

            if (failOnWarnings) {
                cmdLine.add("--fail-on-warnings");
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

            Path bin = Paths.get(System.getProperty("java.home"))
                            .resolve("bin");
            Path launcher = bin.resolve("java");
            if (!Files.exists(launcher)) {
                launcher = bin.resolve("java.exe");
            }
            cmdLine.addAll(0, Arrays.asList(launcher.toAbsolutePath().toString(),
                                            "-classpath", Main.class.getProtectionDomain().getCodeSource().getLocation().getPath(),
                                            "-XX:+IgnoreUnrecognizedVMOptions",
                                            "--add-opens=java.base/java.net=ALL-UNNAMED",
                                            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                                            Main.class.getCanonicalName()));
            if (new ProcessBuilder(cmdLine).inheritIO().start().waitFor() != 0) {
                throw new MojoExecutionException("jackpo30 failed.");
            }
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
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
