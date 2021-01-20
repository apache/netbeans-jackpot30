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

import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javap.Context;
import com.sun.tools.javap.JavapTask;
import com.sun.tools.javap.JavapTask.BadArgs;
import com.sun.tools.javap.JavapTask.ClassFileInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.tools.DiagnosticListener;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import org.netbeans.modules.java.debugjavac.Decompiler;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class JavapDecompilerImpl implements Decompiler {

    @Override
    public Result decompile(Input input) {
        StringWriter errors = new StringWriter();
        StringWriter decompiled = new StringWriter();
        try {
            final Map<String, byte[]> bytecode = compile(input, errors);

            if (!bytecode.isEmpty()) {
                for (final Entry<String, byte[]> e : bytecode.entrySet()) {
                    class JavapTaskImpl extends JavapTask {
                        public Context getContext() {
                            return context;
                        }
                    }
                    JavapTaskImpl t = new JavapTaskImpl();
                    List<String> options = new ArrayList<String>();
                    options.add("-private");
                    options.add("-verbose");
                    options.add(e.getKey());
                    t.handleOptions(options.toArray(new String[0]));
                    t.getContext().put(PrintWriter.class, new PrintWriter(decompiled));
                    ClassFileInfo cfi = t.read(new SimpleJavaFileObject(URI.create("mem://mem"), Kind.CLASS) {
                        @Override public InputStream openInputStream() throws IOException {
                            return new ByteArrayInputStream(e.getValue());
                        }
                    });

                    t.write(cfi);
                }
            }
        } catch (IOException | ConstantPoolException ex) {
            ex.printStackTrace(new PrintWriter(errors));
        } catch (BadArgs ex) {
            Exceptions.printStackTrace(ex);
        }
        
        return new Result(errors.toString(), decompiled.toString(), "text/x-java-bytecode");
    }
    
    private static Map<String, byte[]> compile(Input input, final StringWriter errors) throws IOException {
        DiagnosticListener<JavaFileObject> errorsListener = Utilities.errorReportingDiagnosticListener(errors);
        StandardJavaFileManager sjfm = JavacTool.create().getStandardFileManager(errorsListener, null, null);
        final Map<String, ByteArrayOutputStream> class2BAOS = new HashMap<String, ByteArrayOutputStream>();

        JavaFileManager jfm = new ForwardingJavaFileManager<JavaFileManager>(sjfm) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, javax.tools.FileObject sibling) throws IOException {
                final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                
                class2BAOS.put(className, buffer);
                return new SimpleJavaFileObject(sibling.toUri(), kind) {
                    @Override
                    public OutputStream openOutputStream() throws IOException {
                        return buffer;
                    }
                };
            }
        };

        JavaFileObject file = Utilities.sourceFileObject(input.source);
        JavacTool.create().getTask(null, jfm, errorsListener, /*XXX:*/Utilities.augmentCommandLineParameters(input), null, Arrays.asList(file)).call();

        Map<String, byte[]> result = new HashMap<String, byte[]>();

        for (Map.Entry<String, ByteArrayOutputStream> e : class2BAOS.entrySet()) {
            result.put(e.getKey(), e.getValue().toByteArray());
        }

        return result;
    }
    
}
