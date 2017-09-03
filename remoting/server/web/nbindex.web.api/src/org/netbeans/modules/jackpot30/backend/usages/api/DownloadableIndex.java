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
