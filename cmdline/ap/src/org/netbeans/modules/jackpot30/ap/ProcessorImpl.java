/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2015 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.ap;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.editor.tools.storage.api.ToolPreferences;
import org.netbeans.modules.jackpot30.cmdline.lib.Utils;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;
import org.netbeans.modules.java.hints.providers.spi.HintMetadata;
import org.netbeans.modules.java.hints.spiimpl.RulesManager;
import org.netbeans.modules.java.hints.spiimpl.hints.HintsInvoker;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Severity;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
@SupportedAnnotationTypes("*")
@SupportedOptions("hintsConfiguration")
public class ProcessorImpl extends AbstractProcessor {

    public static final String CONFIGURATION_OPTION = "hintsConfiguration";
    private final Map<URL, String> sources = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final Trees trees = Trees.instance(processingEnv);

        if (!roundEnv.processingOver()) {
            for (Element root : roundEnv.getRootElements()) {
                TypeElement outtermost = outtermostType(root);
                TreePath path = trees.getPath(outtermost);

                if (path == null) {
                    //TODO: log
                    continue;
                }

                try {
                    sources.put(path.getCompilationUnit().getSourceFile().toUri().toURL(), outtermost.getQualifiedName().toString());
                } catch (MalformedURLException ex) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Unexpected exception: " + ex.getMessage());
                    Logger.getLogger(ProcessorImpl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } else {
            try {
                Field contextField = processingEnv.getClass().getDeclaredField("context");
                contextField.setAccessible(true);
                Object context = contextField.get(processingEnv);
                Method get = context.getClass().getDeclaredMethod("get", Class.class);
                JavaFileManager fileManager = (JavaFileManager) get.invoke(context, JavaFileManager.class);

                if (!(fileManager instanceof StandardJavaFileManager)) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "The file manager is not a StandardJavaFileManager, cannot run Jackpot 3.0.");
                    return false;
                }

                setupCache();

                StandardJavaFileManager sfm = (StandardJavaFileManager) fileManager;

                ClassPath bootCP = toClassPath(sfm.getLocation(StandardLocation.PLATFORM_CLASS_PATH));
                ClassPath compileCP = toClassPath(sfm.getLocation(StandardLocation.CLASS_PATH));
                Iterable<? extends File> sourcePathLocation = sfm.getLocation(StandardLocation.SOURCE_PATH);
                ClassPath sourceCP = sourcePathLocation != null ? toClassPath(sourcePathLocation) : inferSourcePath();

                final Map<FileObject, String> sourceFiles = new HashMap<>();

                for (Entry<URL, String> e : sources.entrySet()) {
                    FileObject fo = URLMapper.findFileObject(e.getKey());
                    if (fo == null) {
                        //XXX:
                        return false;
                    }
                    sourceFiles.put(fo, e.getValue());
                }

                final Map<HintMetadata, ? extends Collection<? extends HintDescription>> allHints = RulesManager.getInstance().readHints(null, Arrays.asList(bootCP, compileCP, sourceCP), new AtomicBoolean());
                List<HintDescription> hints = new ArrayList<>();
                for (Collection<? extends HintDescription> v : allHints.values()) {
                    hints.addAll(v);
                }
                final Map<String, String> id2DisplayName = Utils.computeId2DisplayName(hints);
                final Map<HintMetadata, ? extends Collection<? extends HintDescription>> hardcodedHints = RulesManager.getInstance().readHints(null, null, new AtomicBoolean());
                final HintsSettings settings;
                String configurationFileLoc = processingEnv.getOptions().get(CONFIGURATION_OPTION);
                File configurationFile = configurationFileLoc != null ? new File(configurationFileLoc) : null;

                if (configurationFile == null || !configurationFile.canRead()) {
                    settings = new HintsSettings() {

                        @Override
                        public boolean isEnabled(HintMetadata hm) {
                            return !hardcodedHints.containsKey(hm) ? hm.enabled : false;
                        }

                        @Override
                        public void setEnabled(HintMetadata hm, boolean bln) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public Preferences getHintPreferences(HintMetadata hm) {
                            return new DummyPreferences(null, "");
                        }

                        @Override
                        public Severity getSeverity(HintMetadata hm) {
                            return hm.severity;
                        }

                        @Override
                        public void setSeverity(HintMetadata hm, Severity svrt) {
                            throw new UnsupportedOperationException();
                        }
                    };
                } else {
                    settings = HintsSettings.createPreferencesBasedHintsSettings(ToolPreferences.from(configurationFile.toURI()).getPreferences("hints", "text/x-java"), true, null);
                }

                ClasspathInfo cpInfo = ClasspathInfo.create(bootCP, compileCP, sourceCP);

                JavaSource.create(cpInfo, sourceFiles.keySet()).runUserActionTask(new Task<CompilationController>() {

                    @Override
                    public void run(CompilationController parameter) throws Exception {
                        if (parameter.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
                            return;
                        }

                        List<ErrorDescription> eds = new HintsInvoker(settings, /*XXX*/new AtomicBoolean()).computeHints(parameter);

                        if (eds != null) {
                            for (ErrorDescription ed : eds) {
                                String outtermost = sourceFiles.get(ed.getFile());
                                TypeElement type = processingEnv.getElementUtils().getTypeElement(outtermost); //XXX: package-info!!!
                                TreePath typePath = trees.getPath(type);
                                TreePath posPath = pathFor(typePath.getCompilationUnit(), trees.getSourcePositions(), ed.getRange().getBegin().getOffset());
                                String category = Utils.categoryName(ed.getId(), id2DisplayName);
                                Kind diagKind;
                                switch (ed.getSeverity()) {
                                    case ERROR: diagKind = Kind.ERROR; break;
                                    case VERIFIER:
                                    case WARNING: diagKind = Kind.WARNING; break;
                                    case HINT:
                                    default: diagKind = Kind.NOTE; break;
                                }
                                trees.printMessage(diagKind, category + ed.getDescription(), posPath.getLeaf(), posPath.getCompilationUnit());
                            }
                        }
                    }
                }, true);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | IOException ex) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Unexpected exception: " + ex.getMessage());
                Logger.getLogger(ProcessorImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return false;
    }

    private static ClassPath toClassPath(Iterable<? extends File> files) throws MalformedURLException {
        Collection<URL> roots = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                roots.add(FileUtil.urlForArchiveOrDir(FileUtil.normalizeFile(f)));
            }
        }

