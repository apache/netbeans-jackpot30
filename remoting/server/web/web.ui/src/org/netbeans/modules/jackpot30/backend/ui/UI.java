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

package org.netbeans.modules.jackpot30.backend.ui;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.codeviation.pojson.Pojson;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.xml.lexer.XMLTokenId;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.jackpot30.backend.base.WebUtilities;
import static org.netbeans.modules.jackpot30.backend.base.WebUtilities.escapeForQuery;
import org.netbeans.modules.jackpot30.backend.ui.highlighting.ColoringAttributes;
import org.netbeans.modules.jackpot30.backend.ui.highlighting.ColoringAttributes.Coloring;
import org.netbeans.modules.jackpot30.backend.ui.highlighting.SemanticHighlighter;
import org.netbeans.modules.jackpot30.backend.ui.highlighting.TokenList;
import org.netbeans.modules.jackpot30.resolve.api.CompilationInfo;
import org.netbeans.modules.jackpot30.resolve.api.JavaUtils;
import org.netbeans.modules.jackpot30.resolve.api.ResolveService;

/**
 *
 * @author lahvac
 */
@Path("/index/ui")
public class UI {

    private static final String URL_BASE_OVERRIDE = null;
//    private static final String URL_BASE_OVERRIDE = "http://lahoda.info/";

