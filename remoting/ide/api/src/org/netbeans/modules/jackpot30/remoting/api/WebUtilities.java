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

package org.netbeans.modules.jackpot30.remoting.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.annotations.common.CheckForNull;
import org.openide.util.RequestProcessor;
import org.openide.util.RequestProcessor.Task;

/**
 *
 */
public class WebUtilities {

    private WebUtilities() {
    }

    private static final RequestProcessor LOADER = new RequestProcessor(WebUtilities.class.getName(), 100, true, false);

    public static @CheckForNull String requestStringResponse (final URI uri) {
        return requestStringResponse(uri, new AtomicBoolean());
    }

    public static @CheckForNull String requestStringResponse (final URI uri, AtomicBoolean cancel) {
        final String[] result = new String[1];
        final RuntimeException[] re = new RuntimeException[1];
        final Error[] err = new Error[1];
        Task task = LOADER.create(new Runnable() {
            @Override
            public void run() {
        final StringBuffer sb = new StringBuffer ();
        final URL url;
        try {
            url = uri.toURL();
            final URLConnection urlConnection = url.openConnection ();
            urlConnection.connect ();
            final Object content = urlConnection.getContent ();
            final InputStream inputStream = (InputStream) content;
            final BufferedReader reader = new BufferedReader (new InputStreamReader (inputStream, "ASCII"));
            try {
                for (;;) {
                    String line = reader.readLine ();
                    if (line == null)
                        break;
                    sb.append (line).append ('\n');
                }
            } finally {
                reader.close ();
            }
            result[0] = sb.toString();
        } catch (IOException e) {
            Logger.getLogger(WebUtilities.class.getName()).log(Level.INFO, uri.toASCIIString(), e);
        } catch (RuntimeException ex) {
            re[0] = ex;
        } catch (Error ex) {
            err[0] = ex;
        }
            }
        });

        task.schedule(0);
        
        while (!cancel.get()) {
            try {
                if (task.waitFinished(1000)) {
                    if (re[0] != null) throw re[0];
                    else if (err[0] != null) throw err[0];
                    else return result[0];
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(WebUtilities.class.getName()).log(Level.FINE, null, ex);
            }
        }
        return null;
    }

    public static Collection<? extends String> requestStringArrayResponse (URI uri) {
        return requestStringArrayResponse(uri, new AtomicBoolean());
    }

    public static Collection<? extends String> requestStringArrayResponse (URI uri, AtomicBoolean cancel) {
        String content = requestStringResponse(uri, cancel);
        
        if (content == null) return null;
        
        return Arrays.asList(content.split("\n"));
    }

    private static String[] c = new String[] {"&", "<", ">", "\n", "\""}; // NOI18N
    private static String[] tags = new String[] {"&amp;", "&lt;", "&gt;", "<br>", "&quot;"}; // NOI18N

    public static String escapeForHTMLElement(String input) {
        for (int cntr = 0; cntr < c.length; cntr++) {
            input = input.replaceAll(c[cntr], tags[cntr]);
        }

        return input;
    }

    public static String escapeForQuery(String pattern) throws URISyntaxException {
        if (pattern == null) return null;
        return new URI(null, null, null, -1, null, pattern, null).getRawQuery().replaceAll(Pattern.quote("&"), Matcher.quoteReplacement("%26"));
    }

}
