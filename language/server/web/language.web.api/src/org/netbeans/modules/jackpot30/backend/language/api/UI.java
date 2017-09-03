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

package org.netbeans.modules.jackpot30.backend.language.api;

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.codeviation.pojson.Pojson;
import org.netbeans.modules.jackpot30.backend.base.WebUtilities;
import static org.netbeans.modules.jackpot30.backend.base.WebUtilities.escapeForQuery;

/**
 *
 * @author lahvac
 */
@Path("/index/languageui")
public class UI {

    @GET
    @Path("/search")
    @Produces("text/html")
    public String search(@Context UriInfo uriInfo, @QueryParam("path") String path, @QueryParam("pattern") String pattern, @QueryParam("validate") @DefaultValue("false") boolean validate) throws URISyntaxException, IOException, TemplateException {
        String urlBase = uriInfo.getBaseUri().toString();
        Map<String, Object> configurationData = new HashMap<String, Object>();

        configurationData.put("paths", list(urlBase));
        configurationData.put("selectedPath", path);
        configurationData.put("pattern", pattern);
        configurationData.put("patternEscaped", escapeForQuery(pattern));

        if (pattern != null && path != null) {
            URI u = new URI(urlBase + "index/language/search?path=" + escapeForQuery(path) + "&pattern=" + escapeForQuery(pattern) + "&validate=" + validate);
            List<Map<String, Object>> results = new LinkedList<Map<String, Object>>();
            long queryTime = System.currentTimeMillis();
            List<String> candidates = new ArrayList<String>(WebUtilities.requestStringArrayResponse(u));

            queryTime = System.currentTimeMillis() - queryTime;

            Collections.sort(candidates);

            for (String c : candidates) {
                Map<String, Object> found = new HashMap<String, Object>(3);

                found.put("relativePath", c);

                results.add(found);
            }

            configurationData.put("results", results);

            Map<String, Object> statistics = new HashMap<String, Object>();

            statistics.put("files", candidates.size());
            statistics.put("queryTime", queryTime);

            configurationData.put("statistics", statistics);
        }

        return processTemplate("/org/netbeans/modules/jackpot30/backend/language/api/ui-search.html", configurationData);
    }

    @GET
    @Path("/show")
    @Produces("text/html")
    public Response show(@Context UriInfo uriInfo, @QueryParam("path") String path, @QueryParam("relative") String relativePath, @QueryParam("pattern") String pattern) throws URISyntaxException, IOException, TemplateException {
        String urlBase = uriInfo.getBaseUri().toString();
        URI spansURL = new URI(urlBase + "index/language/searchSpans?path=" + escapeForQuery(path) + "&relativePath=" + escapeForQuery(relativePath) + "&pattern=" + escapeForQuery(pattern));
        return Response.temporaryRedirect(new URI("/index/ui/show?path=" + escapeForQuery(path) + "&relative=" + escapeForQuery(relativePath) + "&highlight=" + escapeForQuery(Pojson.save(parseSpans2(WebUtilities.requestStringResponse(spansURL)))))).build();
    }
    
    @GET
    @Path("/snippet")
    @Produces("text/html")
    public String snippet(@Context UriInfo uriInfo, @QueryParam("path") String path, @QueryParam("relative") String relativePath, @QueryParam("pattern") String pattern) throws URISyntaxException, IOException, TemplateException {
        String urlBase = uriInfo.getBaseUri().toString();
        List<Map<String, String>> snippets = new LinkedList<Map<String, String>>();

        URI codeURL = new URI(urlBase + "index/source/cat?path=" + escapeForQuery(path) + "&relative=" + escapeForQuery(relativePath));
        String code = WebUtilities.requestStringResponse(codeURL);
        URI spansURL = new URI(urlBase + "index/language/searchSpans?path=" + escapeForQuery(path) + "&relativePath=" + escapeForQuery(relativePath) + "&pattern=" + escapeForQuery(pattern));

        for (int[] span : parseSpans(WebUtilities.requestStringResponse(spansURL))) {
            snippets.add(prepareSnippet(code, span));
        }

        return processTemplate("/org/netbeans/modules/jackpot30/backend/language/api/ui-snippet.html", Collections.<String, Object>singletonMap("snippets", snippets));
    }

