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
package org.netbeans.modules.jackpot30.source.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.lucene.document.CompressionTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.parsing.lucene.support.Convertor;
import org.netbeans.modules.parsing.lucene.support.Index;
import org.netbeans.modules.parsing.lucene.support.Queries;
import org.netbeans.modules.parsing.lucene.support.Queries.QueryKind;

/**
 *
 * @author lahvac
 */
@Path("/index/source")
public class API {

    private static final String KEY_CONTENT = "content";
    
    @GET
    @Path("/cat")
    @Produces("text/plain")
    public String cat(@QueryParam("path") String segment, @QueryParam("relative") String relative) throws IOException, InterruptedException {
        CategoryStorage category = CategoryStorage.forId(segment);

        return readFileContent(category, relative);
    }

    public static String readFileContent(CategoryStorage category, String relative) throws IOException, InterruptedException {
        Index idx = category.getIndex();
        Query query = Queries.createQuery("relativePath", "does-not-exist", relative, QueryKind.EXACT);
        List<String> found = new ArrayList<String>();

        //TODO: field selector:
        idx.query(found, new ConvertorImpl(), null, new AtomicBoolean(), query);

        return !found.isEmpty() ? found.get(0) : null;
    }

    @GET
    @Path("/randomfiles")
    @Produces("text/plain")
    public String randomFiles(@QueryParam("path") String segment, @QueryParam("count") @DefaultValue("3") int count) throws IOException, InterruptedException {
        CategoryStorage category = CategoryStorage.forId(segment);
        Index idx = category.getIndex();
        Query query = Queries.createQuery("relativePath", "does-not-exist", "", QueryKind.PREFIX);

        List<String> found = new ArrayList<String>();

        //TODO: field selector:
        idx.query(found, new Convertor<Document, String>() {
            @Override public String convert(Document p) {
                return p.get("relativePath");
            }
        }, null, new AtomicBoolean(), query);

        Set<String> chosen = new LinkedHashSet<String>();
        StringBuilder result = new StringBuilder();
        Random r = new Random();

        while (chosen.size() < count) {
            String sel = found.get(r.nextInt(found.size()));

            if (chosen.add(sel)) {
                if (result.length() > 0) result.append("\n");
                result.append(sel);
            }
        }

        return result.toString();
    }

    private static class ConvertorImpl implements Convertor<Document, String> {
        @Override public String convert(Document p) {
            try {
                return CompressionTools.decompressString(p.getBinaryValue(KEY_CONTENT));
            } catch (DataFormatException ex) {
                Logger.getLogger(API.class.getName()).log(Level.SEVERE, null, ex);
                return "";
            }
        }
    }
}
