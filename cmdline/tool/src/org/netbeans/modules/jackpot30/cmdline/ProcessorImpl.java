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
package org.netbeans.modules.jackpot30.cmdline;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.java.hints.Hint;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({"hintsConfiguration", "disableJackpotProcessor"})
public class ProcessorImpl extends AbstractProcessor {

    public static final String CONFIGURATION_OPTION = "hintsConfiguration";
    private final Map<URL, CompilationUnitTree> sources = new HashMap<>();
    private static final Logger TOP_LOGGER = Logger.getLogger("");

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if ("true".equals(processingEnv.getOptions().get("disableJackpotProcessor")))
            return false;
        if (!roundEnv.processingOver()) {
            Trees trees = Trees.instance(processingEnv);
            for (Element root : roundEnv.getRootElements()) {
                TypeElement outtermost = outtermostType(root);
                TreePath path = trees.getPath(outtermost);
                if (path == null) {
                    //TODO: log
                    continue;
                }
                try {
                    sources.put(path.getCompilationUnit().getSourceFile().toUri().toURL(), path.getCompilationUnit());
                } catch (MalformedURLException ex) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Unexpected exception: " + ex.getMessage());
                    Logger.getLogger(ProcessorImpl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } else {
            JavacTask.instance(processingEnv).addTaskListener(new TaskListener() {
                @Override
                public void started(TaskEvent e) {}
                @Override
                public void finished(TaskEvent evt) {
                    if (evt.getKind() == TaskEvent.Kind.ENTER) {
                        runHints();
                    }
                }

            });
        }

        return false;
    }

    private void runHints() {
        Utils.addExports();

        Trees trees = Trees.instance(processingEnv);
        Level originalLoggerLevel = TOP_LOGGER.getLevel();
        Path toDelete = null;
        try {
            TOP_LOGGER.setLevel(Level.OFF);
            System.setProperty("RepositoryUpdate.increasedLogLevel", "OFF");
            Method getContext = processingEnv.getClass().getDeclaredMethod("getContext");
            Object context = getContext.invoke(processingEnv);
            Method get = context.getClass().getDeclaredMethod("get", Class.class);
            JavaFileManager fileManager = (JavaFileManager) get.invoke(context, JavaFileManager.class);

            if (!(fileManager instanceof StandardJavaFileManager)) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "The file manager is not a StandardJavaFileManager, cannot run Jackpot 3.0.");
                return ;
            }

            setupCache();

            StandardJavaFileManager sfm = (StandardJavaFileManager) fileManager;

            ClassPath bootCP = /*XXX*/Utils.createDefaultBootClassPath();//toClassPath(sfm.getLocation(StandardLocation.PLATFORM_CLASS_PATH));
            ClassPath compileCP = toClassPath(sfm.getLocation(StandardLocation.CLASS_PATH));
            Iterable<? extends File> sourcePathLocation = sfm.getLocation(StandardLocation.SOURCE_PATH);
            ClassPath sourceCP = sourcePathLocation != null ? toClassPath(sourcePathLocation) : inferSourcePath();

            final Map<FileObject, CompilationUnitTree> sourceFiles = new HashMap<>();

            for (Entry<URL, CompilationUnitTree> e : sources.entrySet()) {
                FileObject fo = URLMapper.findFileObject(e.getKey());
                if (fo == null) {
                    //XXX:
                    return ;
                }
                sourceFiles.put(fo, e.getValue());
            }

            URI settingsURI;
            String configurationFileLoc = processingEnv.getOptions().get(CONFIGURATION_OPTION);
            File configurationFile = configurationFileLoc != null ? new File(configurationFileLoc) : null;

            if (configurationFile == null || !configurationFile.canRead()) {
                URL cfg = ProcessorImpl.class.getResource("/org/netbeans/modules/jackpot30/cmdline/cfg_hints.xml");
                Path tmp = Files.createTempFile("cfg_hints", "xml"); //TODO: delete
                try (InputStream cfgIn = cfg.openStream();
                     OutputStream out = Files.newOutputStream(tmp)) {
                    int read;
                    while ((read = cfgIn.read()) != (-1))
                        out.write(read);
                }
                settingsURI = tmp.toUri();
                toDelete = tmp;
            } else {
                settingsURI = configurationFile.toURI();
            }

            HintsSettings settings = HintsSettings.createPreferencesBasedHintsSettings(ToolPreferences.from(settingsURI).getPreferences("hints", "text/x-java"), true, null);

            final Map<HintMetadata, ? extends Collection<? extends HintDescription>> allHints;
            java.io.PrintStream oldErr = System.err;
            try {
                //XXX: TreeUtilities.unenter prints exceptions to stderr on JDK 11, throw the output away:
                System.setErr(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
                allHints = RulesManager.getInstance().readHints(null, Arrays.asList(bootCP, compileCP, sourceCP), new AtomicBoolean());
            } finally {
                System.setErr(oldErr);
            }
            List<HintDescription> hints = new ArrayList<>();

            for (Entry<HintMetadata, ? extends Collection<? extends HintDescription>> e : allHints.entrySet()) {
                if (settings.isEnabled(e.getKey()) && e.getKey().kind == Hint.Kind.INSPECTION && !e.getKey().options.contains(HintMetadata.Options.NO_BATCH)) {
                    hints.addAll(e.getValue());
                }
            }
            final Map<String, String> id2DisplayName = Utils.computeId2DisplayName(hints);
            ClasspathInfo cpInfo = new ClasspathInfo.Builder(bootCP).setClassPath(compileCP).setSourcePath(sourceCP).setModuleBootPath(bootCP).build();

            JavaSource.create(cpInfo, sourceFiles.keySet()).runUserActionTask(new Task<CompilationController>() {

                @Override
                public void run(CompilationController parameter) throws Exception {
                    if (parameter.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
                        return;
                    }

                    List<ErrorDescription> eds = new HintsInvoker(settings, /*XXX*/new AtomicBoolean()).computeHints(parameter, hints);

                    if (eds != null) {
                        //TODO: sort errors!!!
                        for (ErrorDescription ed : eds) {
                            CompilationUnitTree originalUnit = sourceFiles.get(ed.getFile());
                            if (originalUnit == null) {
                                //XXX: log properly!!!
                                continue;
                            }
                            TreePath posPath = pathFor(originalUnit, trees.getSourcePositions(), ed.getRange().getBegin().getOffset());
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
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | IOException ex) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Unexpected exception: " + ex.getMessage());
            Logger.getLogger(ProcessorImpl.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            TOP_LOGGER.setLevel(originalLoggerLevel);
            if (toDelete != null) {
                try {
                    Files.delete(toDelete);
                } catch (IOException ex) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Unexpected exception: " + ex.getMessage());
                    Logger.getLogger(ProcessorImpl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
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
        while (/*XXX: package/module-info!*/el.getEnclosingElement() != null && el.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            el = el.getEnclosingElement();
        }
        if (el.getKind() == ElementKind.PACKAGE || el.getKind().name().equals("MODULE"))
            return null;
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
        Entry<URL, CompilationUnitTree> e = sources.entrySet().iterator().next();
        FileObject sourceRoot = URLMapper.findFileObject(e.getKey());
        if (sourceRoot == null) {
            //unexpected
            return ClassPath.EMPTY;
        }
        sourceRoot = sourceRoot.getParent();
        for (String part : e.getValue().getPackageName().toString().split("\\.")) {
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

    private static final class ModuleAndClass {
        public final String module;
        public final String name;
        public ModuleAndClass(String module, String name) {
            this.module = module;
            this.name = name;
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
