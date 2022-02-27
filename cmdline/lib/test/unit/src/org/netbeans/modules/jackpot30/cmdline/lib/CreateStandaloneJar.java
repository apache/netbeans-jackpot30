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
package org.netbeans.modules.jackpot30.cmdline.lib;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.apache.lucene.analysis.tokenattributes.OffsetAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.TermAttributeImpl;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.classfile.ClassFile;
import org.netbeans.modules.classfile.ClassName;
import org.netbeans.modules.editor.mimelookup.MimeLookupCacheSPI;
import org.netbeans.modules.editor.mimelookup.SharedMimeLookupCache;
import org.netbeans.modules.editor.tools.storage.api.ToolPreferences;
import org.netbeans.modules.jackpot30.cmdline.lib.StandaloneTools.ActiveDocumentProviderImpl;
import org.netbeans.modules.jackpot30.cmdline.lib.StandaloneTools.EditorMimeTypesImplementationImpl;
import org.netbeans.modules.jackpot30.cmdline.lib.StandaloneTools.EntityCatalogImpl;
import org.netbeans.modules.jackpot30.cmdline.lib.StandaloneTools.JavaMimeResolver;
import org.netbeans.modules.jackpot30.cmdline.lib.StandaloneTools.RepositoryImpl;
import org.netbeans.modules.java.classpath.DefaultGlobalPathRegistryImplementation;
import org.netbeans.modules.java.hints.StandardJavacWarnings.CompilerSettingsImpl;
import org.netbeans.modules.java.hints.declarative.DeclarativeHintRegistry;
import org.netbeans.modules.java.hints.providers.code.CodeHintProviderImpl;
import org.netbeans.modules.java.hints.providers.code.FSWrapper;
import org.netbeans.modules.java.hints.providers.code.FSWrapper.MethodWrapper;
import org.netbeans.modules.java.hints.providers.spi.ClassPathBasedHintProvider;
import org.netbeans.modules.java.hints.providers.spi.HintProvider;
import org.netbeans.modules.java.hints.spiimpl.RulesManager;
import org.netbeans.modules.java.hints.spiimpl.RulesManagerImpl;
import org.netbeans.modules.java.hints.spiimpl.Utilities.SPI;
import org.netbeans.modules.java.j2seplatform.platformdefinition.jrtfs.NBJRTURLMapper;
import org.netbeans.modules.java.j2seplatform.platformdefinition.jrtfs.NBJRTURLStreamHandler;
import org.netbeans.modules.java.source.DefaultPositionRefProvider;
import org.netbeans.modules.java.source.PositionRefProvider;
import org.netbeans.modules.java.source.indexing.JavaCustomIndexer.CompileWorkerProvider;
import org.netbeans.modules.java.source.indexing.JavaCustomIndexer.DefaultCompileWorkerProvider;
import org.netbeans.modules.java.source.parsing.JavacParser;
import org.netbeans.modules.java.source.parsing.JavacParser.ContextEnhancer;
import org.netbeans.modules.java.source.parsing.JavacParser.VanillaJavacContextEnhancer;
import org.netbeans.modules.java.source.parsing.ModuleOraculum;
import org.netbeans.modules.java.source.tasklist.CompilerSettings;
import org.netbeans.modules.openide.util.DefaultMutexImplementation;
import org.netbeans.modules.openide.util.NbMutexEventProvider;
import org.netbeans.modules.parsing.impl.IndexerBridge;
import org.netbeans.modules.parsing.impl.indexing.IndexerControl;
import org.netbeans.modules.parsing.impl.indexing.implspi.ActiveDocumentProvider;
import org.netbeans.modules.parsing.implspi.EnvironmentFactory;
import org.netbeans.modules.parsing.nb.DataObjectEnvFactory;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.editor.document.EditorMimeTypesImplementation;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.netbeans.spi.java.classpath.GlobalPathRegistryImplementation;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.queries.CompilerOptionsQueryImplementation;
import org.netbeans.spi.project.ProjectManagerImplementation;
import org.openide.filesystems.MIMEResolver;
import org.openide.filesystems.Repository;
import org.openide.util.NbCollections;
import org.openide.util.NbPreferences.Provider;
import org.openide.util.spi.MutexEventProvider;
import org.openide.util.spi.MutexImplementation;
import org.openide.xml.EntityCatalog;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author lahvac
 */
public abstract class CreateStandaloneJar extends NbTestCase {
    private final String toolName;

    public CreateStandaloneJar(String name, String toolName) {
        super(name);
        this.toolName = toolName;
    }

