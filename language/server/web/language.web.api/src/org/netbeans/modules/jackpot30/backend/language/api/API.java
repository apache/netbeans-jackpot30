/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2010 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2009-2010 Sun Microsystems, Inc.
 */

package org.netbeans.modules.jackpot30.backend.language.api;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTaskImpl;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.lucene.queryParser.ParseException;
import org.netbeans.api.java.source.CompilationInfoHack;
import org.netbeans.lib.nbjavac.services.NBParserFactory;
import org.netbeans.lib.nbjavac.services.NBTreeMaker;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.jackpot30.indexing.index.IndexQuery;
import org.netbeans.modules.jackpot30.resolve.api.CompilationInfo;
import org.netbeans.modules.jackpot30.resolve.api.ResolveService;
import org.netbeans.modules.java.hints.declarative.DeclarativeHintsParser;
import org.netbeans.modules.java.hints.jackpot.spi.PatternConvertor;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;
import org.netbeans.modules.java.hints.providers.spi.HintDescription.AdditionalQueryConstraints;
import org.netbeans.modules.java.hints.providers.spi.Trigger.PatternDescription;
import org.netbeans.modules.java.hints.spiimpl.Utilities;
import org.netbeans.modules.java.hints.spiimpl.hints.HintsInvoker;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch.BulkPattern;
import org.netbeans.spi.editor.hints.ErrorDescription;

/**
 *
 * @author lahvac
 */
@Path("/index/language")
public class API {

    static {
        DeclarativeHintsParser.disableCustomCode = true;
    }

    @GET
    @Path("/search")
    @Produces("text/plain")
    public String find(@QueryParam("path") String segment, @QueryParam("pattern") String pattern, @QueryParam("validate") @DefaultValue("false") boolean validate) throws IOException, InterruptedException, ParseException {
        CategoryStorage category = CategoryStorage.forId(segment);
        Iterable<? extends HintDescription> hints = PatternConvertor.create(pattern);
        BulkPattern bulkPattern = preparePattern(hints, null);
        StringBuilder sb = new StringBuilder();
        List<String> candidates = new ArrayList<String>(IndexQuery.performLocalQuery(category.getIndex(), bulkPattern, false).keySet());
        Collections.sort(candidates);

        for (String candidate : candidates) {
            if (validate) {
                CompilationInfo resolvedInfo = ResolveService.parse(segment, candidate);
                CompilationInfoHack info = new CompilationInfoHack(resolvedInfo);
                List<ErrorDescription> computedHints = new HintsInvoker(HintsSettings.getGlobalSettings(), new AtomicBoolean()).computeHints(info, hints);

                if (computedHints.isEmpty()) continue;
            }
            
            sb.append(candidate);
            sb.append("\n");
        }

        return sb.toString();
    }

    @GET
    @Path("/searchSpans")
    @Produces("text/plain")
    public String findSpans(@QueryParam("path") String segment, @QueryParam("relativePath") String relativePath, @QueryParam("pattern") String pattern) throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        CompilationInfo resolvedInfo = ResolveService.parse(segment, relativePath);
        CompilationInfoHack info = new CompilationInfoHack(resolvedInfo);
        Iterable<? extends HintDescription> hints = PatternConvertor.create(pattern);

        List<ErrorDescription> computedHints = new HintsInvoker(HintsSettings.getGlobalSettings(), new AtomicBoolean()).computeHints(info, hints);

        for (ErrorDescription ed : computedHints) {
            if (!ed.getFile().equals(info.getFileObject())) continue;
            sb.append(ed.getRange().getBegin().getOffset());
            sb.append(":");
            sb.append(ed.getRange().getEnd().getOffset());
            sb.append(":");
        }

        if (sb.length() > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }

        return sb.toString();
    }

    @GET
    @Path("/errors")
    @Produces("text/plain")
    public String errors(@QueryParam("pattern") String pattern) throws IOException {
        StringBuilder sb = new StringBuilder();
        Collection<Diagnostic<? extends JavaFileObject>> errors = new LinkedList<Diagnostic<? extends JavaFileObject>>();

        preparePattern(pattern, errors);

        for (Diagnostic<? extends JavaFileObject> d : errors) {
            sb.append(d.getMessage(null));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static BulkPattern preparePattern(String pattern, Collection<Diagnostic<? extends JavaFileObject>> errors) {
        return preparePattern(PatternConvertor.create(pattern), errors);
    }

    //XXX: copied from BatchSearch, may be possible to merge once CompilationInfo is accessible in server mode
    private static BulkPattern preparePattern(final Iterable<? extends HintDescription> patterns, Collection<Diagnostic<? extends JavaFileObject>> errors) {
        return preparePattern(prepareJavacTaskImpl(), patterns, errors);
    }

    private static BulkPattern preparePattern(JavacTaskImpl javac, final Iterable<? extends HintDescription> patterns, Collection<Diagnostic<? extends JavaFileObject>> errors) {
        Collection<String> code = new LinkedList<String>();
        Collection<Tree> trees = new LinkedList<Tree>();
        Collection<AdditionalQueryConstraints> additionalConstraints = new LinkedList<AdditionalQueryConstraints>();

        for (HintDescription pattern : patterns) {
            String textPattern = ((PatternDescription) pattern.getTrigger()).getPattern();

            code.add(textPattern);
            trees.add(Utilities.parseAndAttribute(javac, textPattern, errors));
            additionalConstraints.add(pattern.getAdditionalConstraints());
        }

        return BulkSearch.getDefault().create(code, trees, additionalConstraints, new AtomicBoolean());
    }

    private static JavacTaskImpl prepareJavacTaskImpl() {
        final String bootPath = System.getProperty("sun.boot.class.path"); //NOI18N
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();

        assert tool != null;

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, Arrays.asList("-bootclasspath",  bootPath, "-Xjcov"), null, Collections.<JavaFileObject>emptyList());

        NBParserFactory.preRegister(ct.getContext());
        NBTreeMaker.preRegister(ct.getContext());

        return ct;
    }

    private static final class JFOImpl extends SimpleJavaFileObject {
        private final CharSequence code;
        public JFOImpl(CharSequence code) {
            super(URI.create(""), Kind.SOURCE);
            this.code = code;
        }
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return code;
        }
    }
}
