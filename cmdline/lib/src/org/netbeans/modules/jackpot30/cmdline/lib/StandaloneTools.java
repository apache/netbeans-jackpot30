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

import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.modules.java.hints.spiimpl.Utilities.SPI;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings.GlobalSettingsProvider;
import org.netbeans.modules.java.source.indexing.JavaCustomIndexer;
import org.netbeans.modules.java.source.parsing.JavacParser;
import org.netbeans.modules.java.source.parsing.JavacParserFactory;
import org.netbeans.modules.parsing.impl.indexing.implspi.ActiveDocumentProvider;
import org.netbeans.spi.editor.document.EditorMimeTypesImplementation;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation;
import org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation2;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.filesystems.MultiFileSystem;
import org.openide.filesystems.Repository;
import org.openide.filesystems.XMLFileSystem;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.NbPreferences.Provider;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;
import org.openide.xml.EntityCatalog;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**XXX: should not include JavaCustomIndexer for tools that do not strictly require it
 *
 * @author lahvac
 */
public class StandaloneTools {

    @ServiceProvider(service=MimeDataProvider.class)
    public static final class StandaloneMimeDataProviderImpl implements MimeDataProvider {

        private static final Lookup L = Lookups.fixed(NbPreferences.forModule(StandaloneTools.class), new JavacParserFactory(), new JavaCustomIndexer.Factory(), new GlobalSettingsProvider());

        public Lookup getLookup(MimePath mimePath) {
            if ("text/x-java".equals(mimePath.getPath()))
                return L;
            return null;
        }

    }

    @ServiceProvider(service=SourceForBinaryQueryImplementation.class, position=0)
    public static final class EmptySourceForBinaryQueryImpl implements SourceForBinaryQueryImplementation2 {
        public Result findSourceRoots2(URL binaryRoot) {
            return INSTANCE;
        }
        public SourceForBinaryQuery.Result findSourceRoots(URL binaryRoot) {
            return findSourceRoots2(binaryRoot);
        }
        private static final Result INSTANCE = new Result() {
            public boolean preferSources() {
                return false;
            }
            public org.openide.filesystems.FileObject[] getRoots() {
                return new org.openide.filesystems.FileObject[0];
            }
            public void addChangeListener(ChangeListener l) {}
            public void removeChangeListener(ChangeListener l) {}
        };
    }

    @ServiceProvider(service=Provider.class, position=0)
    public static class PreferencesProvider implements Provider {

        private final MemoryPreferencesFactory f;

        public PreferencesProvider() {
            this.f = new MemoryPreferencesFactory();
        }

        @Override
        public Preferences preferencesForModule(Class cls) {
            return f.userRoot().node(cls.getPackage().getName());
        }

        @Override
        public Preferences preferencesRoot() {
            return f.userRoot();
        }

    }
    //copied from NB junit:
    public static class MemoryPreferencesFactory implements PreferencesFactory {
        /** Creates a new instance  */
        public MemoryPreferencesFactory() {}

        public Preferences userRoot() {
            return NbPreferences.userRootImpl();
        }

        public Preferences systemRoot() {
            return NbPreferences.systemRootImpl();
        }

        private static class NbPreferences extends AbstractPreferences {
            private static Preferences USER_ROOT;
            private static Preferences SYSTEM_ROOT;

            /*private*/Properties properties;

            static Preferences userRootImpl() {
                if (USER_ROOT == null) {
                    USER_ROOT = new NbPreferences();
                }
                return USER_ROOT;
            }

            static Preferences systemRootImpl() {
                if (SYSTEM_ROOT == null) {
                    SYSTEM_ROOT = new NbPreferences();
                }
                return SYSTEM_ROOT;
            }


            private NbPreferences() {
                super(null, "");
            }

            /** Creates a new instance of PreferencesImpl */
            private  NbPreferences(NbPreferences parent, String name)  {
                super(parent, name);
                newNode = true;
            }

            protected final String getSpi(String key) {
                return properties().getProperty(key);
            }

            protected final String[] childrenNamesSpi() throws BackingStoreException {
                return new String[0];
            }

            protected final String[] keysSpi() throws BackingStoreException {
                return properties().keySet().toArray(new String[0]);
            }

            protected final void putSpi(String key, String value) {
                properties().put(key,value);
            }

            protected final void removeSpi(String key) {
                properties().remove(key);
            }

            protected final void removeNodeSpi() throws BackingStoreException {}
            protected  void flushSpi() throws BackingStoreException {}
            protected void syncSpi() throws BackingStoreException {
                properties().clear();
            }

            @Override
            public void put(String key, String value) {
                try {
                    super.put(key, value);
                } catch (IllegalArgumentException iae) {
                    if (iae.getMessage().contains("too long")) {
                        // Not for us!
                        putSpi(key, value);
                    } else {
                        throw iae;
                    }
                }
            }

            Properties properties()  {
                if (properties == null) {
                    properties = new Properties();
                }
                return properties;
            }

            protected AbstractPreferences childSpi(String name) {
                return new NbPreferences(this, name);
            }
        }

    }

    @ServiceProvider(service=Repository.class)
    public static class RepositoryImpl extends Repository {

        public RepositoryImpl() {
            super(createDefaultFS());
        }

        private static FileSystem createDefaultFS() {
            try {
                List<URL> layers = new LinkedList<URL>();
                boolean found = false;

                for (Enumeration<URL> en = RepositoryImpl.class.getClassLoader().getResources("META-INF/generated-layer.xml"); en.hasMoreElements();) {
                    found = true;
                    layers.add(en.nextElement());
                }

                assert found;

                XMLFileSystem xmlFS = new XMLFileSystem();

                xmlFS.setXmlUrls(layers.toArray(new URL[0]));

                return new MultiFileSystem(new FileSystem[]{FileUtil.createMemoryFileSystem(), xmlFS});
            } catch (PropertyVetoException ex) {
                throw new IllegalStateException(ex);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @ServiceProvider(service=SPI.class, position=0)
    public static class UtilitiesSPIImpl implements SPI {

        @Override
        public ClasspathInfo createUniversalCPInfo() {
            return ClasspathInfo.create(Utils.createDefaultBootClassPath(), ClassPath.EMPTY, ClassPath.EMPTY);
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

    @ServiceProvider(service=ActiveDocumentProvider.class)
    public static final class ActiveDocumentProviderImpl implements ActiveDocumentProvider {

        @Override
        public Document getActiveDocument() {
            return null;
        }

        @Override
        public Set<? extends Document> getActiveDocuments() {
            return Collections.emptySet();
        }

        @Override
        public void addActiveDocumentListener(ActiveDocumentListener listener) {
        }

        @Override
        public void removeActiveDocumentListener(ActiveDocumentListener listener) {
        }

    }

    @ServiceProvider(service=EditorMimeTypesImplementation.class)
    public static final class EditorMimeTypesImplementationImpl implements EditorMimeTypesImplementation {

        private static final Set<String> MIME_TYPES = Collections.singleton("text/x-java");

        @Override
        public Set<String> getSupportedMimeTypes() {
            return MIME_TYPES;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }

    }

    @ServiceProvider(service=EntityCatalog.class, position=100)
    public static final class EntityCatalogImpl extends EntityCatalog {

        @Override
        public InputSource resolveEntity(String arg0, String arg1) throws SAXException, IOException {
            if ("-//NetBeans//DTD Tool Configuration 1.0//EN".equals(arg0)) {
                return new InputSource(EntityCatalogImpl.class.getResourceAsStream("/org/netbeans/modules/editor/tools/storage/ToolConfiguration-1_0.dtd"));
            }
            return null;
        }

    }
}
