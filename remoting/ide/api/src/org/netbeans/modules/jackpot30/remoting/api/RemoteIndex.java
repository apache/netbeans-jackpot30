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

package org.netbeans.modules.jackpot30.remoting.api;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.codeviation.pojson.Pojson;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.modules.jackpot30.remotingapi.options.Utils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;

/**
 *
 * @author lahvac
 */
public class RemoteIndex {

    public final boolean enabled;
    private final String folder;
    public final UseLocalCache useLocalCache;
    public final URL    remote;
    public final String remoteSegment;

    public static RemoteIndex create(@NullAllowed URL localFolder, URL remote, String remoteSegment) {
        return create(true, UseLocalCache.NEVER, localFolder, remote, remoteSegment);
    }

    public static RemoteIndex create(boolean enabled, UseLocalCache useLocalCache, @NullAllowed URL localFolder, URL remote, String remoteSegment) {
        return new RemoteIndex(enabled, useLocalCache, localFolder != null ? localFolder.toExternalForm() : null, remote, remoteSegment);
    }

    private RemoteIndex() {//used by Pojson
        this.enabled = true;
        this.useLocalCache = UseLocalCache.NEVER;
        this.folder = null;
        this.remote = null;
        this.remoteSegment = null;
    }

    private RemoteIndex(boolean enabled, UseLocalCache useLocalCache, String folder, URL remote, String remoteSegment) {
        this.enabled = enabled;
        this.useLocalCache = useLocalCache;
        this.folder = folder;
        this.remote = remote;
        this.remoteSegment = remoteSegment;
    }

    public @CheckForNull URL getLocalFolder() {
        return folder != null ? Utils.fromDisplayName(folder) : null;
    }

    private Reference<FileSystem> data;

    public FileObject getFile(String relativePath) {
        if (folder == null) {
            FileSystem fs;

            synchronized (this) {
                fs = data != null ? data.get() : null;

                if (fs == null) data = new SoftReference<FileSystem>(fs = FileUtil.createMemoryFileSystem());
            }

            String remoteRelativePath = relativePath.replace(".java", ".rjava");

            FileObject result = fs.getRoot().getFileObject(remoteRelativePath);

            if (result == null) {
                try {
                    result = FileUtil.createData(fs.getRoot(), remoteRelativePath);
                    result.setAttribute("remoteIndex", this);
                    result.setAttribute("relative", relativePath);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            return result;
        }
        FileObject originFolder = URLMapper.findFileObject(getLocalFolder());

        return originFolder != null ? originFolder.getFileObject(relativePath) : null;
    }

    private static final String KEY_REMOTE_INDICES = RemoteIndex.class.getSimpleName();
    private static int localServerPort = -2;

    public static Iterable<? extends RemoteIndex> loadIndices() {
        return loadIndices(false);
    }

    public static Iterable<? extends RemoteIndex> loadIndices(boolean includeAll) {
        List<RemoteIndex> result = new LinkedList<RemoteIndex>();
        Preferences prefs = NbPreferences.forModule(RemoteIndex.class).node(KEY_REMOTE_INDICES);

        if (prefs != null) {
            try {
                for (String key : prefs.keys()) {
                    if (key.startsWith("index")) {
                        RemoteIndex idx = Pojson.load(RemoteIndex.class, prefs.get(key, null));

                        if (idx.enabled && idx.useLocalCache == UseLocalCache.ALWAYS) {
                            if (localServerPort == (-2)) {
                                localServerPort = Lookup.getDefault().lookup(LocalServer.class).startLocalServer();
                            }

                            if (localServerPort != (-1)) {
                                try {
                                    idx = new RemoteIndex(true, UseLocalCache.ALWAYS, idx.folder, new URL("http://localhost:" + localServerPort + "/index"), idx.remoteSegment);
                                } catch (MalformedURLException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            }
                        }

                        if (includeAll || idx.enabled)
                            result.add(idx);
                    }
                }
            } catch (BackingStoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return result;
    }
    
    public static void saveIndices(Iterable<? extends RemoteIndex> indices) {
        Preferences prefs = NbPreferences.forModule(RemoteIndex.class).node(KEY_REMOTE_INDICES);

        try {
            prefs.clear();
        } catch (BackingStoreException ex) {
            Exceptions.printStackTrace(ex);
        }

        int i = 0;

        for (RemoteIndex idx : indices) {
            prefs.put("index" + i++, Pojson.save(idx));
        }
        
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public enum UseLocalCache {
        ALWAYS,
        NEVER;
    }
}
