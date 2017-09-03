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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.codeviation.pojson.Pojson;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.jackpot30.backend.base.Utilities;
import org.netbeans.modules.jumpto.common.Utils;
import org.netbeans.modules.parsing.lucene.support.Convertor;
import org.netbeans.modules.parsing.lucene.support.Index;
import org.netbeans.modules.parsing.lucene.support.Queries;
import org.netbeans.modules.parsing.lucene.support.Queries.QueryKind;

/**
 *
 * @author lahvac
 */
public class Base {

    protected <T> String doFind(String segment, String prefix, boolean casesensitive, String queryKindName, String fieldPrefix, Convertor<Document, Entry<String, T>> conv) throws IOException, InterruptedException {
        //copied (and converted to NameKind) from jumpto's GoToTypeAction:
        boolean exact = prefix.endsWith(" "); // NOI18N

        prefix = prefix.trim();

        if ( prefix.length() == 0) {
            return "";
        }

        QueryKind queryKind = null;

        if (queryKindName != null) {
            for (QueryKind k : QueryKind.values()) {
                if (queryKindName.equals(k.name())) {
                    queryKind = k;
                }
            }

            //TODO: what to do? currently autoguess, but might also return an error
        }

        if (queryKind == null) {
            int wildcard = Utils.containsWildCard(prefix);

            if (exact) {
                //nameKind = panel.isCaseSensitive() ? SearchType.EXACT_NAME : SearchType.CASE_INSENSITIVE_EXACT_NAME;
                queryKind = QueryKind.EXACT;
            }
            else if ((Utils.isAllUpper(prefix) && prefix.length() > 1) || Queries.isCamelCase(prefix, null, null)) {
                queryKind = QueryKind.CAMEL_CASE;
            }
            else if (wildcard != -1) {
                queryKind = casesensitive ? QueryKind.REGEXP : QueryKind.CASE_INSENSITIVE_REGEXP;
            }
            else {
                queryKind = casesensitive ? QueryKind.PREFIX : QueryKind.CASE_INSENSITIVE_PREFIX;
            }
        }

        CategoryStorage category = CategoryStorage.forId(segment);
        Index index = category.getIndex();

        List<Query> queries = new ArrayList<Query>(2);

        queries.add(Queries.createQuery(fieldPrefix + "SimpleName", fieldPrefix + "SimpleNameLower", prefix, queryKind));

        if (queryKind == QueryKind.CAMEL_CASE) {
            queries.add(Queries.createQuery(fieldPrefix + "SimpleName", fieldPrefix + "SimpleNameLower", prefix, QueryKind.CASE_INSENSITIVE_PREFIX));
        }

        List<Entry<String, T>> found = new ArrayList<Entry<String, T>>();

        //TODO: field selector:
        index.query(found, conv, null, new AtomicBoolean(), queries.toArray(new Query[queries.size()]));

        return Pojson.save(Utilities.sortBySourceRoot(found, category));
    }

}
