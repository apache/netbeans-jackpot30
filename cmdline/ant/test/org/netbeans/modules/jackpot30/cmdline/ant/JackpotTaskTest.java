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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.tools.ant.BuildFileTest;

/**
 *
 * @author lahvac
 */
public class JackpotTaskTest extends BuildFileTest {

    public JackpotTaskTest(String testName) {
        super(testName);
    }

    public void testFailNoHome() throws Exception {
        prepareTest("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<project name=\"test\" default=\"run\" basedir=\".\">\n" +
                    "    <target name=\"run\">\n" +
                    "    <taskdef name=\"jackpot\" classname=\"org.netbeans.modules.jackpot30.cmdline.ant.JackpotTask\" classpath=\"" + System.getProperty("java.class.path") + "\"/>\n" +
                    "        <jackpot>\n" +
                    "            <src>\n" +
                    "                <pathelement path=\"src\" />\n" +
                    "            </src>\n" +
                    "        </jackpot>\n" +
                    "    </target>\n" +
                    "</project>\n");
        expectBuildException("run", "Must specify jackpotHome");
    }

    private void prepareTest(String code) throws IOException {
        File tempBuild = File.createTempFile("jackpot-ant-test", ".xml");

        tempBuild.deleteOnExit();

        OutputStream out = new FileOutputStream(tempBuild);

        out.write(code.getBytes("UTF-8"));
        out.close();

        configureProject(tempBuild.getAbsolutePath());
    }
}