    public void testDumpImportantHack() throws Exception {
        String targetDir = System.getProperty("outputDir", System.getProperty("java.io.tmpdir"));
        String targetName = System.getProperty("targetName", toolName + ".jar");

        createCompiler(new File(targetDir, targetName), new File(targetDir, "hints"));
    }

    protected abstract Info computeInfo();

    public void createCompiler(File targetCompilerFile, File targetHintsFile) throws Exception {
        Map<String, byte[]> out = new TreeMap<>();
        List<String> toProcess = new LinkedList<String>(INCLUDE);

        for (FSWrapper.ClassWrapper cw : FSWrapper.listClasses()) {
            toProcess.add(cw.getName());

            //XXX: CodeHintProviderImpl currently resolves customizer providers eagerly,
            //so need to copy them as well, even though these won't be normally used:
            Hint ch = cw.getAnnotation(Hint.class);

            if (ch != null) {
                toProcess.add(ch.customizerProvider().getName());
            }

            for (MethodWrapper mw : cw.getMethods()) {
                Hint mh = mw.getAnnotation(Hint.class);

                if (mh != null) {
                    toProcess.add(mh.customizerProvider().getName());
                }
            }
        }

        Info info = computeInfo();

        toProcess.addAll(info.additionalRoots);

        Set<String> done = new HashSet<String>();
        Set<String> bundlesToCopy = new HashSet<String>();

        OUTER: while (!toProcess.isEmpty()) {
            String fqn = toProcess.remove(0);

            if (!done.add(fqn)) {
                continue;
            }

            if (ALLOWED_DOMAINS.stream().noneMatch(fqn::startsWith)) {
                continue;
            }

            for (Pattern p : info.exclude) {
                if (p.matcher(fqn).matches()) continue OUTER;
            }

//            System.err.println("processing: " + fqn);

            String fileName = fqn.replace('.', '/') + ".class";
            URL url = this.getClass().getClassLoader().getResource(fileName);

            if (url == null) {
                //probably array:
                continue;
            }

            byte[] bytes = readFile(url);

            ClassFile cf = new ClassFile(new ByteArrayInputStream(bytes));

            for (ClassName classFromCP : cf.getConstantPool().getAllClassNames()) {
                toProcess.add(classFromCP.getInternalName().replace('/', '.'));
            }

            out.put(escapeJavaxLang(info, fileName), escapeJavaxLang(info, bytes));

            if (COPY_REGISTRATION.contains(fqn) || info.copyMetaInfRegistration.contains(fqn)) {
                String serviceName = "META-INF/services/" + fqn;
                Enumeration<URL> resources = this.getClass().getClassLoader().getResources(serviceName);

                if (resources.hasMoreElements()) {
                    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                        while (resources.hasMoreElements()) {
                            URL res = resources.nextElement();

                            buffer.write(readFile(res));
                        }

                        out.put(escapeJavaxLang(info, serviceName), buffer.toByteArray());
                    }

                }
            }

            int lastSlash = fileName.lastIndexOf('/');

            if (lastSlash > 0) {
                bundlesToCopy.add(fileName.substring(0, lastSlash + 1) + "Bundle.properties");
            }
        }

        bundlesToCopy.addAll(RESOURCES);
        bundlesToCopy.addAll(info.additionalResources);
        copyResources(out, info, bundlesToCopy);

        //generated-layer.xml:
        List<URL> layers2Merge = new ArrayList<URL>();
        List<String> layerNames = new ArrayList<String>();

        layerNames.add("META-INF/generated-layer.xml");
        layerNames.addAll(info.additionalLayers);

        for (String layerName : layerNames) {
            for (URL layerURL : NbCollections.iterable(this.getClass().getClassLoader().getResources(layerName))) {
                layers2Merge.add(layerURL);
            }
        }

        Document main = null;

