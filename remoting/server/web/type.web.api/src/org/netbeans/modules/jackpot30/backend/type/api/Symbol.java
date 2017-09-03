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
