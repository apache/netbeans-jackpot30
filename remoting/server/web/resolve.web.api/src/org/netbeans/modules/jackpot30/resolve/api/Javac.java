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

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.model.LazyTreeLoader;
import com.sun.tools.javac.util.Abort;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import org.netbeans.modules.jackpot30.backend.base.SourceRoot;

/**
 *
 * @author lahvac
 */
public class Javac {
    private static final Map<SourceRoot, Reference<Javac>> category2Javac = new WeakHashMap<SourceRoot, Reference<Javac>>();

    static synchronized Javac get(SourceRoot sourceRoot) {
        Reference<Javac> javacSR = category2Javac.get(sourceRoot);
        Javac javac = javacSR != null ? javacSR.get() : null;

        if (javac == null) {
            category2Javac.put(sourceRoot, new SoftReference<Javac>(javac = new Javac(sourceRoot)));
        }

        return javac;
    }

    private final SourceRoot sourceRoot;
    private final AtomicReference<JavacTaskImpl> javacTask = new AtomicReference<JavacTaskImpl>();
    private final Map<String, CompilationInfo> path2CUT = new HashMap<String, CompilationInfo>();

    private Javac(SourceRoot sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public CompilationInfo parse(String relativePath) throws IOException, InterruptedException {
        CompilationInfo result = path2CUT.get(relativePath);

        if (result == null) {
            String content = org.netbeans.modules.jackpot30.source.api.API.readFileContent(sourceRoot.getCategory(), relativePath).replace("\r\n", "\n");
            CompilationUnitTree cut;

            try {
                cut = doParse(relativePath, content);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ignore) {
                javacTask.set(null);
                cut = doParse(relativePath, content);
            }

            path2CUT.put(relativePath, result = new CompilationInfo(this, cut, content));
        }

        return result;
    }

    private CompilationUnitTree doParse(String relativePath, String content) throws IOException {
        JavacTaskImpl javac = (JavacTaskImpl) getTask();
        Iterable<? extends CompilationUnitTree> cuts = javac.parse(new FileObjectImpl(relativePath, content));
        CompilationUnitTree cut = cuts.iterator().next();

        javac.analyze(javac.enter(Collections.singletonList(cut)));
        return cut;
    }

    public JavacTask getTask() {
        JavacTaskImpl jti = javacTask.get();

        if (jti == null) {
            FMImpl fm = new FMImpl(sourceRoot.getClassPath());
            javacTask.set(jti = JavacCreator.create(null, fm, null, Arrays.asList("-Xjcov", "-proc:none", "-XDshouldStopPolicy=FLOW"), null, Collections.<JavaFileObject>emptyList()));
            TreeLoaderImpl.preRegister(jti.getContext());
        }

        return jti;
    }

    private static class FileObjectImpl extends SimpleJavaFileObject {
        private String text;
        public FileObjectImpl(String relativePath, String text) {
            super(URI.create("myfo:/" + relativePath), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    private static final Set<String> CLASSFILE_EXTENSIONS = new HashSet<String>(Arrays.asList("class", "sig"));
    
    private static final class FMImpl implements JavaFileManager {

        private final Collection<org.openide.filesystems.FileObject> classpath;
        private final Map<org.openide.filesystems.FileObject, JFOImpl> files = new HashMap<org.openide.filesystems.FileObject, JFOImpl>();

        public FMImpl(Collection<org.openide.filesystems.FileObject> classpath) {
            this.classpath = classpath;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
            assert !recurse;
            if (!kinds.contains(Kind.CLASS) || location != StandardLocation.CLASS_PATH) return Collections.emptyList();

            String dir = packageName.replace('.', '/');
            List<JavaFileObject> result = new ArrayList<JavaFileObject>();

            for (org.openide.filesystems.FileObject root : classpath) {
                org.openide.filesystems.FileObject dirFO = root.getFileObject(dir);

                if (dirFO != null) {
                    for (org.openide.filesystems.FileObject  f : dirFO.getChildren()) {
                        if (!f.isData() || !CLASSFILE_EXTENSIONS.contains(f.getExt())) continue;

                        JFOImpl jfo = files.get(f);

                        if (jfo == null) {
                            files.put(f, jfo = new JFOImpl(f, packageName + "." + f.getName()));
                        }

                        result.add(jfo);
                    }
                }
            }

            return result;
        }

        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            return ((JFOImpl) file).binaryName;
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean handleOption(String current, Iterator<String> remaining) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean hasLocation(Location location) {
            return StandardLocation.CLASS_PATH == location;
        }

        @Override
        public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void flush() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void close() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int isSupportedOption(String option) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static final class JFOImpl implements JavaFileObject {

        private final org.openide.filesystems.FileObject file;
        private final String binaryName;

        public JFOImpl(org.openide.filesystems.FileObject file, String binaryName) {
            this.file = file;
            this.binaryName = binaryName;
        }

        @Override
        public Kind getKind() {
            return Kind.CLASS;
        }

        @Override
        public boolean isNameCompatible(String simpleName, Kind kind) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public NestingKind getNestingKind() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Modifier getAccessLevel() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public URI toUri() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return file.getInputStream();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Writer openWriter() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public long getLastModified() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean delete() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

    private static final class TreeLoaderImpl extends LazyTreeLoader {
        public static void preRegister(Context ctx) {
            ctx.put(lazyTreeLoaderKey, new Factory<LazyTreeLoader>() {
                @Override public LazyTreeLoader make(Context ctx) {
                    return new TreeLoaderImpl(ctx);
                }
            });
        }

        public TreeLoaderImpl(Context ctx) {
            ctx.put(lazyTreeLoaderKey, this);
        }

        @Override
        public void couplingError(ClassSymbol clazz, Tree t) {
            //ignore...
        }

    }
}