    @GET
    @Path("/search")
    public Response searchType(@Context UriInfo uriInfo, @QueryParam("path") List<String> paths, @QueryParam("prefix") String prefix) throws URISyntaxException, IOException {
        URI base = uriInfo.getBaseUri();
        return Response.seeOther(new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), base.getPath() + "index/ui/index.html", null, "/search?prefix=" + prefix)).build();
    }

    @GET
    @Path("/searchSymbol")
    @Produces("application/javascript")
    public String searchSymbol(@Context UriInfo uriInfo, @QueryParam("path") List<String> paths, @QueryParam("prefix") String prefix) throws URISyntaxException, IOException {
        String urlBase = URL_BASE_OVERRIDE != null ? URL_BASE_OVERRIDE : uriInfo.getBaseUri().toString();
        Map<String, Map<?, ?>> segment2Result = new HashMap<String, Map<?, ?>>();

        if (paths == null) {
            Map<String,Map<String, String>> pathsList = list(urlBase);
            paths = new ArrayList<String>(pathsList.keySet());
        }

        for (String path : paths) {
            URI u = new URI(urlBase + "index/symbol/search?path=" + escapeForQuery(path) + "&prefix=" + escapeForQuery(prefix));
            @SuppressWarnings("unchecked") //XXX: should not trust something got from the network!
            Map<String, List<Map<String, Object>>> symbols = Pojson.load(LinkedHashMap.class, u);

            URI typeSearch = new URI(urlBase + "index/type/search?path=" + escapeForQuery(path) + "&prefix=" + escapeForQuery(prefix));
            @SuppressWarnings("unchecked") //XXX: should not trust something got from the network!
            Map<String, List<String>> types = Pojson.load(LinkedHashMap.class, typeSearch);

            for (Entry<String, List<String>> e : types.entrySet()) {
                List<Map<String, Object>> thisSourceRootResults = new ArrayList<Map<String, Object>>(e.getValue().size());
                for (String fqn : e.getValue()) {
                    Map<String, Object> result = new HashMap<String, Object>();

                    result.put("kind", "CLASS");
                    result.put("fqn", fqn);

                    String simpleName = fqn;
                    String enclosingFQN = "";

                    if (simpleName.lastIndexOf('.') > 0) {
                        simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
                        enclosingFQN = fqn.substring(0, fqn.lastIndexOf('.'));
                    }

                    if (simpleName.lastIndexOf('$') > 0) {
                        simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1);
                        enclosingFQN = fqn.substring(0, fqn.lastIndexOf('$'));
                    }

                    result.put("simpleName", simpleName);
                    result.put("enclosingFQN", enclosingFQN);

                    if (fqn.contains("$")) {
                        fqn = fqn.substring(0, fqn.indexOf("$"));
                    }

                    result.put("file", e.getKey() + fqn.replace('.', '/') + ".java");

                    thisSourceRootResults.add(result);
                }

                List<Map<String, Object>> putInto = symbols.get(e.getKey());

                if (putInto == null) symbols.put(e.getKey(), thisSourceRootResults);
                else putInto.addAll(thisSourceRootResults);
            }

            segment2Result.put(path, Collections.singletonMap("found", symbols));
        }

        return Pojson.save(segment2Result);
    }

    @GET
    @Path("/show")
    public Response show(@Context UriInfo uriInfo, @QueryParam("path") String segment, @QueryParam("relative") String relative, @QueryParam("highlight") String highlightSpec, @QueryParam("signature") String signature) throws URISyntaxException, IOException, InterruptedException {
        URI base = uriInfo.getBaseUri();
        return Response.seeOther(new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), base.getPath() + "index/ui/index.html", null, "/showCode?path=" + segment + "&relative=" + relative + "&goto=" + signature)).build();
    }

    @GET
    @Path("/highlightData")
    @Produces("application/javascript")
    public String highlightData(@Context UriInfo uriInfo, @QueryParam("path") String segment, @QueryParam("relative") String relative) throws URISyntaxException, IOException, InterruptedException {
        HighlightData highlights;
        
        if (relative.endsWith(".java")) {
            CompilationInfo info = ResolveService.parse(segment, relative);
            highlights = colorTokens(info);
        } else {
            String urlBase = URL_BASE_OVERRIDE != null ? URL_BASE_OVERRIDE : uriInfo.getBaseUri().toString();
            URI u = new URI(urlBase + "index/source/cat?path=" + escapeForQuery(segment) + "&relative=" + escapeForQuery(relative));
            String content = WebUtilities.requestStringResponse(u).replace("\r\n", "\n");
            if (relative.endsWith(".xml")) {
                highlights = colorTokens(TokenHierarchy.create(content, XMLTokenId.language()).tokenSequence(XMLTokenId.language()), Collections.<Token, Coloring>emptyMap());
            } else {
                highlights = new HighlightData(Collections.<String>singletonList(""), Collections.<Long>singletonList((long) content.length()));
            }
        }


        return Pojson.save(highlights);
    }

    static HighlightData colorTokens(CompilationInfo info) throws IOException, InterruptedException {
        TokenSequence<?> ts = info.getTokenHierarchy().tokenSequence(JavaTokenId.language());
        Map<Token, Coloring> semanticHighlights = SemanticHighlighter.computeHighlights(info, new TokenList(info, ts, new AtomicBoolean()));

        return colorTokens(ts, semanticHighlights);
    }

    static HighlightData colorTokens(TokenSequence<?> ts, Map<Token, Coloring> semanticHighlights) throws IOException, InterruptedException {
        List<Long> spans = new ArrayList<Long>(ts.tokenCount());
        List<String> cats  = new ArrayList<String>(ts.tokenCount());
        long currentOffset = 0;

        ts.moveStart();

        while (ts.moveNext()) {
            long endOffset = ts.offset() + ts.token().length();
            spans.add(endOffset - currentOffset);
            String category = ts.token().id().primaryCategory();

            switch (category) {
                case "literal":
                case "keyword-directive": category = "keyword"; break;
                case "xml-attribute": category = "markup-attribute"; break;
                case "xml-comment": category = "comment"; break;
                case "xml-error": category = "error"; break;
                case "xml-operator": category = "operator"; break;
                case "xml-ref": category = "entity-reference"; break;
                case "xml-tag": category = "markup-element"; break;
                case "xml-value": category = "markup-attribute-value"; break;
            }
            
            Coloring coloring = semanticHighlights.get(ts.token());

            if (coloring != null) {
                for (ColoringAttributes ca : coloring) {
                    if (!category.isEmpty()) category += " ";
                    category += ca.name().toLowerCase();
                }
            }

            cats.add(category);

            currentOffset = endOffset;
        }

        return new HighlightData(cats, spans);
    }

    private static final class HighlightData {
        List<String> categories;
        List<Long> spans;
        public HighlightData(List<String> cats, List<Long> spans) {
            this.categories = cats;
            this.spans = spans;
        }
    }

    //XXX: usages on fields do not work because the field signature in the index contain also the field type
    @GET
    @Path("/usages")
    public Response usages(@Context UriInfo uriInfo, @QueryParam("path") String segment, @QueryParam("signatures") final String signatures) throws URISyntaxException, IOException {
        URI base = uriInfo.getBaseUri();
        return Response.seeOther(new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), base.getPath() + "index/ui/index.html", null, "#/usages?signature=" + signatures)).build();
    }

    @GET
    @Path("/implements")
    public Response impl(@Context UriInfo uriInfo, @QueryParam("path") String segment, @QueryParam("type") String typeSignature, @QueryParam("method") final String methodSignature) throws URISyntaxException, IOException {
        URI base = uriInfo.getBaseUri();
        return Response.seeOther(new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), base.getPath() + "index/ui/index.html", null, "#/usages?signature=" + (typeSignature != null ? typeSignature : methodSignature))).build();
    }

    @GET
    @Path("/searchUsages")
    @Produces("application/javascript")
    public String searchUsages(@Context UriInfo uriInfo, @QueryParam("path") List<String> paths, @QueryParam("signature") final String signature) throws URISyntaxException, IOException {
        String urlBase = URL_BASE_OVERRIDE != null ? URL_BASE_OVERRIDE : uriInfo.getBaseUri().toString();
        Map<String, Map<?, ?>> segment2Result = new HashMap<String, Map<?, ?>>();

        if (paths == null) {
            Map<String,Map<String, String>> pathsList = list(urlBase);
            paths = new ArrayList<String>(pathsList.keySet());
        }

        for (String path : paths) {
            Map<String, Map<String, Object>> usageFile2Flags = new HashMap<String, Map<String, Object>>();
            
            URI usagesURI = new URI(urlBase + "index/usages/search?path=" + escapeForQuery(path) + "&signatures=" + escapeForQuery(signature));
            List<String> files = new ArrayList<String>(WebUtilities.requestStringArrayResponse(usagesURI));

            for (String file : files) {
                Map<String, Object> flags = usageFile2Flags.get(file);

                if (flags == null) {
                    usageFile2Flags.put(file, flags = new HashMap<String, Object>());
                }

                flags.put("usage", "true");
            }

            if (signature.startsWith("CLASS:") || signature.startsWith("INTERFACE:") || signature.startsWith("ENUM:") || signature.startsWith("ANNOTATION_TYPE:")) {
                final String type = strip(signature, "CLASS:", "INTERFACE:", "ENUM:", "ANNOTATION_TYPE:");
                URI u = new URI(urlBase + "index/implements/search?path=" + escapeForQuery(path) + "&type=" + escapeForQuery(type));
                Map<String, List<Map<String, String>>> data = Pojson.load(HashMap.class, u);
                for (Entry<String, List<Map<String, String>>> relpath2ImplementorsE : data.entrySet()) {
                    for (Map<String, String> implementorData : relpath2ImplementorsE.getValue()) {
                        String file = implementorData.get("file");
                        Map<String, Object> flags = usageFile2Flags.get(file);

                        if (flags == null) {
                            usageFile2Flags.put(file, flags = new HashMap<String, Object>());
                        }

                        List<String> implementors = (List<String>) flags.get("subtypes");

                        if (implementors == null) {
                            flags.put("subtypes", implementors = new ArrayList<String>());
                        }

                        implementors.add(implementorData.get("class"));
                    }
                }
            } else if (signature.startsWith("METHOD:")) {
                URI u = new URI(urlBase + "index/implements/search?path=" + escapeForQuery(path) + "&method=" + escapeForQuery(signature));
                Map<String, List<Map<String, String>>> data = Pojson.load(HashMap.class, u);
                for (Entry<String, List<Map<String, String>>> relpath2ImplementorsE : data.entrySet()) {
                    for (Map<String, String> implementorData : relpath2ImplementorsE.getValue()) {
                        String file = implementorData.get("file");
                        Map<String, Object> flags = usageFile2Flags.get(file);

                        if (flags == null) {
                            usageFile2Flags.put(file, flags = new HashMap<String, Object>());
                        }

                        List<String> overridersParents = (List<String>) flags.get("overridersParents");

                        if (overridersParents == null) {
                            flags.put("overridersParents", overridersParents = new ArrayList<String>());
                        }

                        overridersParents.add(implementorData.get("enclosingFQN"));
                    }
                }
            }
            
            segment2Result.put(path, usageFile2Flags);
        }

        return Pojson.save(segment2Result);
    }

    @GET
    @Path("/localUsages")
    @Produces("application/javascript")
    public String localUsages(@Context UriInfo uriInfo, @QueryParam("path") String segment, @QueryParam("relative") String relative, @QueryParam("signature")  String signature, @QueryParam("position") final long position, @QueryParam("usages") boolean usages) throws URISyntaxException, IOException, InterruptedException {
        List<long[]> result = new ArrayList<long[]>();

        if (relative.endsWith(".java")) {
            final CompilationInfo info = ResolveService.parse(segment, relative);

            if (signature == null) {
                ResolvedLocation location = resolveTarget(info, position, false);

                signature = location.signature;
            }

            if (signature == null) {
                return "[]";
            }

            for (long[] span : ResolveService.usages(info, signature)) {
                result.add(new long[] {span[2], span[3]});
            }

            long[] declSpans = ResolveService.declarationSpans(info, signature);

            if (declSpans != null) {
                result.add(new long[] {declSpans[2], declSpans[3]});
            }
        } else {
            //look for usages from resources:
            String[] parts = signature.split(":");

            if (parts.length >= 2 ) {
                String otherSignature;

                switch (parts[0]) {
                    case "FIELD": case "ENUM_CONSTANT":
                    case "METHOD":
                        if (parts.length >= 3) {
                            otherSignature = parts[1].replace(".", "[.-]") + "[.-]" + parts[2];
                            break;
                        }
                    default:
                        otherSignature = parts[1].replace(".", "[.-]");
                        break;
                }

                String urlBase = URL_BASE_OVERRIDE != null ? URL_BASE_OVERRIDE : uriInfo.getBaseUri().toString();
                URI u = new URI(urlBase + "index/source/cat?path=" + escapeForQuery(segment) + "&relative=" + escapeForQuery(relative));
                String content = WebUtilities.requestStringResponse(u).replace("\r\n", "\n");
                java.util.regex.Matcher matcher = Pattern.compile(otherSignature).matcher(content);

                while (matcher.find()) {
                    result.add(new long[] {matcher.start(), matcher.end()});
                }
            }
        }

        return Pojson.save(result);
    }

    private static String elementDisplayName(String signatures) {
        StringBuilder elementDisplayName = new StringBuilder();
        String[] splitSignature = signatures.split(":");

        if (splitSignature.length == 2) {
            return splitSignature[1];
        }

        elementDisplayName.append(splitSignature[2]);

        if ("METHOD".equals(splitSignature[0])) {
            elementDisplayName.append(decodeMethodSignature(splitSignature[3]));
        }

        elementDisplayName.append(" in ");
        elementDisplayName.append(splitSignature[1]);

        return elementDisplayName.toString();
    }

    private static Map<String, Map<String, String>> list(String urlBase) throws URISyntaxException {
        Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();

        for (String enc : WebUtilities.requestStringArrayResponse(new URI(urlBase + "index/list"))) {
            Map<String, String> rootDesc = new HashMap<String, String>();
            String[] col = enc.split(":", 2);

            rootDesc.put("segment", col[0]);
            rootDesc.put("displayName", col[1]);
            result.put(col[0], rootDesc);
        }

        return result;
    }
    
    //Copied from Icons, NetBeans proper
    private static final String GIF_EXTENSION = ".gif";
    private static final String PNG_EXTENSION = ".png";

    public static String getElementIcon(String elementKind, Collection<String> modifiers ) {
        if ("PACKAGE".equals(elementKind)) {
            return "package" + GIF_EXTENSION;
        } else if ("ENUM".equals(elementKind)) {
            return "enum" + PNG_EXTENSION;
        } else if ("ANNOTATION_TYPE".equals(elementKind)) {
            return "annotation" + PNG_EXTENSION;
        } else if ("CLASS".equals(elementKind)) {
            return "class" + PNG_EXTENSION;
        } else if ("INTERFACE".equals(elementKind)) {
            return "interface"  + PNG_EXTENSION;
	} else if ("FIELD".equals(elementKind)) {
            return getIconName("field", PNG_EXTENSION, modifiers );
	} else if ("ENUM_CONSTANT".equals(elementKind)) {
            return "constant" + PNG_EXTENSION;
	} else if ("CONSTRUCTOR".equals(elementKind)) {
            return getIconName("constructor", PNG_EXTENSION, modifiers );
	} else if (   "INSTANCE_INIT".equals(elementKind)
                   || "STATIC_INIT".equals(elementKind)) {
            return "initializer" + (modifiers.contains("STATIC") ? "Static" : "") + PNG_EXTENSION;
	} else if ("METHOD".equals(elementKind)) {
            return getIconName("method", PNG_EXTENSION, modifiers );
        } else {
            return "";
        }
    }

    // Private Methods ---------------------------------------------------------

    private static String getIconName(String typeName, String extension, Collection<String> modifiers ) {

        StringBuilder fileName = new StringBuilder( typeName );

        if ( modifiers.contains("STATIC") ) {
            fileName.append( "Static" );                        //NOI18N
        }
        if ( modifiers.contains("PUBLIC") ) {
            return fileName.append( "Public" ).append( extension ).toString();      //NOI18N
        }
        if ( modifiers.contains("PROTECTED") ) {
            return fileName.append( "Protected" ).append( extension ).toString();   //NOI18N
        }
        if ( modifiers.contains("PRIVATE") ) {
            return fileName.append( "Private" ).append( extension ).toString();     //NOI18N
        }
        return fileName.append( "Package" ).append( extension ).toString();         //NOI18N

    }

    static String decodeMethodSignature(String signature) {
        assert signature.charAt(0) == '(' || signature.charAt(0) == '<';

        int[] pos = new int[] {1};

        if (signature.charAt(0) == '<') {
            int b = 1;

            while (b > 0) {
                switch (signature.charAt(pos[0]++)) {
                    case '<': b++; break;
                    case '>': b--; break;
                }
            };

            pos[0]++;
        }

        StringBuilder result = new StringBuilder();

        result.append("(");

        while (signature.charAt(pos[0]) != ')') {
            if (result.charAt(result.length() - 1) != '(') {
                result.append(", ");
            }

            result.append(decodeSignatureType(signature, pos));
        }

        result.append(')');

        return result.toString();
    }

    static String decodeSignatureType(String signature, int[] pos) {
        char c = signature.charAt(pos[0]++);
        switch (c) {
            case 'V': return "void";
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'S': return "short";
            case 'I': return "int";
            case 'J': return "long";
            case 'C': return "char";
            case 'F': return "float";
            case 'D': return "double";
            case '[': return decodeSignatureType(signature, pos) + "[]";
            case 'L': {
                int lastSlash = pos[0];
                StringBuilder result = new StringBuilder();

                while (signature.charAt(pos[0]) != ';' && signature.charAt(pos[0]) != '<') {
                    if (signature.charAt(pos[0]) == '/') {
                        lastSlash = pos[0] + 1;
                    }
                    if (signature.charAt(pos[0]) == '$') {
                        lastSlash = pos[0] + 1;
                    }
                    pos[0]++;
                }

                result.append(signature.substring(lastSlash, pos[0]));

                if (signature.charAt(pos[0]++) == '<') {
                    result.append('<');

                    while (signature.charAt(pos[0]) != '>') {
                        if (result.charAt(result.length() - 1) != '<') {
                            result.append(", ");
                        }
                        result.append(decodeSignatureType(signature, pos));
                    }

                    result.append('>');
                    pos[0] += 2;
                }


                return result.toString();
            }
            case 'T': {
                StringBuilder result = new StringBuilder();

                while (signature.charAt(pos[0]) != ';') {
                    result.append(signature.charAt(pos[0]));
                    pos[0]++;
                }

                pos[0]++;
                
                return result.toString();
            }
            case '+': return "? extends " + decodeSignatureType(signature, pos);
            case '-': return "? super " + decodeSignatureType(signature, pos);
            case '*': return "?";
            default: return "unknown";
        }
    }

    private static String[] c = new String[] {"&", "<"}; // NOI18N
    private static String[] tags = new String[] {"&amp;", "&lt;"}; // NOI18N

    private String translate(String input) {
        for (int cntr = 0; cntr < c.length; cntr++) {
            input = input.replaceAll(c[cntr], tags[cntr]);
        }

        return input;
    }

    static String strip(String originalSignature, String... prefixesToStrip) {
        for (String strip : prefixesToStrip) {
            if (originalSignature.startsWith(strip)) {
                return originalSignature.substring(strip.length());
            }
        }

        return originalSignature;
    }

    @GET
    @Path("/target")
