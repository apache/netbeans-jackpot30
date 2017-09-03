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
package org.netbeans.modules.jackpot30.backend.language.api;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javadoc.JavadocClassFinder;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import org.netbeans.lib.nbjavac.services.NBAttr;
import org.netbeans.lib.nbjavac.services.NBClassReader;
import org.netbeans.lib.nbjavac.services.NBClassWriter;
import org.netbeans.lib.nbjavac.services.NBJavacTrees;
import org.netbeans.lib.nbjavac.services.NBJavadocEnter;
import org.netbeans.lib.nbjavac.services.NBJavadocMemberEnter;
import org.netbeans.lib.nbjavac.services.NBMessager;
import org.netbeans.lib.nbjavac.services.NBParserFactory;
import org.netbeans.lib.nbjavac.services.NBTreeMaker;
import org.netbeans.modules.jackpot30.resolve.api.JavacCreator;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service = JavacCreator.class)
public class JavacCreatorImpl extends JavacCreator {

    @Override
    protected JavacTaskImpl doCreate(Writer out, JavaFileManager fileManager, DiagnosticListener<? super JavaFileObject> diagnosticListener, Iterable<String> options, Iterable<String> classes, Iterable<? extends JavaFileObject> compilationUnits) {
        List<String> realOptions = new ArrayList<String>();
        for (String option : options) {
            realOptions.add(option);
        }
        realOptions.add("-Xjcov"); //NOI18N, Make the compiler store end positions
        realOptions.add("-XDallowStringFolding=false"); //NOI18N
        realOptions.add("-XDshouldStopPolicy=GENERATE");   // NOI18N, parsing should not stop in phase where an error is found
        realOptions.add("-XDsuppressAbortOnBadClassFile=true");
        realOptions.add("-XDkeepComments=true"); //NOI18N
        Context context = new Context();
        //need to preregister the Messages here, because the getTask below requires Log instance:
        NBMessager.preRegister(context, null, DEV_NULL, DEV_NULL, DEV_NULL);
        JavacTaskImpl task = (JavacTaskImpl) JavacTool.create().getTask(out, fileManager, diagnosticListener, realOptions, classes, compilationUnits, context);
        NBClassReader.preRegister(context);
        NBAttr.preRegister(context);
        NBClassWriter.preRegister(context);
        NBParserFactory.preRegister(context);
        NBTreeMaker.preRegister(context);
        NBJavacTrees.preRegister(context);
        NBJavadocEnter.preRegister(context);
        NBJavadocMemberEnter.preRegister(context);
        JavadocClassFinder.preRegister(context);

        return task;
    }

    private static final PrintWriter DEV_NULL = new PrintWriter(new NullWriter(), false);

    private static class NullWriter extends Writer {

        public void write(char[] cbuf, int off, int len) throws IOException {
        }

        public void flush() throws IOException {
        }

        public void close() throws IOException {
        }
    }
}
