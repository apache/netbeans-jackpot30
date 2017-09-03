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
package org.netbeans.modules.jackpot30.remoting.local;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import org.netbeans.api.extexecution.ExternalProcessBuilder;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.progress.aggregate.ProgressContributor;
import org.netbeans.modules.jackpot30.remoting.api.LocalServer;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=LocalServer.class)
public class LocalServerImpl implements LocalServer {

    private static final String CACHE_PATH = "remoting-index";

    private int serverPort = -2;

    @Override
    public int startLocalServer() {
        File webMain = InstalledFileLocator.getDefault().locate("index-server/web/web.main.jar", null, false);

        if (webMain == null) return -1;

        FileObject javaFO = JavaPlatform.getDefault().findTool("java");
        File java = javaFO != null ? FileUtil.toFile(javaFO) : null;

        if (java == null) return -1;

        ExternalProcessBuilder epb = new ExternalProcessBuilder(java.getAbsolutePath());

        epb = epb.addArgument("-Djava.index.useMemCache=false");
        epb = epb.addArgument("-Xbootclasspath/p:" + new File(webMain, "lib/javac-api-nb-7.0-b07.jar").getAbsolutePath() + ":" + new File(webMain, "lib/javac-api-nb-7.0-b07.jar").getAbsolutePath());
        epb = epb.addArgument("-jar");
        epb = epb.addArgument(webMain.getAbsolutePath());
        epb = epb.addArgument("--freeport");
        epb = epb.addArgument(Places.getCacheSubdirectory(CACHE_PATH).getAbsolutePath());

        try {
            Process running = epb.call();
            BufferedReader br = new BufferedReader(new InputStreamReader(running.getInputStream()));
            String line = br.readLine();

            if (line != null && line.startsWith("Running on port: ")) {
                return serverPort = Integer.parseInt(line.substring("Running on port: ".length()));
            }

            running.destroy();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return serverPort = -1;
        }

        return serverPort = -1;
    }

    private static final int TOTAL_WORK = 1000;

    @Override
    public boolean downloadIndex(RemoteIndex idx, ProgressContributor progress) throws IOException {
        try {
            progress.progress("Downloading index from " + idx.remote.toURI() + " subindex " + idx.remoteSegment);
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        }
        
        URL url = new URL(idx.remote.toExternalForm() + "/downloadable/index?path=" + idx.remoteSegment);
        URLConnection c = url.openConnection();
        String totalUnpackedSizeString = c.getHeaderField("NB-Total-Unpacked-Size");
        long totalUnpackedSize = -1;
        try {
            totalUnpackedSize = Long.parseLong(totalUnpackedSizeString);
        } catch (NumberFormatException ex) {
            Logger.getLogger(LocalServerImpl.class.getName()).log(Level.FINE, null, ex);
        }
        InputStream in = url.openStream();
        JarInputStream jis = new JarInputStream(in);
        File cacheDir = Places.getCacheSubdirectory(CACHE_PATH);
        File newTarget = new File(cacheDir, idx.remoteSegment + ".new");
        final byte[] BUFFER = new byte[4096];
        long written = 0;

        progress.start(TOTAL_WORK);

        try {
        ZipEntry ze;

        while ((ze = jis.getNextEntry()) != null) {
            if (ze.isDirectory()) continue;

            File targetFile = new File(newTarget, ze.getName());

            targetFile.getParentFile().mkdirs();

            OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile));

            int read;

            while ((read = jis.read(BUFFER)) != (-1)) {
                out.write(BUFFER, 0, read);
                written += read;
                if (totalUnpackedSize > 0) {
                    progress.progress(Math.min(TOTAL_WORK, (int) (((double) written / totalUnpackedSize) * TOTAL_WORK)));
                }
            }

            out.close();
        }

        File target = new File(cacheDir, idx.remoteSegment);
        File old = new File(cacheDir, idx.remoteSegment + ".old");

        target.renameTo(old);
        newTarget.renameTo(target);

        FileObject oldFO = FileUtil.toFileObject(old);

        if (oldFO != null) oldFO.delete();

        if (serverPort > 0) {
            new URL("http://localhost:" + serverPort + "/index/internal/indexUpdated").openStream().close();
        }

        } finally {
        progress.finish();
        }
        
        return true;
    }

}
