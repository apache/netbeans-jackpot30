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
package org.netbeans.modules.jackpot30.backend.usages.api;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbCollections;

/**
 *
 * @author lahvac
 */
@Path("/index/downloadable")
public class DownloadableIndex {

    @GET
    @Path("/netbeans")
    @Produces("application/octet-stream")
    public Response netbeans(@QueryParam("path") String segment, @QueryParam("root") String root) throws IOException, InterruptedException {
        CategoryStorage category = CategoryStorage.forId(segment);
        final File idx = category.getSegment(root);

        if (idx == null || !idx.canRead()) return Response.status(Response.Status.NOT_FOUND).build();
        
        return Response.ok().entity(new StreamingOutput() {
            @Override public void write(OutputStream output) throws IOException, WebApplicationException {
                InputStream in = new FileInputStream(idx);
                byte[] read = new byte[8 * 1024];

                while (true) {
                    int n = in.read(read);

                    if (n == (-1)) break;

                    output.write(read, 0, n);
                }

                in.close(); //TODO: finally
            }
        } ).build();
    }

    private static final Collection<String> PARTS_TO_COPY = Arrays.asList("index", "info", "segments");

    @GET
    @Path("/index")
    @Produces("application/octet-stream")
    public Response index(@QueryParam("path") String segment) throws IOException, InterruptedException {
        CategoryStorage category = CategoryStorage.forId(segment);
        final FileObject idxRoot = category.getCacheRoot();

        if (idxRoot == null || !idxRoot.canRead()) return Response.status(Response.Status.NOT_FOUND).build();

        long totalSize = 0;

        for (String rel : PARTS_TO_COPY) {
            FileObject relFO = idxRoot.getFileObject(rel);

            if (relFO == null) continue;

            Iterable<? extends FileObject> children;

            if (relFO.isFolder()) children = NbCollections.iterable(relFO.getChildren(true));
            else children = Collections.singletonList(relFO);

            for (FileObject c : children) {
                if (c.isFolder()) continue;

                totalSize += c.getSize();
            }
        }

        return Response.ok().header("NB-Total-Unpacked-Size", String.valueOf(totalSize)).entity(new StreamingOutput() {
            @Override public void write(OutputStream output) throws IOException, WebApplicationException {
                JarOutputStream out = new JarOutputStream(output);

                try {
                    for (String rel : PARTS_TO_COPY) {
                        FileObject relFO = idxRoot.getFileObject(rel);

                        if (relFO == null) continue;

                        Iterable<? extends FileObject> children;

                        if (relFO.isFolder()) children = NbCollections.iterable(relFO.getChildren(true));
                        else children = Collections.singletonList(relFO);

                        for (FileObject c : children) {
                            if (c.isFolder()) continue;

                            out.putNextEntry(new ZipEntry(FileUtil.getRelativePath(idxRoot, c)));

                            InputStream in = c.getInputStream();

                            try {
                                FileUtil.copy(in, out);
                            } finally {
                                in.close();
                            }

                            out.closeEntry();
                        }
                    }
                } finally {
                    out.close();
                }
            }
        } ).build();
    }

}
