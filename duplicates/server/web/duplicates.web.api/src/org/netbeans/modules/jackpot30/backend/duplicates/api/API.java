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

package org.netbeans.modules.jackpot30.backend.duplicates.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.codeviation.pojson.Pojson;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.parsing.lucene.support.Convertor;
import org.netbeans.modules.parsing.lucene.support.Index;

/**
 *
 * @author lahvac
 */
@Path("/index/duplicates")
public class API {

    @GET
    @Path("/findDuplicates")
    @Produces("text/plain")
    public String findDuplicates(@QueryParam("path") String segment, @QueryParam("hashes") String hashes) throws IOException, InterruptedException {
        Map<String, Map<String, Collection<? extends String>>> hash2Segment2Contains = new HashMap<String, Map<String, Collection<? extends String>>>();
        Collection<String> segments = new LinkedList<String>();

        if (segment != null) segments.add(segment);
        else {
            for (CategoryStorage cat : CategoryStorage.listCategories()) {
                segments.add(cat.getId());
            }
        }

        Iterable<? extends String> hashesList = Arrays.asList(Pojson.load(String[].class, hashes));

        for (String key : segments) {
            for (String hash : hashesList) {
                CategoryStorage category = CategoryStorage.forId(key);
                Index index = category.getIndex();
                List<String> files = new ArrayList<String>();
                Query query = new TermQuery(new Term("duplicatesGeneralized", hash));
                index.query(files, new Convertor<Document, String>() {
                    @Override public String convert(Document p) {
                        return p.get("duplicatesPath");
                    }
                }, new FieldSelector() {
                    @Override public FieldSelectorResult accept(String fieldName) {
                        return "duplicatesPath".equals(fieldName) ? FieldSelectorResult.LOAD : FieldSelectorResult.NO_LOAD;
                    }
                }, new AtomicBoolean(), query);
                Map<String, Collection<? extends String>> segment2Contains = hash2Segment2Contains.get(hash);
                if (segment2Contains == null) {
                    hash2Segment2Contains.put(hash, segment2Contains = new HashMap<String, Collection<? extends String>>());
                }
                segment2Contains.put(key, files);
            }
        }

        return Pojson.save(hash2Segment2Contains);
    }

}
