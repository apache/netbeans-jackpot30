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

package org.netbeans.modules.jackpot30.compiler.ant;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.compilers.JavacExternal;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.Commandline.Argument;
import org.netbeans.modules.jackpot30.compiler.HintsAnnotationProcessing;
import org.netbeans.modules.jackpot30.compiler.IndexingAnnotationProcessor;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class JackpotCompiler extends JavacExternal {

    @Override
    public boolean execute() throws BuildException {
        try {
            Field forkField = Javac.class.getDeclaredField("fork");

            forkField.setAccessible(true);
            forkField.set(getJavac(), true);
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
        } catch (NoSuchFieldException ex) {
            Exceptions.printStackTrace(ex);
        } catch (SecurityException ex) {
            Exceptions.printStackTrace(ex);
        }
        
        return super.execute();
    }

    @Override
    protected Commandline setupJavacCommandlineSwitches(Commandline cmd, boolean useDebugLevel) {
        super.setupJavacCommandlineSwitches(cmd, useDebugLevel);

        URL jackpotCompiler = HintsAnnotationProcessing.class.getProtectionDomain().getCodeSource().getLocation();
        String jackpotCompilerPath = jackpotCompiler.getPath();
        Argument arg = cmd.createArgument(true);
        List<String> options = new LinkedList<String>();
        StringBuilder enabledHintsProp = new StringBuilder();

        options.addAll(HintsAnnotationProcessing.OPTIONS);
        options.addAll(IndexingAnnotationProcessor.OPTIONS);

        for (String prop : options) {
            String val = getProject().getProperty(prop);

            if (val != null) {
                enabledHintsProp.append(" -A").append(prop).append("=").append(val);
            }
        }

        arg.setLine("-J-Xbootclasspath/p:" + jackpotCompilerPath + " -Xjcov -J-Xmx256m" + enabledHintsProp); //XXX: Xmx!

        return cmd;
    }


}
