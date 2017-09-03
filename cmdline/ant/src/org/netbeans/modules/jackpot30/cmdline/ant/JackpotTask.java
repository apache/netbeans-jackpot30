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
