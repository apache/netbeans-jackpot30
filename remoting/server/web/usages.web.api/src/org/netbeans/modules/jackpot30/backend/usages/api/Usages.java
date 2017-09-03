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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
@Path("/index/usages")
public class Usages {

    private static final String KEY_SIGNATURES = "signatures";
    
    @GET
    @Path("/search")
    @Produces("text/plain")
    public String search(@QueryParam("path") String segment, @QueryParam("signatures") String signatures, @QueryParam("searchResources") @DefaultValue("true") boolean searchResources) throws IOException, InterruptedException {
        StringBuilder result = new StringBuilder();
        CategoryStorage category = CategoryStorage.forId(segment);
        Index idx = category.getIndex();
        String origSignature = signatures;

        if ((signatures.startsWith("FIELD:") || signatures.startsWith("ENUM_CONSTANT:")) && signatures.split(":").length == 4) {
            //handle old clients sending field type inside as part of the field handle:
            signatures = signatures.substring(0, signatures.lastIndexOf(':'));
        }

        List<String> found = new ArrayList<String>();
        Query query = Queries.createQuery(KEY_SIGNATURES, "does-not-exist", signatures, QueryKind.EXACT);

        //TODO: field selector:
        idx.query(found, new ConvertorImpl(), null, new AtomicBoolean(), query);

        if (found.isEmpty()) {
            //transient: try old index structure with field handles containing the field type
            query = Queries.createQuery(KEY_SIGNATURES, "does-not-exist", origSignature, QueryKind.EXACT);

            //TODO: field selector:
            idx.query(found, new ConvertorImpl(), null, new AtomicBoolean(), query);
        }

        if (searchResources) {
            //look for usages from resources:
            String[] parts = signatures.split(":");

            if (parts.length >= 2 ) {
                String otherSignature;

                switch (parts[0]) {
                    case "FIELD": case "ENUM_CONSTANT":
                    case "METHOD":
                        if (parts.length >= 3) {
                            otherSignature = "OTHER:" + parts[1] + ":" + parts[2];
                            break;
                        }
                    default:
                        otherSignature = "OTHER:" + parts[1];
                        break;
                }

                query = Queries.createQuery(KEY_SIGNATURES, "does-not-exist", otherSignature, QueryKind.EXACT);

                //TODO: field selector:
                idx.query(found, new ConvertorImpl(), null, new AtomicBoolean(), query);
            }
        }

        for (String foundFile : found) {
            result.append(foundFile);
            result.append("\n");
        }

        return result.toString();
    }

    private static class ConvertorImpl implements Convertor<Document, String> {
        @Override public String convert(Document p) {
            return p.get("file");
        }
    }
}
