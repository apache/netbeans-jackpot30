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
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.codeviation.pojson.Pojson;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.jackpot30.backend.base.Utilities;
import org.netbeans.modules.parsing.lucene.support.Convertor;
import org.netbeans.modules.parsing.lucene.support.Index;
import org.netbeans.modules.parsing.lucene.support.Queries;
import org.netbeans.modules.parsing.lucene.support.Queries.QueryKind;

/**
 *
 * @author lahvac
 */
@Path("/index/implements")
public class SubClasses {

    private static final String KEY_SUPERTYPES = "classSupertypes";
    
    @GET
    @Path("/search")
    @Produces("text/plain")
    public String search(@QueryParam("path") String segment, @QueryParam("type") String type, @QueryParam("method") String method) throws IOException, InterruptedException {
        CategoryStorage category = CategoryStorage.forId(segment);
        Index idx = category.getIndex();
        Query query = Queries.createQuery(type != null ? KEY_SUPERTYPES : "featureOverrides", "does-not-exist", type != null ? type : method, QueryKind.EXACT);
        List<Entry<String, Map<String, Object>>> found = new ArrayList<Entry<String, Map<String, Object>>>();

        //TODO: field selector:
        idx.query(found, type != null ? new SubTypeConvertorImpl() : new OverridersConvertorImpl(), null, new AtomicBoolean(), query);

        return Pojson.save(Utilities.sortBySourceRoot(found, category));
    }

    private static class SubTypeConvertorImpl implements Convertor<Document, Entry<String, Map<String, Object>>> {
        @Override public Entry<String, Map<String, Object>> convert(Document p) {
            Map<String, Object> result = new HashMap<String, Object>();

            result.put("file", p.get("file"));
            result.put("class", p.get("classFQN"));

            return new SimpleEntry<String, Map<String, Object>>(p.get("file"), result);
        }
    }

    private static class OverridersConvertorImpl implements Convertor<Document, Entry<String, Map<String, Object>>> {
        @Override public Entry<String, Map<String, Object>> convert(Document p) {
            Map<String, Object> result = new HashMap<String, Object>();

            result.put("file", p.get("file"));
            result.put("enclosingFQN", p.get("featureClassFQN"));
            result.put("simpleName", p.get("featureSimpleName"));
            String featureSignature = p.get("featureSignature");
            if (featureSignature != null)
                result.put("signature", featureSignature);
            String featureVMSignature = p.get("featureVMSignature");
            if (featureVMSignature != null)
                result.put("vmsignature", featureVMSignature);
            result.put("kind", p.get("featureKind"));
            result.put("modifiers", p.getValues("featureModifiers")); //XXX

            return new SimpleEntry<String, Map<String, Object>>(p.get("file"), result);
        }
    }
}
