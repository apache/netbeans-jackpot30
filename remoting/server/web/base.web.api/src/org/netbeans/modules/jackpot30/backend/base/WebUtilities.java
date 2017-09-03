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

package org.netbeans.modules.jackpot30.backend.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class WebUtilities {

    private WebUtilities() {
    }

    public static String requestStringResponse (URI uri) {
        final StringBuffer sb = new StringBuffer ();
        final URL url;
        try {
            url = uri.toURL();
            final URLConnection urlConnection = url.openConnection ();
            urlConnection.connect ();
            final Object content = urlConnection.getContent ();
//            System.out.println (content);
//            System.out.println (content.getClass ());
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
        } catch (IOException e) {
            e.printStackTrace ();  // TODO
            return null;
        }
        return sb.toString ();
    }
    
    public static Collection<? extends String> requestStringArrayResponse (URI uri) {
        final List<String> result = new LinkedList<String> ();
        final URL url;
        try {
            url = uri.toURL();
            final URLConnection urlConnection = url.openConnection ();
            urlConnection.connect ();
            final Object content = urlConnection.getContent ();
//            System.out.println (content);
//            System.out.println (content.getClass ());
            final InputStream inputStream = (InputStream) content;
            final BufferedReader reader = new BufferedReader (new InputStreamReader (inputStream, "ASCII"));
            try {
                for (;;) {
                    String line = reader.readLine ();
                    if (line == null)
                        break;
                    result.add (line);
                }
            } finally {
                reader.close ();
            }
        } catch (IOException e) {
            e.printStackTrace ();  // TODO
        }
        return result;
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
