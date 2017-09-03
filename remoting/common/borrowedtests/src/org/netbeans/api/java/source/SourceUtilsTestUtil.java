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

import com.sun.source.util.JavacTask;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;
import javax.swing.event.ChangeListener;
import junit.framework.Assert;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.java.JavaDataLoader;
import org.netbeans.modules.java.source.indexing.JavaCustomIndexer;
import org.netbeans.modules.java.source.parsing.JavacParser;
import org.netbeans.modules.java.source.parsing.JavacParserFactory;
import org.netbeans.modules.java.source.usages.IndexUtil;
import org.netbeans.modules.parsing.api.indexing.IndexingManager;
import org.netbeans.modules.parsing.impl.indexing.MimeTypes;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.LocalFileSystem;
import org.openide.filesystems.MIMEResolver;
import org.openide.filesystems.MultiFileSystem;
import org.openide.filesystems.Repository;
import org.openide.filesystems.URLMapper;
import org.openide.filesystems.XMLFileSystem;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;
import org.xml.sax.SAXException;

/**
 *
 * @author Jan Lahoda
 */
public final class SourceUtilsTestUtil extends ProxyLookup {
    
    private static SourceUtilsTestUtil DEFAULT_LOOKUP = null;
    
    public SourceUtilsTestUtil() {
//        Assert.assertNull(DEFAULT_LOOKUP);
        DEFAULT_LOOKUP = this;
    }
    
    /**
     * Set the global default lookup with some fixed instances including META-INF/services/*.
     */
    /**
     * Set the global default lookup with some fixed instances including META-INF/services/*.
     */
    public static void setLookup(Object[] instances, ClassLoader cl) {
        DEFAULT_LOOKUP.setLookups(new Lookup[] {
            Lookups.fixed(instances),
            Lookups.metaInfServices(cl),
            Lookups.singleton(cl),
        });
    }
    
    private static Object[] extraLookupContent = null;
    
    public static void prepareTest(String[] additionalLayers, Object[] additionalLookupContent) throws IOException, SAXException, PropertyVetoException {
        List<URL> layers = new LinkedList<URL>();
        
        for (int cntr = 0; cntr < additionalLayers.length; cntr++) {
            boolean found = false;

            for (Enumeration<URL> en = Thread.currentThread().getContextClassLoader().getResources(additionalLayers[cntr]); en.hasMoreElements(); ) {
                found = true;
                layers.add(en.nextElement());
            }

            Assert.assertTrue(additionalLayers[cntr], found);
        }
        
        XMLFileSystem xmlFS = new XMLFileSystem();
        xmlFS.setXmlUrls(layers.toArray(new URL[0]));
        
        FileSystem system = new MultiFileSystem(new FileSystem[] {FileUtil.createMemoryFileSystem(), xmlFS});
        
        Repository repository = new Repository(system);
        extraLookupContent = new Object[additionalLookupContent.length + 1];
        
        System.arraycopy(additionalLookupContent, 0, extraLookupContent, 1, additionalLookupContent.length);
        
        extraLookupContent[0] = repository;
        
        SourceUtilsTestUtil.setLookup(extraLookupContent, SourceUtilsTestUtil.class.getClassLoader());
        
        SourceUtilsTestUtil2.disableLocks();

        Set<String> mimeTypes = MimeTypes.getAllMimeTypes();

        if (mimeTypes == null) {
            mimeTypes = new HashSet<String>();
        } else {
            mimeTypes = new HashSet<String>(mimeTypes);
        }

        mimeTypes.add("text/x-java");

        MimeTypes.setAllMimeTypes(mimeTypes);
        org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
    }
    
    static {
        SourceUtilsTestUtil.class.getClassLoader().setDefaultAssertionStatus(true);
        System.setProperty("org.openide.util.Lookup", SourceUtilsTestUtil.class.getName());
        Assert.assertEquals(SourceUtilsTestUtil.class, Lookup.getDefault().getClass());
    }
    
    public static void prepareTest(FileObject sourceRoot, FileObject buildRoot, FileObject cache) throws Exception {
        prepareTest(sourceRoot, buildRoot, cache, new FileObject[0]);
    }
    
