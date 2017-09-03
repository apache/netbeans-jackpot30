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
package org.netbeans.modules.jackpot30.remoting.downloadable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.netbeans.modules.parsing.impl.indexing.RepositoryUpdater;
import org.netbeans.modules.parsing.impl.indexing.friendapi.IndexDownloader;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=IndexDownloader.class)
public class IndexDownloaderImpl implements IndexDownloader {

    @Override
    public URL getIndexURL(URL sourceRoot) {
        FileObject sourceRootFO = URLMapper.findFileObject(sourceRoot);

        if (sourceRootFO == null) return null;

        for (RemoteIndex ri : RemoteIndex.loadIndices()) {
            URL localFolderURL = ri.getLocalFolder();
            FileObject indexRootFO = localFolderURL != null ? URLMapper.findFileObject(localFolderURL) : null;

            if (indexRootFO == null) continue;

            if (FileUtil.isParentOf(indexRootFO, sourceRootFO) || indexRootFO == sourceRootFO) {
                String relativePath = FileUtil.getRelativePath(indexRootFO, sourceRootFO);
                InputStream in = null;

                try {
                    URL result = new URL(ri.remote.toExternalForm() + "/downloadable/netbeans?path=" + WebUtilities.escapeForQuery(ri.remoteSegment) + "&root=" + WebUtilities.escapeForQuery(relativePath));
                    HttpURLConnection c = (HttpURLConnection) result.openConnection();

                    if (c.getResponseCode() / 100 == 2 && (in = c.getInputStream()).read() != (-1)) //XXX: because the RepUp would currently throw an exception!
                        return result;
                } catch (MalformedURLException ex) {
                    Logger.getLogger(IndexDownloaderImpl.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(IndexDownloaderImpl.class.getName()).log(Level.SEVERE, null, ex);
                } catch (URISyntaxException ex) {
                    Logger.getLogger(IndexDownloaderImpl.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Logger.getLogger(IndexDownloaderImpl.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        }

        return null;
    }

}
