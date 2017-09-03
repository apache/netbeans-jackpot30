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
package org.netbeans.modules.jackpot30.cmdline.ant;

import java.io.IOException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.LogStreamHandler;
import org.apache.tools.ant.types.CommandlineJava;
import org.apache.tools.ant.types.Path;

/**
 *
 * @author lahvac
 */
public class JackpotTask extends Task {

    private String jackpotHome;

    public void setJackpotHome(String jackpotHome) {
        this.jackpotHome = jackpotHome;
    }

    private Path src;

    public Path createSrc() {
        return src = new Path(getProject());
    }

    private String sourcepath;

    public void setSourcepath(String sourcepath) {
        this.sourcepath = sourcepath;
    }

    private String sourcelevel;

    public void setSource(String sourcelevel) {
        this.sourcelevel = sourcelevel;
    }

    private Path classpath;

    public Path createClasspath() {
        return this.classpath = new Path(getProject());
    }

    public Path getClasspath() {
        return classpath != null ? classpath : createClasspath();
    }

    private String configFile;

    public void setConfigfile(String file) {
        this.configFile = file;
    }

    @Override
    public void execute() throws BuildException {
        try {
            CommandlineJava cmdLine = new CommandlineJava();

            if (jackpotHome == null) {
                throw new BuildException("Must specify jackpotHome");
            }

            Path srcPath = src;

            if (srcPath == null) {
                if (sourcepath == null) {
                    throw new BuildException("Must specify either src subelement or sourcepath");
                }
                
                srcPath = new Path(getProject(), sourcepath);
            }

            cmdLine.createClasspath(getProject()).add(new Path(getProject(), jackpotHome + "/jackpot.jar"));
            cmdLine.setClassname("org.netbeans.modules.jackpot30.cmdline.Main");

            addArguments(cmdLine, "-no-apply");
            addArguments(cmdLine, "-sourcepath", srcPath.toString());
            addArguments(cmdLine, "-classpath", getClasspath().toString());
            if (sourcelevel != null) addArguments(cmdLine, "--source", sourcelevel);
            if (configFile != null) addArguments(cmdLine, "--config-file", configFile);
            addArguments(cmdLine, srcPath.list());

            Execute exec = new Execute(new LogStreamHandler(this, Project.MSG_INFO, Project.MSG_WARN));
            exec.setCommandline(cmdLine.getCommandline());
            exec.execute();
        } catch (IOException ex) {
            throw new BuildException(ex);
        }
    }

    private static void addArguments(CommandlineJava cmdLine, String... args) {
        for (String arg : args) {
            cmdLine.createArgument().setValue(arg);
        }
    }

}