    public static void prepareTest(FileObject sourceRoot, FileObject buildRoot, FileObject cache, FileObject[] classPathElements) throws Exception {
        if (extraLookupContent == null)
            prepareTest(new String[0], new Object[0]);
        
        Object[] lookupContent = new Object[extraLookupContent.length + 4];
        
        System.arraycopy(extraLookupContent, 0, lookupContent, 4, extraLookupContent.length);
        
        lookupContent[0] = new TestProxyClassPathProvider(sourceRoot, buildRoot, classPathElements);
        lookupContent[1] = new TestSourceForBinaryQuery(sourceRoot, buildRoot);
        lookupContent[2] = new TestSourceLevelQueryImplementation();
        lookupContent[3] = JavaDataLoader.getLoader(JavaDataLoader.class);
        
        setLookup(lookupContent, SourceUtilsTestUtil.class.getClassLoader());

        IndexUtil.setCacheFolder(FileUtil.toFile(cache));
    }

    private static Map<FileObject,  String> file2SourceLevel = new WeakHashMap<FileObject, String>();
    
    public static void setSourceLevel(FileObject file, String level) {
        file2SourceLevel.put(file, level);
    }

    /**This method assures that all java classes under sourceRoot are compiled,
     * and the caches are created for them.
     */
    public static void compileRecursively(FileObject sourceRoot) throws Exception {
        IndexingManager.getDefault().refreshIndexAndWait(sourceRoot.getURL(), null);
    }

    private static List<URL> bootClassPath;

    private static Logger log = Logger.getLogger(SourceUtilsTestUtil.class.getName());
    
    public static synchronized List<URL> getBootClassPath() {
        if (bootClassPath == null) {
            try {
                String cp = System.getProperty("sun.boot.class.path");
                List<URL> urls = new ArrayList<URL>();
                String[] paths = cp.split(Pattern.quote(System.getProperty("path.separator")));
                
                for (String path : paths) {
                    File f = new File(path);
                    
                    if (!f.canRead())
                        continue;
                    
                    FileObject fo = FileUtil.toFileObject(f);
                    
                    if (FileUtil.isArchiveFile(fo)) {
                        fo = FileUtil.getArchiveRoot(fo);
                    }
                    
                    if (fo != null) {
                        urls.add(fo.getURL());
                    }
                }
                
                bootClassPath = urls;
            } catch (FileStateInvalidException e) {
                if (log.isLoggable(Level.SEVERE))
                    log.log(Level.SEVERE, e.getMessage(), e);
            }
        }

        return bootClassPath;
    }

    private static class TestSourceForBinaryQuery implements SourceForBinaryQueryImplementation {
        
        private final FileObject sourceRoot;
        private final FileObject buildRoot;
        
        public TestSourceForBinaryQuery(FileObject sourceRoot, FileObject buildRoot) {
            this.sourceRoot = sourceRoot;
            this.buildRoot = buildRoot;
        }
        
        public SourceForBinaryQuery.Result findSourceRoots(URL binaryRoot) {
            FileObject f = URLMapper.findFileObject(binaryRoot);

            if (buildRoot.equals(f)) {
                return new SourceForBinaryQuery.Result() {
                    public FileObject[] getRoots() {
                        return new FileObject[] {
                            sourceRoot,
                        };
                    }

                    public void addChangeListener(ChangeListener l) {
                    }

                    public void removeChangeListener(ChangeListener l) {
                    }
                };
            }

            return null;
        }
        
    }
    
    private static class TestProxyClassPathProvider implements ClassPathProvider {
        
        private FileObject sourceRoot;
        private FileObject buildRoot;
        private FileObject[] classPathElements;
        
        public TestProxyClassPathProvider(FileObject sourceRoot, FileObject buildRoot, FileObject[] classPathElements) {
            this.sourceRoot = sourceRoot;
            this.buildRoot = buildRoot;
            this.classPathElements = classPathElements;
        }
        
