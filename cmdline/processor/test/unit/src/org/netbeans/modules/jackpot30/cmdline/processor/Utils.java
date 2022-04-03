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
package org.netbeans.modules.jackpot30.cmdline.processor;

import java.util.Arrays;
import java.util.List;
import static junit.framework.TestCase.assertNotNull;

public class Utils {

    public static List<String> findJavacLauncher() {
        String javaHome = System.getProperty("java.home");
        
        if (System.getProperty("sun.boot.class.path") != null) {
            //JDK 8:
            final String nbJavacPath = System.getProperty("nb.javac.path");
            assertNotNull("Path to nb-javac must be set in the 'nb.javac.path' property!", nbJavacPath);
            return Arrays.asList(javaHome + "/../bin/javac",
                                 "-J-Xbootclasspath/p:" + nbJavacPath);
        } else {
            //JDK 9+:
            return Arrays.asList(javaHome + "/bin/javac",
                                 "-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                                 "-J--add-opens=java.base/java.net=ALL-UNNAMED", //stream handler factory
                                 "-J--add-opens=java.desktop/sun.awt=ALL-UNNAMED", //org.openide.util.RequestProcessor$TopLevelThreadGroup to method sun.awt.AppContext.getAppContext()
                                 "-J--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED", //Illegal reflective access by org.netbeans.modules.jackpot30.cmdline.ProcessorImpl to field com.sun.tools.javac.processing.JavacProcessingEnvironment.context
                                 "-J--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" //Illegal reflective access by org.netbeans.modules.jackpot30.cmdline.ProcessorImpl to method com.sun.tools.javac.util.Context.get(java.lang.Class)
            );
        }
    }

}
