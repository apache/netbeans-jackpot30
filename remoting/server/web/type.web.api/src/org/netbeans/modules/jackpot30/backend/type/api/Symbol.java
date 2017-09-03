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

package org.netbeans.modules.jackpot30.backend.type.api;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.lucene.document.Document;
import org.netbeans.modules.parsing.lucene.support.Convertor;

/**
 *
 * @author lahvac
 */
@Path("/index/symbol")
public class Symbol extends Base {

    @GET
    @Path("/search")
    @Produces("application/json")
    public String findSymbol(@QueryParam("path") String segment, @QueryParam("prefix") String prefix, @QueryParam("casesensitive") @DefaultValue("false") boolean casesensitive, @QueryParam("querykind") String queryKindName) throws IOException, InterruptedException {
        return doFind(segment, prefix, casesensitive, queryKindName, "feature", new SymbolConvertorImpl());
    }

    private static class SymbolConvertorImpl implements Convertor<Document, Entry<String, Map<String, Object>>> {
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
