/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2010 Sun Microsystems, Inc.
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
