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
package org.netbeans.modules.jackpot30.resolve.api;

import com.sun.tools.javac.api.JavacTaskImpl;
import java.io.Writer;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public abstract class JavacCreator {

    public static JavacTaskImpl create(Writer out,
                                     JavaFileManager fileManager,
                                     DiagnosticListener<? super JavaFileObject> diagnosticListener,
                                     Iterable<String> options,
                                     Iterable<String> classes,
                                     Iterable<? extends JavaFileObject> compilationUnits) {
        JavacCreator c = Lookup.getDefault().lookup(JavacCreator.class);

        if (c != null) return c.doCreate(out, fileManager, diagnosticListener, options, classes, compilationUnits);

        return (JavacTaskImpl) ToolProvider.getSystemJavaCompiler().getTask(out, fileManager, diagnosticListener, options, classes, compilationUnits);
    }

    protected abstract JavacTaskImpl doCreate(Writer out,
                                             JavaFileManager fileManager,
                                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                                             Iterable<String> options,
                                             Iterable<String> classes,
                                             Iterable<? extends JavaFileObject> compilationUnits);
}
