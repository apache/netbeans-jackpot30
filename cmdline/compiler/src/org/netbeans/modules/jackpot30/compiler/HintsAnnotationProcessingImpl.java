/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2011 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2009-2011 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.compiler;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Key;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationInfoHack;
import org.netbeans.lib.nbjavac.services.NBParserFactory;
import org.netbeans.lib.nbjavac.services.NBResolve;
import org.netbeans.modules.jackpot30.compiler.AbstractHintsAnnotationProcessing.Reporter;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@SupportedAnnotationTypes("*")
@ServiceProvider(service=Processor.class)
public final class HintsAnnotationProcessingImpl extends AbstractProcessor {

    private final Collection<String> seenTypes = new LinkedList<String>();
    private final Collection<AbstractHintsAnnotationProcessing> processors = new LinkedList<AbstractHintsAnnotationProcessing>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        for (AbstractHintsAnnotationProcessing p : Lookup.getDefault().lookupAll(AbstractHintsAnnotationProcessing.class)) {
            if (p.initialize(processingEnv)) {
                processors.add(p);
            }
        }

        if (processors.isEmpty()) {
            return;
        }

        if (!(processingEnv instanceof JavacProcessingEnvironment)) {
            throw new UnsupportedOperationException("Not a JavacProcessingEnvironment");
        }

        Context c = ((JavacProcessingEnvironment) processingEnv).getContext();
        
        MultiTaskListener.instance(c).add(new TaskListenerImpl());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement type : ElementFilter.typesIn(roundEnv.getRootElements())) {
            seenTypes.add(type.getQualifiedName().toString());
        }

        if (roundEnv.processingOver()) {
            try {
                //XXX: workarounding a bug in CRTable (see HintsAnnotationProcessingTest.testCRTable):
                Context c = ((JavacProcessingEnvironment) processingEnv).getContext();
                Options.instance(c).remove("-Xjcov");
                Field f = Gen.class.getDeclaredField("genCrt");
                f.setAccessible(true);
                f.set(Gen.instance(c), false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void doProcessing(TypeElement type) {
        if (!seenTypes.remove(type.getQualifiedName().toString())) return;
        
        Context c = ((JavacProcessingEnvironment) processingEnv).getContext();
        StandardJavaFileManager s = (StandardJavaFileManager) c.get(JavaFileManager.class);
        ClassPath boot = computeClassPath(s, StandardLocation.PLATFORM_CLASS_PATH);
        ClassPath compile = computeClassPath(s, StandardLocation.CLASS_PATH);
        ClassPath source = computeClassPath(s, StandardLocation.SOURCE_PATH);
        Trees trees = JavacTrees.instance(c);
        final Log log = Log.instance(c);
        final Key<ParserFactory> key = ParserFactoryKeyAccessor.getContextKey();
        ParserFactory origParserFactory = c.get(key);
        c.put(key, (ParserFactory) null);
        NBParserFactory.preRegister(c);
        final Key<Resolve> resolveKey = ResolveKeyAccessor.getContextKey();
        Resolve origResolve = c.get(resolveKey);
        c.put(resolveKey, (Resolve) null);
        NBResolve.preRegister(c);

        try {
            TreePath elTree = trees.getPath(type);
            JCCompilationUnit cut = (JCCompilationUnit) elTree.getCompilationUnit();

            if (!cut.sourcefile.toUri().isAbsolute()) {
                processingEnv.getMessager().printMessage(Kind.NOTE, "Not an absolute URI: " + cut.sourcefile.toUri().toASCIIString(), type);
                return ; //XXX
            }

            CompilationInfoHack info = new CompilationInfoHack(c, ClasspathInfo.create(boot, compile, source), cut);
            JavaFileObject origSourceFile = log.currentSourceFile();

            try {
                log.useSource(cut.sourcefile);

                for (AbstractHintsAnnotationProcessing p : processors) {
                    p.doProcess(info, processingEnv, new Reporter() {
                        @Override public void warning(int offset, String message) {
                            log.warning(offset, "proc.messager", message);
                        }
                    });
                }
            } finally {
                log.useSource(origSourceFile);
            }
        } finally {
            if (seenTypes.isEmpty()) {
                for (AbstractHintsAnnotationProcessing p : processors) {
                    p.finish();
                }
            }

            c.put(key, (ParserFactory) null);
            c.put(key, origParserFactory);
            c.put(resolveKey, (Resolve) null);
            c.put(resolveKey, origResolve);
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedOptions() {
        Set<String> options = new HashSet<String>();

        for (AbstractHintsAnnotationProcessing p : Lookup.getDefault().lookupAll(AbstractHintsAnnotationProcessing.class)) {
            options.addAll(p.getSupportedOptions());
        }

        return options;
    }

    private static ClassPath computeClassPath(StandardJavaFileManager m, StandardLocation kind) {
        List<URL> urls = new LinkedList<URL>();
        Iterable<? extends File> files = m.getLocation(kind);

        if (files != null) {
            for (File f : files) {
                urls.add(FileUtil.urlForArchiveOrDir(FileUtil.normalizeFile(f.getAbsoluteFile())));
            }
        }

        return ClassPathSupport.createClassPath(urls.toArray(new URL[0]));
    }

    private final class TaskListenerImpl implements TaskListener {

        public TaskListenerImpl() { }

        @Override
        public void started(TaskEvent te) {
        }

        @Override
        public void finished(TaskEvent te) {
            if (te.getKind() == TaskEvent.Kind.ANALYZE) {
                TypeElement toProcess = te.getTypeElement();

                assert toProcess != null;
                doProcessing(toProcess);
            }
        }

    }

    private static final class ParserFactoryKeyAccessor extends ParserFactory {
        ParserFactoryKeyAccessor() {
            super(null);
        }
        public static Key<ParserFactory> getContextKey() {
            return parserFactoryKey;
        }
    }

    private static final class ResolveKeyAccessor extends Resolve {
        ResolveKeyAccessor() {
            super(null);
        }
        public static Key<Resolve> getContextKey() {
            return resolveKey;
        }
    }

    static {
        try {
            ClassLoader l = HintsAnnotationProcessingImpl.class.getClassLoader();

            if (l == null) {
                l = ClassLoader.getSystemClassLoader();
            }

            l.setClassAssertionStatus("org.netbeans.api.java.source.CompilationInfo", false);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
