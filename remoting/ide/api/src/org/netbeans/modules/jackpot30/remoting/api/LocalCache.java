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
package org.netbeans.modules.jackpot30.remoting.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.FSDirectory;
import org.codeviation.pojson.Pojson;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.modules.jackpot30.common.api.IndexAccess.NoAnalyzer;
import org.netbeans.modules.jackpot30.remotingapi.CacheFolder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class LocalCache {

    private static final Map<URI, IndexReader> readerCache = new HashMap<URI, IndexReader>();

    private static File findLocalCacheDir(RemoteIndex ri) throws IOException {
        return new File(FileUtil.toFile(FileUtil.createFolder(CacheFolder.getDataFolder(ri.remote), "remote-duplicates")), ri.remoteSegment);
    }

    private static final long VERSION_CHECK_PERIOD = 60 * 60 * 1000;
    private static final Map<Entry<URI, String>, Long> lastVersionCheck = new HashMap<Entry<URI, String>, Long>();

    @CheckForNull public static synchronized <R> R runOverLocalCache(RemoteIndex ri, Task<IndexReader, R> task, R empty, AtomicBoolean cancel) throws IOException, URISyntaxException {
        URI uri = ri.remote.toURI();
        SimpleEntry<URI, String> versionCheckKey = new SimpleEntry<URI, String>(uri, ri.remoteSegment);
        Long lastCheck = lastVersionCheck.get(versionCheckKey);

        if (lastCheck == null || (System.currentTimeMillis() - lastCheck) > VERSION_CHECK_PERIOD) {
            File dir = findLocalCacheDir(ri);
            File remoteVersion = new File(dir, "remoteVersion");
            FileObject remoteVersionFO = FileUtil.toFileObject(remoteVersion);
            String previousVersion = remoteVersionFO != null ? remoteVersionFO.asText("UTF-8") : null;
            URI infoURI = new URI(ri.remote.toExternalForm() + "/info?path=" + WebUtilities.escapeForQuery(ri.remoteSegment));
            String infoContent = WebUtilities.requestStringResponse(infoURI, cancel);

            if (cancel.get()) return empty;

            if (infoContent != null && !infoContent.trim().isEmpty()) {
                Object buildId = Pojson.load(LinkedHashMap.class, infoContent).get("BUILD_ID");

                if (buildId != null && !(buildId = buildId.toString()).equals(previousVersion)) {
                    remoteVersion.getParentFile().mkdirs();
                    OutputStream out = new FileOutputStream(remoteVersion);
                    try {
                        out.write(buildId.toString().getBytes("UTF-8"));
                    } finally {
                        out.close();
                    }

                    LOG.log(Level.FINE, "Deleting local cache");
                    delete(new File(dir, "index"));

                    IndexReader reader = readerCache.remove(uri);
                    if (reader != null)
                        reader.close();

                }
            }

            lastVersionCheck.put(versionCheckKey, System.currentTimeMillis());
        }

        IndexReader reader = readerCache.get(uri);

        if (reader == null && !cancel.get()) {
            File dir = new File(findLocalCacheDir(ri), "index");

            if (dir.listFiles() != null && dir.listFiles().length > 0) {
                readerCache.put(uri, reader = IndexReader.open(FSDirectory.open(dir), true));
            }
        }

        if (reader == null || cancel.get()) {
            return empty;
        }

        return task.run(reader, cancel);
    }

    public static synchronized void saveToLocalCache(RemoteIndex ri, Task<IndexWriter, Void> save) throws IOException, URISyntaxException {
        IndexReader r = readerCache.remove(ri.remote.toURI());

        if (r != null) {
            r.close();
        }

        IndexWriter w = new IndexWriter(FSDirectory.open(new File(findLocalCacheDir(ri), "index")), new NoAnalyzer(), MaxFieldLength.UNLIMITED);

        save.run(w, new AtomicBoolean());

        w.optimize();
        w.close();
    }

    private static final Logger LOG = Logger.getLogger(LocalCache.class.getName());

    private static void delete(File file) {
        File[] c = file.listFiles();

        if (c != null) {
            for (File cc : c) {
                delete(cc);
            }
        }

        file.delete();
    }

    public interface Task<P, R> {
        public R run(P p, AtomicBoolean cancel) throws IOException;
    }
}
