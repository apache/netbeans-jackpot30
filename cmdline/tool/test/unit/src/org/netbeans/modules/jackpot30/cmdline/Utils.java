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
package org.netbeans.modules.jackpot30.cmdline;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import static junit.framework.TestCase.assertNotNull;
import org.openide.filesystems.FileUtil;

public class Utils {

    public static List<String> findJavaLauncher() {
        String javaHome = System.getProperty("java.home");
        
        if (System.getProperty("sun.boot.class.path") != null) {
            //JDK 8:
            return Arrays.asList(javaHome + "/bin/java");
        } else {
            //JDK 9+:
            return Arrays.asList(javaHome + "/bin/java",
                                 "-Djava.awt.headless=true", //scratch Document (CasualDiff)
                                 "--add-opens=java.base/java.net=ALL-UNNAMED", //stream handler factory
                                 "--add-opens=java.desktop/sun.awt=ALL-UNNAMED", //org.openide.util.RequestProcessor$TopLevelThreadGroup to method sun.awt.AppContext.getAppContext()
                                 "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED" //scratch Document (CasualDiff)
            );
        }
    }

}
