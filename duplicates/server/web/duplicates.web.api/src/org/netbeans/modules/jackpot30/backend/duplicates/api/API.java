/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2012 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2009-2012 Sun Microsystems, Inc.
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
