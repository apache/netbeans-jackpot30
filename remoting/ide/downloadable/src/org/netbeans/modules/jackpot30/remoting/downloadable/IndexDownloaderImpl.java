/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
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