        public ClassPath findClassPath(FileObject file, String type) {
            try {
            if (ClassPath.BOOT == type) {
                return ClassPathSupport.createClassPath(getBootClassPath().toArray(new URL[0]));
            }
            
            if (ClassPath.SOURCE == type) {
                return ClassPathSupport.createClassPath(new FileObject[] {
                    sourceRoot
                });
            }
            
            if (ClassPath.COMPILE == type) {
                return ClassPathSupport.createClassPath(classPathElements);
            }
            
            if (ClassPath.EXECUTE == type) {
                return ClassPathSupport.createClassPath(new FileObject[] {
                    buildRoot
                });
            }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
        
    }

    public static class TestSourceLevelQueryImplementation implements SourceLevelQueryImplementation {
        
        public String getSourceLevel(FileObject javaFile) {
            String level = file2SourceLevel.get(javaFile);
            
            if (level == null) {
                if (javaFile.isFolder()) {
                    for (FileObject data : file2SourceLevel.keySet()) {
                        if (FileUtil.isParentOf(javaFile, data)) {
                            return file2SourceLevel.get(data);
                        }
                    }
                }
                return "1.5";
            } else
                return level;
        }
        
    }

    /**Copied from org.netbeans.api.project.
     * Create a scratch directory for tests.
     * Will be in /tmp or whatever, and will be empty.
     * If you just need a java.io.File use clearWorkDir + getWorkDir.
     */
    public static FileObject makeScratchDir(NbTestCase test) throws IOException {
        test.clearWorkDir();
        File root = test.getWorkDir();
        assert root.isDirectory() && root.list().length == 0;
        FileObject fo = FileUtil.toFileObject(root);
        if (fo != null) {
            // Presumably using masterfs.
            return fo;
        } else {
            // For the benefit of those not using masterfs.
            LocalFileSystem lfs = new LocalFileSystem();
            try {
                lfs.setRootDirectory(root);
            } catch (PropertyVetoException e) {
                assert false : e;
            }
            Repository.getDefault().addFileSystem(lfs);
            return lfs.getRoot();
        }
    }
    
    public static JavacTask getJavacTaskFor(CompilationInfo info) {
        return info.impl.getJavacTask();
    }
    
    /** Blocking call for CompilationInfo after given phase is reached.
     *  @param phase to be reached
     *  @return CompilationInfo or null
     *  XXX: REMOVE ME!!!!!!!
     */
    public static CompilationInfo getCompilationInfo(JavaSource js, Phase phase ) throws IOException {        
        if (phase == null || phase == Phase.MODIFIED) { 
            throw new IllegalArgumentException (String.format("The %s is not a legal value of phase",phase));   //NOI18N
        }
        final DeadlockTask bt = new DeadlockTask(phase);
        js.runUserActionTask(bt,true);
        return bt.info;
    }
    
    
    private static class DeadlockTask implements Task<CompilationController> {
        
        private final Phase phase;
        private CompilationInfo info;
        
        public DeadlockTask(Phase phase) {
            assert phase != null;
            this.phase = phase;
        }
        
        public void run( CompilationController info ) {
            try {
                info.toPhase(this.phase);
                this.info = info;
            } catch (IOException ioe) {
                if (log.isLoggable(Level.SEVERE))
                    log.log(Level.SEVERE, ioe.getMessage(), ioe);
            }
        }                
        
    }

    @ServiceProvider(service=MimeDataProvider.class)
    public static final class JavacParserProvider implements MimeDataProvider {

        private Lookup javaLookup = Lookups.fixed(new JavacParserFactory(), new JavaCustomIndexer.Factory());

        public Lookup getLookup(MimePath mimePath) {
            if (mimePath.getPath().endsWith(JavacParser.MIME_TYPE)) {
                return javaLookup;
            }

            return Lookup.EMPTY;
        }
        
    }

    @ServiceProvider(service=MIMEResolver.class)
    public static final class JavaMimeResolver extends MIMEResolver {

        public JavaMimeResolver() {
            super(JavacParser.MIME_TYPE);
        }

        @Override
        public String findMIMEType(FileObject fo) {
            if ("java".equals(fo.getExt())) {
                return JavacParser.MIME_TYPE;
            }

            return null;
        }

    }
    
}