        return ClassPathSupport.createClassPath(roots.toArray(new URL[0]));
    }

    private void setupCache() throws IOException {
        File tmp = File.createTempFile("jackpot30", null);

        tmp.delete();
        tmp.mkdirs();
        tmp.deleteOnExit();

        tmp = FileUtil.normalizeFile(tmp);
        FileUtil.refreshFor(tmp.getParentFile());

        org.openide.filesystems.FileObject tmpFO = FileUtil.toFileObject(tmp);

        if (tmpFO != null) {
            CacheFolder.setCacheFolder(tmpFO);
        }
    }

    private TypeElement outtermostType(Element el) {
        while (el.getEnclosingElement().getKind() != ElementKind.PACKAGE) { //XXX: package-info!
            el = el.getEnclosingElement();
        }
        return (TypeElement) el;
    }

    private TreePath pathFor(final CompilationUnitTree cut, final SourcePositions sp, final long pos) {
        class Result extends RuntimeException {
            final TreePath result;
            public Result(TreePath result) {
                this.result = result;
            }
        }
        try {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void scan(Tree tree, Void p) {
                    long s = sp.getStartPosition(cut, tree);
                    long e = sp.getEndPosition(cut, tree);
                    if (s <= pos && pos <= e) {
                        super.scan(tree, p);
                        throw new Result(getCurrentPath());
                    }
                    return null;
                }
            }.scan(cut, null);
        } catch (Result r) {
            return r.result;
        }
        return null;
    }

    private ClassPath inferSourcePath() {
        if (sources.isEmpty())
            return ClassPath.EMPTY;
        Entry<URL, String> e = sources.entrySet().iterator().next();
        FileObject sourceRoot = URLMapper.findFileObject(e.getKey());
        if (sourceRoot == null) {
            //unexpected
            return ClassPath.EMPTY;
        }
        for (String part : e.getValue().split("\\.")) {
            sourceRoot = sourceRoot.getParent();
        }
        return ClassPathSupport.createClassPath(sourceRoot);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    static {
        ClassLoader origContext = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ProcessorImpl.class.getClassLoader());
            Lookup.getDefault();
        } finally {
            Thread.currentThread().setContextClassLoader(origContext);
        }
    }

    private static final class DummyPreferences extends AbstractPreferences {

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, DummyPreferences> subNodes = new HashMap<>();

        public DummyPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            ((DummyPreferences) parent()).subNodes.remove(name());
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return subNodes.keySet().toArray(new String[0]);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            DummyPreferences n = subNodes.get(name);
            if (n == null) {
                subNodes.put(name, n = new DummyPreferences(this, name));
            }
            return n;
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }

    }
}
