/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2011 Sun Microsystems, Inc. All rights reserved.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2009-2011 Sun Microsystems, Inc.
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
