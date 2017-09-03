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
package org.netbeans.api.java.source;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.swing.text.Document;
import javax.tools.Diagnostic;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.modules.java.source.parsing.CompilationInfoImpl;
import org.netbeans.modules.java.source.parsing.HackAccessor;
import org.netbeans.modules.java.source.save.ElementOverlay;
import org.netbeans.modules.parsing.api.Snapshot;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class CompilationInfoHack extends WorkingCopy {

    private final Context context;
    private final ClasspathInfo cpInfo;
    private FileObject file;
    private String text;
    private TokenHierarchy<?> th;
    private final CompilationUnitTree cut;
    private PositionConverter conv;
    private final org.netbeans.modules.jackpot30.resolve.api.CompilationInfo resolvedInfo;
    
    public CompilationInfoHack(org.netbeans.modules.jackpot30.resolve.api.CompilationInfo resolvedInfo) {
        //TODO: a more sane ClasspathInfo:
        this((JavacTaskImpl) resolvedInfo.getJavacTask(), ClasspathInfo.create(ClassPath.EMPTY, ClassPath.EMPTY, ClassPath.EMPTY), (JCCompilationUnit) resolvedInfo.getCompilationUnit(), resolvedInfo);
    }

    private CompilationInfoHack(JavacTaskImpl jti, ClasspathInfo cpInfo, JCCompilationUnit cut, org.netbeans.modules.jackpot30.resolve.api.CompilationInfo resolvedInfo) {
        super(HackAccessor.createCII(cpInfo), ElementOverlay.getOrCreateOverlay());
        this.context = jti.getContext();
        this.cpInfo = cpInfo;
        try {
            this.text = cut.sourcefile.getCharContent(false).toString();
            this.file = FileUtil.createMemoryFileSystem().getRoot().createData("Dummy.java");//XXX
            Writer w = new OutputStreamWriter(this.file.getOutputStream());
            w.write(this.text);
            w.close();
            this.th = TokenHierarchy.create(text, JavaTokenId.language());

            conv = new PositionConverter(/*SnapshotHack.create(text)*/);
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        this.cut = cut;

        try {
            Field javacTask = CompilationInfoImpl.class.getDeclaredField("javacTask");

            javacTask.setAccessible(true);
            javacTask.set(this.impl, jti);

            Method init = WorkingCopy.class.getDeclaredMethod("init");

            init.setAccessible(true);
            init.invoke(this);
        } catch (InvocationTargetException ex) {
            Exceptions.printStackTrace(ex);
        } catch (NoSuchMethodException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
        } catch (NoSuchFieldException ex) {
            Exceptions.printStackTrace(ex);
        } catch (SecurityException ex) {
            Exceptions.printStackTrace(ex);
        }
        this.resolvedInfo = resolvedInfo;
    }

    @Override
    public TreePath getChangedTree() {
        return null;
    }

    @Override
    public ClasspathInfo getClasspathInfo() {
        return cpInfo;
    }

    @Override
    public CompilationUnitTree getCompilationUnit() {
        return cut;
    }

    @Override
    public List<Diagnostic> getDiagnostics() {
        //could be enabled if necessary:
        throw new UnsupportedOperationException();
    }

    @Override
    public Document getDocument() throws IOException {
        return null;
    }

    @Override
    public synchronized ElementUtilities getElementUtilities() {
        return super.getElementUtilities();
    }

    @Override
    public Elements getElements() {
        return JavacElements.instance(context);
    }

    @Override
    public FileObject getFileObject() {
        return file;
    }

    public CompilationInfoImpl getImpl() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaSource getJavaSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Phase getPhase() {
        return Phase.RESOLVED;
    }

    @Override
    public PositionConverter getPositionConverter() {
        return conv;
    }

    @Override
    public Snapshot getSnapshot() {
        return org.netbeans.modules.parsing.api.Source.create(file).createSnapshot();
    }

    @Override
    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(Source.instance(context));
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public TokenHierarchy<?> getTokenHierarchy() {
        return th;
    }

    @Override
    public List<? extends TypeElement> getTopLevelElements() throws IllegalStateException {
        final List<TypeElement> result = new ArrayList<TypeElement>();
        CompilationUnitTree cu = getCompilationUnit();
        if (cu == null) {
            return null;
        }
        final Trees trees = getTrees();
        assert trees != null;
        List<? extends Tree> typeDecls = cu.getTypeDecls();
        TreePath cuPath = new TreePath(cu);
        for( Tree t : typeDecls ) {
            TreePath p = new TreePath(cuPath,t);
            Element e = trees.getElement(p);
            if ( e != null && ( e.getKind().isClass() || e.getKind().isInterface() ) ) {
                result.add((TypeElement)e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized TreeUtilities getTreeUtilities() {
        return super.getTreeUtilities();
    }

    @Override
    public Trees getTrees() {
        return JavacTrees.instance(context);
    }

    @Override
    public synchronized TypeUtilities getTypeUtilities() {
        return super.getTypeUtilities();
    }

    @Override
    public Types getTypes() {
        return JavacTypes.instance(context);
    }

    public Context getContext() {
        return context;
    }

}
