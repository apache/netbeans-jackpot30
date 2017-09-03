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
