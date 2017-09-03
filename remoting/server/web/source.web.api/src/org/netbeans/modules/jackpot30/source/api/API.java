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
