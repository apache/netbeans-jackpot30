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
