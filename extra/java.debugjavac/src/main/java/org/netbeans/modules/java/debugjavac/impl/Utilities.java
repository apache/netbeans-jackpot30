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
package org.netbeans.modules.java.debugjavac.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import org.netbeans.modules.java.debugjavac.Decompiler.Input;

/**
 *
 * @author lahvac
 */
public class Utilities {
    public static List<String> augmentCommandLineParameters(Input input) throws IOException {
        try {
            Class.forName("com.sun.tools.javac.comp.Repair");
            List<String> augmentedParams = new ArrayList<>(input.params);
            augmentedParams.add("-XDshouldStopPolicy=GENERATE");
            return augmentedParams;
        } catch (ClassNotFoundException ex) {
            //OK
            return input.params;
        }
    }
    
    public static JavaFileObject sourceFileObject(final String code) {
        return new SimpleJavaFileObject(URI.create("mem://mem"), Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                return code;
            }
            @Override public boolean isNameCompatible(String simpleName, Kind kind) {
                return true;
            }
        };
    }
    
    public static DiagnosticListener<JavaFileObject> errorReportingDiagnosticListener(final StringWriter out) {
        return new DiagnosticListener<JavaFileObject>() {
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    out.write(diagnostic.getMessage(null));
                    out.write("\n");
                }
            }
        };
    }
}