        for (URL res : layers2Merge) {
            Document current = XMLUtil.parse(new InputSource(res.openStream()), false, false, null, new EntityCatalog() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                    return new InputSource(CreateStandaloneJar.class.getResourceAsStream("/org/openide/filesystems/filesystem1_2.dtd"));
                }
            });

            if (main == null) {
                main = current;
            } else {
                NodeList children = current.getDocumentElement().getChildNodes();

                for (int i = 0; i < children.getLength(); i++) {
                    main.getDocumentElement().appendChild(main.importNode(children.item(i), true));
                }
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        XMLUtil.write(main, bytes, "UTF-8");
        bytes.close();
        out.put(escapeJavaxLang(info, "META-INF/generated-layer.xml"), escapeJavaxLang(info, bytes.toByteArray()));

        List<MetaInfRegistration> registrations = new ArrayList<MetaInfRegistration>();

        registrations.add(new MetaInfRegistration("java.lang.SecurityManager", "org.netbeans.modules.masterfs.filebasedfs.utils.FileChangedManager"));
        registrations.add(new MetaInfRegistration("org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation", StandaloneTools.EmptySourceForBinaryQueryImpl.class.getName(), 0));
        registrations.add(new MetaInfRegistration(Provider.class.getName(), StandaloneTools.PreferencesProvider.class.getName(), 0));
        registrations.add(new MetaInfRegistration(MimeDataProvider.class.getName(), StandaloneTools.StandaloneMimeDataProviderImpl.class.getName()));
        registrations.add(new MetaInfRegistration(SPI.class.getName(), StandaloneTools.UtilitiesSPIImpl.class.getName()));
        registrations.add(new MetaInfRegistration(MIMEResolver.class.getName(), JavaMimeResolver.class.getName()));
        registrations.add(new MetaInfRegistration(ActiveDocumentProvider.class.getName(), ActiveDocumentProviderImpl.class.getName()));
        registrations.add(new MetaInfRegistration(EditorMimeTypesImplementation.class.getName(), EditorMimeTypesImplementationImpl.class.getName()));
        registrations.add(new MetaInfRegistration(PositionRefProvider.Factory.class.getName(), DefaultPositionRefProvider.FactoryImpl.class.getName()));
        registrations.add(new MetaInfRegistration(CompilerSettings.class.getName(), CompilerSettingsImpl.class.getName()));
        registrations.add(new MetaInfRegistration(MutexImplementation.class.getName(), DefaultMutexImplementation.class.getName()));
        registrations.add(new MetaInfRegistration(MutexEventProvider.class.getName(), NbMutexEventProvider.class.getName()));
        registrations.add(new MetaInfRegistration(CompilerOptionsQueryImplementation.class.getName(), ModuleOraculum.class.getName()));
        registrations.add(new MetaInfRegistration(URLStreamHandlerFactory.class.getName(), NBJRTURLStreamHandler.FactoryImpl.class.getName()));
        registrations.add(new MetaInfRegistration(ContextEnhancer.class.getName(), JavacParser.VanillaJavacContextEnhancer.class.getName()));
        registrations.add(new MetaInfRegistration(Repository.class.getName(), RepositoryImpl.class.getName()));
        registrations.add(new MetaInfRegistration(RulesManager.class.getName(), RulesManagerImpl.class.getName()));
        registrations.add(new MetaInfRegistration(EntityCatalog.class.getName(), EntityCatalogImpl.class.getName()));
        registrations.add(new MetaInfRegistration(CompileWorkerProvider.class.getName(), DefaultCompileWorkerProvider.class.getName()));

        registrations.addAll(info.metaInf);

        Map<String, Collection<MetaInfRegistration>> api2Registrations = new HashMap<String, Collection<MetaInfRegistration>>();

        for (MetaInfRegistration r : registrations) {
            Collection<MetaInfRegistration> regs = api2Registrations.get(r.apiClassName);

            if (regs == null) {
                api2Registrations.put(r.apiClassName, regs = new ArrayList<MetaInfRegistration>());
            }

            regs.add(r);
        }

        for (Entry<String, Collection<MetaInfRegistration>> e : api2Registrations.entrySet()) {
            addMETA_INFRegistration(out, info, e.getValue());
        }

        try (JarOutputStream outStream = new JarOutputStream(new FileOutputStream(targetCompilerFile))) {
            Set<String> seenDirs = new HashSet<>();
            for (Entry<String, byte[]> e : out.entrySet()) {
                String[] parts = e.getKey().split("/");
                StringBuilder dir = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    dir.append(parts[i]);
                    dir.append("/");
                    if (seenDirs.add(dir.toString()))
                        outStream.putNextEntry(new ZipEntry(dir.toString()));
                }
                outStream.putNextEntry(new ZipEntry(e.getKey()));
                outStream.write(e.getValue());
            }
        }

        Writer hints = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetHintsFile), "UTF-8"));