    private static List<Map<String, String>> list(String urlBase) throws URISyntaxException {
        List<Map<String, String>> result = new LinkedList<Map<String, String>>();

        for (String enc : WebUtilities.requestStringArrayResponse(new URI(urlBase + "index/list"))) {
            Map<String, String> rootDesc = new HashMap<String, String>();
            String[] col = enc.split(":", 2);

            rootDesc.put("segment", col[0]);
            rootDesc.put("displayName", col[1]);
            result.add(rootDesc);
        }

        return result;
    }
    
    private static Iterable<int[]> parseSpans(String from) {
        if (from.isEmpty()) {
            return Collections.emptyList();
        }
        String[] split = from.split(":");
        List<int[]> result = new LinkedList<int[]>();

        for (int i = 0; i < split.length; i += 2) {
            result.add(new int[] {
                Integer.parseInt(split[i + 0].trim()),
                Integer.parseInt(split[i + 1].trim())
            });
        }

        return result;
    }

    private static Iterable<Long> parseSpans2(String from) {
        if (from.isEmpty()) {
            return Collections.emptyList();
        }
        String[] split = from.split(":");
        List<Long> result = new LinkedList<Long>();

        for (String s : split) {
            result.add(Long.parseLong(s.trim()));
        }

        return result;
    }

    private static final int DESIRED_CONTEXT = 2;

    private static Map<String, String> prepareSnippet(String code, int[] span) {
        int grandStart = span[0];
        int firstLineStart = grandStart = lineStart(code, grandStart);

        while (grandStart > 0 && contextLength(code.substring(grandStart, firstLineStart)) < DESIRED_CONTEXT)
            grandStart = lineStart(code, grandStart - 1);

        int grandEnd = span[1];
        int firstLineEnd = grandEnd = lineEnd(code, grandEnd);
        
        while (grandEnd < code.length() - 1 && contextLength(code.substring(firstLineEnd, grandEnd)) < DESIRED_CONTEXT)
            grandEnd = lineEnd(code, grandEnd + 1);

        Map<String, String> result = new HashMap<String, String>();
        
        result.put("prefix", WebUtilities.escapeForHTMLElement(code.substring(grandStart, span[0])));
        result.put("occurrence", WebUtilities.escapeForHTMLElement(code.substring(span[0], span[1])));
        result.put("suffix", WebUtilities.escapeForHTMLElement(code.substring(span[1], grandEnd)));

        return result;
    }

    private static int lineStart(String code, int o) {
        while (o > 0 && code.charAt(o) != '\n') {
            o--;
        }

        return o;
    }

    private static int lineEnd(String code, int o) {
        while (o < code.length() - 1 && code.charAt(o) != '\n') {
            o++;
        }

        return o;
    }

    private static int contextLength(String in) {
        return in.replaceAll("\n[ \t]*\n", "\n").trim().split("\n").length;
    }

    private static String processTemplate(String template, Map<String, Object> configurationData) throws TemplateException, IOException {
        Configuration conf = new Configuration();

        conf.setTemplateLoader(new TemplateLoaderImpl());

        Template templ = conf.getTemplate(template);
        StringWriter out = new StringWriter();

        templ.process(configurationData, out);

        return out.toString();
    }

    private static final class TemplateLoaderImpl implements TemplateLoader {

        public Object findTemplateSource(String name) throws IOException {
            return TemplateLoaderImpl.class.getResourceAsStream("/" + name);
        }

        public long getLastModified(Object templateSource) {
            return 0L;
        }

        public Reader getReader(Object templateSource, String encoding) throws IOException {
            InputStream in = (InputStream) templateSource;

            return new InputStreamReader(in);
        }

        public void closeTemplateSource(Object templateSource) throws IOException {
        }
    }

}
