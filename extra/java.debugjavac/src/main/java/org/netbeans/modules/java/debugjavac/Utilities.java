/*
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
package org.netbeans.modules.java.debugjavac;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.ClassPath.PathConversionMode;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.queries.SourceLevelQuery;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public class Utilities {
    public static List<String> commandLineParameters(FileObject source, String extraParameters) throws IOException {
        List<String> extraParams = new ArrayList<>();
        Set<String> extraParamsSet = new HashSet<>();
        StringBuilder param = new StringBuilder();
        char currentQuote = '\0';
        for (char c : extraParameters.toCharArray()) {
            switch (c) {
                case '\r': continue;
                case  ' ': case '\n': case '\t':
                    if (currentQuote == '\0') {
                        String p = param.toString();
                        param.delete(0, param.length());
                        extraParams.add(p);
                        extraParamsSet.add(p);
                        continue;
                    }
                    break;
                case '"':
                    if (currentQuote == '"') {
                        currentQuote = '\0';
                        continue;
                    } else if (currentQuote == '\0') {
                        currentQuote = '"';
                        continue;
                    }
                    break;
                case '\'':
                    if (currentQuote == '\'') {
                        currentQuote = '\0';
                        continue;
                    } else if (currentQuote == '\0') {
                        currentQuote = '\'';
                        continue;
                    }
                    break;
            }

            param.append(c);
        }
        if (param.length() > 0) {
            String p = param.toString();
            extraParams.add(p);
            extraParamsSet.add(p);
        }
        List<String> result = new ArrayList<>();
//        if (!extraParamsSet.contains("-bootclasspath")) {
//            ClassPath boot = ClassPath.getClassPath(source, ClassPath.BOOT);
//            if (boot == null) boot = JavaPlatform.getDefault().getBootstrapLibraries();
//            result.add("-bootclasspath");
//            result.add(boot.toString(PathConversionMode.PRINT));
//        }
        if (!extraParamsSet.contains("-classpath")) {
            ClassPath compile = ClassPath.getClassPath(source, ClassPath.COMPILE);
            if (compile == null) compile = ClassPath.EMPTY;
            result.add("-classpath");
            result.add(compile.toString(PathConversionMode.PRINT));
        }
        String sourceLevel = SourceLevelQuery.getSourceLevel(source);
        sourceLevel = sourceLevel != null ? sourceLevel : "1.8";
        if (!extraParamsSet.contains("-source")) {
            result.add("-source");
            result.add(sourceLevel);
        }
        if (!extraParamsSet.contains("-target")) {
            result.add("-target");
            result.add(sourceLevel);
        }

        result.addAll(extraParams);
        
        return result;
    }
    
}