//        hints.write(DumpHints.dumpHints());

        hints.close();
    }

    public static final class Info {
        private final Set<String> additionalRoots = new HashSet<String>();
        private final Set<String> additionalResources = new HashSet<String>();
        private final Set<String> additionalLayers = new HashSet<String>();
        private final List<MetaInfRegistration> metaInf = new LinkedList<MetaInfRegistration>();
        private final Set<String> copyMetaInfRegistration = new HashSet<String>();
        private       boolean escapeJavaxLang;
        private final List<Pattern> exclude = new LinkedList<Pattern>();
        public Info() {}
        public Info addAdditionalRoots(String... fqns) {
            additionalRoots.addAll(Arrays.asList(fqns));
            return this;
        }
        public Info addAdditionalResources(String... paths) {
            additionalResources.addAll(Arrays.asList(paths));
            return this;
        }
        public Info addAdditionalLayers(String... paths) {
            additionalLayers.addAll(Arrays.asList(paths));
            return this;
        }
        public Info addMetaInfRegistrations(MetaInfRegistration... registrations) {
            metaInf.addAll(Arrays.asList(registrations));
            return this;
        }
        public Info addMetaInfRegistrationToCopy(String... registrationsToCopy) {
            copyMetaInfRegistration.addAll(Arrays.asList(registrationsToCopy));
            return this;
        }
        public Info addExcludePattern(Pattern... pattern) {
            exclude.addAll(Arrays.asList(pattern));
            return this;
        }
        public Info setEscapeJavaxLang() {
            this.escapeJavaxLang = true;
            return this;
        }
    }

    public static final class MetaInfRegistration {
        private final String apiClassName;
        private final String implClassName;
        private final Integer pos;

        public MetaInfRegistration(String apiClassName, String implClassName) {
            this(apiClassName, implClassName, null);
        }
        
        public MetaInfRegistration(Class<?> apiClass, Class<?> implClass) {
            this(apiClass.getName(), implClass.getName(), null);
        }

        public MetaInfRegistration(String apiClassName, String implClassName, Integer pos) {
            this.apiClassName = apiClassName;
            this.implClassName = implClassName;
            this.pos = pos;
        }

    }

    private void copyResources(Map<String, byte[]> out, Info info, Set<String> res) throws IOException {
        for (String resource : res) {
            URL url = this.getClass().getClassLoader().getResource(resource);

            if (url == null) {
                continue;
            }
            
            out.put(escapeJavaxLang(info, resource), readFile(url));
        }
    }

    private static byte[] readFile(URL url) throws IOException {
        InputStream ins = url.openStream();
        ByteArrayOutputStream data = new ByteArrayOutputStream();

        try {
            int read;

            while ((read = ins.read()) != (-1)) {
                data.write(read);
            }
        } finally {
            ins.close();
            data.close();
        }

        return data.toByteArray();
    }

    private static void addMETA_INFRegistration(Map<String, byte[]> out, Info info, Iterable<MetaInfRegistration> registrations) throws IOException {
        String apiClassName = registrations.iterator().next().apiClassName;

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            for (MetaInfRegistration r : registrations) {
                assert apiClassName.equals(r.apiClassName);
                buffer.write(r.implClassName.getBytes("UTF-8"));
                buffer.write("\n".getBytes("UTF-8"));
                if (r.pos != null) {
                    buffer.write(("#position=" + r.pos.toString() + "\n").getBytes("UTF-8"));
                }
            }

            out.put(escapeJavaxLang(info, "META-INF/services/" + apiClassName), buffer.toByteArray());
        }
    }

    private static final Map<String, String> replaceWhat2With = new LinkedHashMap<String, String>();

    static {
        replaceWhat2With.put("javax/annotation/processing/", "jpt30/annotation/processing/");
        replaceWhat2With.put("javax/lang/", "jpt30/lang/");
        replaceWhat2With.put("javax/tools/", "jpt30/tools/");
        replaceWhat2With.put("com/sun/tools/", "jpt/sun/tools/");
        replaceWhat2With.put("com/sun/source/", "jpt/sun/source/");

        for (String originalKey : new HashSet<>(replaceWhat2With.keySet())) {
            replaceWhat2With.put(originalKey.replace('/', '.'), replaceWhat2With.get(originalKey).replace('/', '.'));
        }
    }
            

   private static byte[] escapeJavaxLang(Info info, byte[] source) throws UnsupportedEncodingException {
       if (!info.escapeJavaxLang) return source;

       for (Entry<String, String> e  : replaceWhat2With.entrySet()) {
           byte[] replaceSource = e.getKey().getBytes("UTF-8");
           byte[] replaceTarget = e.getValue().getBytes("UTF-8");

           OUTER:
           for (int i = 0; i < source.length - replaceSource.length; i++) {
               for (int j = 0; j < replaceSource.length; j++) {
                   if (source[i + j] != replaceSource[j]) {
                       continue OUTER;
                   }
               }

               for (int j = 0; j < replaceTarget.length; j++) {
                   source[i + j] = replaceTarget[j];
               }

               i += replaceTarget.length - 1;
           }
       }

       return source;
    }

    private static String escapeJavaxLang(Info info, String fileName) throws UnsupportedEncodingException {
       if (!info.escapeJavaxLang) return fileName;

        for (Entry<String, String> e : replaceWhat2With.entrySet()) {
            fileName = fileName.replace(e.getKey(), e.getValue());
        }

        return fileName;
    }



    private static final Set<String> INCLUDE = new HashSet<String>(Arrays.asList(
            StandaloneTools.class.getName(),
            StandaloneTools.EmptySourceForBinaryQueryImpl.class.getName(),
            StandaloneTools.PreferencesProvider.class.getName(),
            StandaloneTools.StandaloneMimeDataProviderImpl.class.getName(),
            CodeHintProviderImpl.class.getName(),
            JavaSource.class.getName(),
            DumpHints.class.getName(),
            RepositoryImpl.class.getName(),
            "org.netbeans.core.startup.layers.ArchiveURLMapper",
            DeclarativeHintRegistry.class.getName(),
            "org.netbeans.core.startup.layers.NbinstURLMapper",
            "org.netbeans.modules.masterfs.MasterURLMapper",
            "org.netbeans.core.NbLoaderPool",
            "org.netbeans.core.startup.preferences.PreferencesProviderImpl",
            "org.netbeans.modules.java.platform.DefaultJavaPlatformProvider",
            RulesManagerImpl.class.getName(),
            
            "com.sun.tools.javac.resources.compiler",
            "com.sun.tools.javac.resources.javac",
            TermAttributeImpl.class.getName(),
            OffsetAttributeImpl.class.getName(),
            PositionIncrementAttributeImpl.class.getName()


            , "org.netbeans.modules.java.hints.legacy.spi.RulesManager$HintProviderImpl"
            ,JavaMimeResolver.class.getName()
            , "org.netbeans.api.java.source.support.OpenedEditors",
            SharedMimeLookupCache.class.getName(),
            DataObjectEnvFactory.class.getName(),
            NbProjectManager.class.getName(),
            DefaultGlobalPathRegistryImplementation.class.getName(),
            DefaultPositionRefProvider.FactoryImpl.class.getName(),
            CompilerSettingsImpl.class.getName(),
            NbMutexEventProvider.class.getName(),
            DefaultMutexImplementation.class.getName(),
            Utils.class.getName(),
            IndexerControl.class.getName(),
            ModuleOraculum.class.getName(),
            ToolPreferences.class.getName(),
            NBJRTURLStreamHandler.FactoryImpl.class.getName(),
            NBJRTURLMapper.class.getName(),
            VanillaJavacContextEnhancer.class.getName(),
            EntityCatalogImpl.class.getName(),
            DefaultCompileWorkerProvider.class.getName()
        ));

    private static final Set<String> COPY_REGISTRATION = new HashSet<String>(Arrays.<String>asList(
            HintProvider.class.getName(),
            "org.openide.filesystems.URLMapper",
            "org.openide.util.Lookup",
            "org.netbeans.modules.openide.util.PreferencesProvider",
            ClassPathBasedHintProvider.class.getName(),
            RulesManager.class.getName(),
            MimeLookupCacheSPI.class.getName(),
            EnvironmentFactory.class.getName(),
            ProjectManagerImplementation.class.getName(),
            GlobalPathRegistryImplementation.class.getName(),
            IndexerBridge.class.getName()
        ));

    private static final Set<String> RESOURCES = new HashSet<String>(Arrays.asList(
        "org/netbeans/modules/java/source/resources/icons/error-badge.gif",
        "org/netbeans/modules/java/source/resources/layer.xml",
        "org/netbeans/modules/java/j2seproject/ui/resources/brokenProjectBadge.gif",
        "org/netbeans/modules/java/j2seproject/ui/resources/compileOnSaveDisabledBadge.gif",
        "org/netbeans/modules/parsing/impl/resources/error-badge.gif",
        "org/netbeans/modules/editor/tools/storage/ToolConfiguration-1_0.dtd"
    ));

    private static final Set<String> ALLOWED_DOMAINS = new HashSet<String>(Arrays.asList(
            "org.netbeans.",
            "org.openide.",
            "org.apache.",
            "net.bytebuddy.",
            "joptsimple.",
            "io.reflectoring.diffparser.",
            "org.slf4j.",
            "com.sun.source.",
            "com.sun.tools.",
            "javax.tools.",
            "javax.annotation.processing.",
            "javax.lang.model.",
            "nbjavac."
    ));
}
