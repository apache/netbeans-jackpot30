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
package org.netbeans.modules.jackpot30.backend.base;

import com.sun.jersey.api.NotFoundException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.codeviation.pojson.Pojson;
import org.netbeans.modules.parsing.lucene.support.Index;
import org.netbeans.modules.parsing.lucene.support.IndexManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.JarFileSystem;

/**
 *
 * @author lahvac
 */
public class CategoryStorage {

    public static synchronized void setCacheRoot(File cacheRoot) {
        CategoryStorage.cacheRoot = cacheRoot;
        categoryCache = null;
    }

    public static synchronized File getAccessStatisticsFile() {
        return new File(cacheRoot, "accessStatistics");
    }

    public static void internalReset() {
        setCacheRoot(cacheRoot);
    }

    private static File cacheRoot;
    private static Reference<Iterable<? extends CategoryStorage>> categoryCache;

    public static synchronized Iterable<? extends CategoryStorage> listCategories() {
        Iterable<? extends CategoryStorage> cached = categoryCache != null ? categoryCache.get() : null;

        if (cached != null) return cached;

        List<CategoryStorage> result = new ArrayList<CategoryStorage>();

        for (File cat : cacheRoot.listFiles()) {
            if (!cat.isDirectory()) continue;
            File info = new File(cat, "info");
            String displayName = cat.getName();
            if (info.canRead()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = Pojson.load(HashMap.class, info);
                    if (data.containsKey("displayName")) {
                        displayName = (String) data.get("displayName"); //XXX: should check type!
                    }
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(CategoryStorage.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(CategoryStorage.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            result.add(new CategoryStorage(cat.getName(), displayName));
        }

        categoryCache = new SoftReference<Iterable<? extends CategoryStorage>>(result);
        
        return result;
    }

    public static CategoryStorage forId(String id) {
        for (CategoryStorage s : listCategories()) {
            if (s.id.equals(id)) return s;
        }

        throw new NotFoundException("No category with id: " + id);
    }
    
    private final String id;
    private final String displayName;

    private CategoryStorage(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    private Iterable<? extends SourceRoot> sourceRoots;

    public synchronized Iterable<? extends SourceRoot> getSourceRoots() {
        if (sourceRoots != null) return sourceRoots;

        List<SourceRoot> result = new ArrayList<SourceRoot>();

        try {
            Map<String, String> invertedSegments = getInvertedSegments();

            for (Entry<String, String> e : invertedSegments.entrySet()) {
                if (!e.getKey().startsWith("rel:")) continue;
                result.add(new SourceRoot(this, new URL(e.getKey()).getPath().substring(1), e.getValue()));
            }
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(CategoryStorage.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(CategoryStorage.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(CategoryStorage.class.getName()).log(Level.SEVERE, null, ex);
        }

        return sourceRoots = result;
    }

    private final Map<String, Reference<FileSystem>> embeddedJar2FileSystem = new HashMap<String, Reference<FileSystem>>();

    public synchronized FileObject getEmbeddedJarRoot(String jarName) throws IOException {
        Reference<FileSystem> fsRef = embeddedJar2FileSystem.get(jarName);
        FileSystem fs = fsRef != null ? fsRef.get() : null;

        if (fs == null) {
            File f = new File(FileUtil.toFile(getCacheRoot()), jarName);

            if (!f.canRead()) {
                return null;//XXX: should not happen, but does?
//                throw new IllegalStateException(jarName);
            }

            embeddedJar2FileSystem.put(jarName, new SoftReference<FileSystem>(fs = new JarFileSystem(f)));
        }

        return fs.getRoot();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public FileObject getCacheRoot() {
        return FileUtil.toFileObject(FileUtil.normalizeFile(new File(cacheRoot, id)));
    }

    private File getIndexFile() {
        return new File(new File(cacheRoot, id), "index");
    }

    private Reference<Index> cachedIndex;

    public synchronized Index getIndex() {
        Index cached = cachedIndex != null ? cachedIndex.get() : null;

        if (cached != null) return cached;

        try {
            Index index = IndexManager.createIndex(getIndexFile(), new KeywordAnalyzer());

            index.getStatus(true);

            cachedIndex = new SoftReference<Index>(index);

            return index;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, String> getInvertedSegments() throws IOException {
        FileObject root = getCacheRoot();
        FileObject segments = root.getFileObject("segments");

        if (segments == null) return null;

        Properties segmentsMap = loadProperties(segments);
        Map<String, String> invertedSegments = new HashMap<String, String>();

        for (Entry<Object, Object> e : segmentsMap.entrySet()) {
            invertedSegments.put((String) e.getValue(), (String) e.getKey());
        }

        return invertedSegments;
    }

    public File getSegment(String relPath) {
        try {
            Map<String, String> invertedSegments = getInvertedSegments();
            String segment = invertedSegments.get(relPath);

            if (segment == null) {
                segment = invertedSegments.get("rel:/" + relPath);
            }

            if (segment == null) {
                segment = invertedSegments.get("rel:/" + relPath + "/");
            }
            
            if (segment != null) {
                return new File(new File(cacheRoot, id), segment);
            } else {
                return null;
            }
        } catch (IOException ex) {
            Logger.getLogger(CategoryStorage.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private static Properties loadProperties(FileObject properties) throws IOException {
        Properties inProps = new Properties();
        InputStream inPropsIS = properties.getInputStream();
        try {
            inProps.load(inPropsIS);
        } finally {
            inPropsIS.close();
        }
        return inProps;
    }

    private long getSize() {
        long result = 0;

        for (Enumeration<? extends FileObject> en = getCacheRoot().getChildren(true); en.hasMoreElements(); ) {
            FileObject f = en.nextElement();

            if (f.isData()) {
                result += f.getSize();
            }
        }

        return result;
    }

    private AtomicReference<String> info = new AtomicReference<String>();

    public String getInfo() {
        String result = info.get();

        if (result != null) return result;

        FileObject infoFile = getCacheRoot().getFileObject("info");
        String content;
        try {
            content = infoFile != null ? infoFile.asText("UTF-8") : "{}";
        } catch (IOException ex) {
            Logger.getLogger(CategoryStorage.class.getName()).log(Level.SEVERE, null, ex);
            content = "{}";
        }
        Map<String, Object> infoData = Pojson.load(HashMap.class, content);

        if (!infoData.containsKey("indexSize")) {
            infoData.put("indexSize", getSize());
        }

        info.set(result = Pojson.save(infoData));

        return result;
    }
}