//    @Produces("text/html")
    public String target(@QueryParam("path") String segment, @QueryParam("relative") String relative, @QueryParam("position") final long position) throws URISyntaxException, IOException, InterruptedException {
        final CompilationInfo info = ResolveService.parse(segment, relative);
        ResolvedLocation location = resolveTarget(info, position, true);
        Map<String, Object> result = new HashMap<String, Object>();

        if (location.declaration) {
            if (location.signature != null) {
                List<Map<String, String>> menu = new ArrayList<Map<String, String>>();

                menu.add(menuMap("Usages in current project", "/index/ui/usages?path=" + escapeForQuery(segment) + "&signatures=" + escapeForQuery(location.signature)));
                menu.add(menuMap("Usages in all known projects", "/index/ui/usages?signatures=" + escapeForQuery(location.signature)));

                switch (location.kind) {
                    case METHOD:
                        menu.add(menuMap("Overriders in the current project", "/index/ui/implements?path=" + escapeForQuery(segment) + "&method=" + escapeForQuery(location.signature)));
                        menu.add(menuMap("Overriders in all known projects", "/index/ui/implements?method=" + escapeForQuery(location.signature)));
                        break;
                    case CLASS: case INTERFACE: case ENUM: case ANNOTATION_TYPE:
                        menu.add(menuMap("Subtypes in the current project", "/index/ui/implements?path=" + escapeForQuery(segment) + "&type=" + escapeForQuery(location.signature)));
                        menu.add(menuMap("Subtypes in all known projects", "/index/ui/implements?type=" + escapeForQuery(location.signature)));
                        break;
                }
                result.put("menu", menu);
                result.put("signature", location.signature);
            }
        } else {
            if (location.position != (-2)) {
                result.put("position", location.position);
            } else if (location.signature != null) {
                String targetSignature = location.signature;
                String source = ResolveService.resolveSource(segment, relative, targetSignature);

                if (source != null) {
                    result.put("path", segment);
                    result.put("source", source);
                } else {
                    String singleSource = null;
                    String singleSourceSegment = null;
                    boolean multipleSources = false;
                    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

                    for (Entry<? extends CategoryStorage, ? extends Iterable<? extends String>> e : ResolveService.findSourcesContaining(targetSignature).entrySet()) {
                        Map<String, Object> categoryData = new HashMap<String, Object>();

                        categoryData.put("rootDisplayName", e.getKey().getDisplayName());
                        categoryData.put("rootPath", e.getKey().getId());
                        categoryData.put("files", e.getValue());

                        if (!multipleSources) {
                            Iterator<? extends String> fIt = e.getValue().iterator();

                            if (fIt.hasNext()) {
                                singleSource = fIt.next();
                                singleSourceSegment = e.getKey().getId();
                            }

                            if (fIt.hasNext())
                                multipleSources = true;
                        }

                        results.add(categoryData);
                    }

                    if (singleSource != null && !multipleSources) {
                        //TODO: will automatically jump to the single known target - correct?
                        result.put("path", singleSourceSegment);
                        result.put("source", singleSource);
                    } else if (!results.isEmpty()) {
                        result.put("targets", results);
                    }
                }
            }
        }

        if (location.signature != null) {
            result.put("signature", location.signature);
            result.put("superMethods", location.superMethodsSignatures);
        }

        return Pojson.save(result);
    }

    private static Map<String, String> menuMap(String displayName, String url) {
        Map<String, String> result = new HashMap<String, String>();

        result.put("displayName", displayName);
        result.put("url", url);

        return result;
    }

    private static ResolvedLocation resolveTarget(final CompilationInfo info, final long position, final boolean resolveTargetPosition) {
        final boolean[] declaration = new boolean[1];
        final long[] targetPosition = new long[] { -2 };
        final String[] signature = new String[1];
        final String[][] superMethodsSignaturesOut = new String[1][];
        final ElementKind[] kind = new ElementKind[1];

        new TreePathScanner<Void, Void>() {
            @Override public Void visitIdentifier(IdentifierTree node, Void p) {
                handleUsage();
                return super.visitIdentifier(node, p);
            }
            @Override public Void visitMemberSelect(MemberSelectTree node, Void p) {
                handleUsage();
                return super.visitMemberSelect(node, p);
            }
            private void handleUsage() {
                Element el = info.getTrees().getElement(getCurrentPath());

                if (el == null) return;

                long[] span = ResolveService.nameSpan(info, getCurrentPath());

                if (span[0] <= position && position <= span[1]) {
                    fillSignature(el);

                    if (resolveTargetPosition) {
                        TreePath tp = info.getTrees().getPath(el);

                        if (tp != null && tp.getCompilationUnit() == info.getCompilationUnit()) {
                            targetPosition[0] = info.getTrees().getSourcePositions().getStartPosition(tp.getCompilationUnit(), tp.getLeaf());
                        }
                    }
                }
            }
            @Override public Void visitClass(ClassTree node, Void p) {
                handleDeclaration();
                return super.visitClass(node, p);
            }
            @Override public Void visitMethod(MethodTree node, Void p) {
                handleDeclaration();
                return super.visitMethod(node, p);
            }
            @Override public Void visitVariable(VariableTree node, Void p) {
                handleDeclaration();
                return super.visitVariable(node, p);
            }
            private void handleDeclaration() {
                Element el = info.getTrees().getElement(getCurrentPath());

                if (el == null) return;

                long[] span = ResolveService.nameSpan(info, getCurrentPath());

                if (span[2] <= position && position <= span[3]) {
                    fillSignature(el);
                    declaration[0] = true;
                    kind[0] = el.getKind();
                }
            }
            private void fillSignature(Element el) {
                if (JavaUtils.SUPPORTED_KINDS.contains(el.getKind())) {
                    signature[0] = JavaUtils.serialize(ElementHandle.create(el));
                    if (el.getKind() == ElementKind.METHOD) {
                        List<ElementHandle<?>> superMethods = superMethods(info, new HashSet<TypeElement>(), (ExecutableElement) el, (TypeElement) el.getEnclosingElement());

                        String[] superMethodsSignatures = new String[superMethods.size()];
                        int i = 0;

                        for (ElementHandle<?> m : superMethods) {
                            superMethodsSignatures[i++] = JavaUtils.serialize(m);
                        }

                        superMethodsSignaturesOut[0] = superMethodsSignatures;
                    }
                }
            }
        }.scan(info.getCompilationUnit(), null);

        return new ResolvedLocation(signature[0], kind[0], targetPosition[0], declaration[0], superMethodsSignaturesOut[0]);
    }

    //XXX: duplicated here and in RemoteUsages:
    private static List<ElementHandle<?>> superMethods(CompilationInfo info, Set<TypeElement> seenTypes, ExecutableElement baseMethod, TypeElement currentType) {
        if (!seenTypes.add(currentType))
            return Collections.emptyList();

        List<ElementHandle<?>> result = new ArrayList<ElementHandle<?>>();

        for (TypeElement sup : superTypes(info, currentType)) {
            for (ExecutableElement ee : ElementFilter.methodsIn(sup.getEnclosedElements())) {
                if (info.getElements().overrides(baseMethod, ee, (TypeElement) baseMethod.getEnclosingElement())) {
                    result.add(ElementHandle.create(ee));
                }
            }

            result.addAll(superMethods(info, seenTypes, baseMethod, currentType));
        }

        return result;
    }

    private static List<TypeElement> superTypes(CompilationInfo info, TypeElement type) {
        List<TypeElement> superTypes = new ArrayList<TypeElement>();

        for (TypeMirror sup : info.getTypes().directSupertypes(type.asType())) {
            if (sup.getKind() == TypeKind.DECLARED) {
                superTypes.add((TypeElement) ((DeclaredType) sup).asElement());
            }
        }

        return superTypes;
    }


    @GET
    @Path("/declarationSpan")
//    @Produces("text/html")
    public String declarationSpan(@QueryParam("path") String segment, @QueryParam("relative") String relative, @QueryParam("signature") String signature) throws URISyntaxException, IOException, InterruptedException {
        CompilationInfo info = ResolveService.parse(segment, relative);
        long[] span = ResolveService.declarationSpans(info, signature);

        if (span == null) {
            span = new long[] {-1, -1, -1, -1};
        }

        return Pojson.save(span);
    }

    private static class ResolvedLocation {

        private final String signature;
        private final ElementKind kind;
        private final long position;
        private final boolean declaration;
        private final String[] superMethodsSignatures;

        public ResolvedLocation(String signature, ElementKind kind, long position, boolean declaration, String[] superMethodsSignatures) {
            this.signature = signature;
            this.kind = kind;
            this.position = position;
            this.declaration = declaration;
            this.superMethodsSignatures = superMethodsSignatures;
        }
    }
}
