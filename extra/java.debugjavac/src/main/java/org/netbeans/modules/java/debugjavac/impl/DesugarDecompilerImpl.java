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

import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.util.Context.Factory;
import com.sun.tools.javac.util.Pair;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Queue;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import org.netbeans.modules.java.debugjavac.Decompiler;

/**
 *
 * @author lahvac
 */
public class DesugarDecompilerImpl implements Decompiler {

    @Override
    public Result decompile(Input input) {
        StringWriter errors = new StringWriter();
        StringWriter decompiled = new StringWriter();
        
        try {
            DiagnosticListener<JavaFileObject> errorsListener = Utilities.errorReportingDiagnosticListener(errors);
            JavaFileObject file = Utilities.sourceFileObject(input.source);
            JavacTask task = JavacTool.create().getTask(null, 
                    null,
                    errorsListener, Utilities.augmentCommandLineParameters(input), null, Arrays.asList(file));

            JavaCompilerOverride.preRegister(((JavacTaskImpl) task).getContext(), decompiled);
            task.generate();
        } catch (IOException ex) {
            ex.printStackTrace(new PrintWriter(errors));
        }
        
        return new Result(errors.toString(), decompiled.toString().trim(), "text/x-java");
    }
    
    static class JavaCompilerOverride extends JavaCompiler {
        public static void preRegister(com.sun.tools.javac.util.Context context, final StringWriter out) {
            context.put(compilerKey, new Factory<JavaCompiler>() {
                @Override public JavaCompiler make(com.sun.tools.javac.util.Context c) {
                    return new JavaCompilerOverride(out, c);
                }
            });
        }
        private final StringWriter out;

        public JavaCompilerOverride(StringWriter out, com.sun.tools.javac.util.Context context) {
            super(context);
            this.out = out;
        }
        
        @Override public void generate(Queue<Pair<Env<AttrContext>, JCClassDecl>> queue, Queue<JavaFileObject> results) {
            Pair<Env<AttrContext>, JCClassDecl> first = queue.peek();

            if (first != null) {
                if (first.fst.toplevel.getPackageName() != null) {
                    out.write("package ");
                    out.write(first.fst.toplevel.getPackageName().toString());
                    out.write(";\n\n");
                }

                boolean hasImports = false;
                
                for (Tree importCandidate : first.fst.toplevel.defs) {
                    if (importCandidate != null && importCandidate.getKind() == Kind.IMPORT) {
                        out.write("import ");
                        ImportTree importTree = (ImportTree) importCandidate;
                        if (importTree.isStatic()) {
                            out.write("static ");
                        }
                        out.write(importTree.getQualifiedIdentifier().toString());
                        out.write(";\n");
                        hasImports = true;
                    }
                }

                if (hasImports) {
                    out.write("\n");
                }

                for (Pair<Env<AttrContext>, JCClassDecl> q : queue) {
                    out.write(q.snd.toString());
                    out.write("\n");
                }
            }
        }
    }
}
