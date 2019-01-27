/*
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

package org.netbeans.jackpot.prs.webapp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GitHub;

/**
 *
 * @author lahvac
 */
@Path("/github/authtarget")
public class WebAppAuthTarget {
    @GET
    public static Response authCallBack(@Context HttpServletRequest request, @Context UriInfo uriInfo, @QueryParam("code") String code, @QueryParam("state") String state) {
        try {
            //TODO: check state
            String clientId = Config.getDefault().getPreferences().node("app").get("client_id", null);
            String clientSecret = Config.getDefault().getPreferences().node("app").get("client_secret", null);
            String params = "client_id=" + clientId + "&client_secret=" + clientSecret + "&code=" + code;
            byte[] data = params.getBytes("UTF-8");
            URL accessTokenURL = new URL("https://github.com/login/oauth/access_token");
            HttpsURLConnection conn = (HttpsURLConnection) accessTokenURL.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded"); 
            conn.setRequestProperty( "charset", "utf-8");
            conn.setRequestProperty( "Content-Length", Integer.toString(data.length));
            conn.getOutputStream().write(data);
            ByteArrayOutputStream inp = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                int read;
                while ((read = in.read()) != (-1)) {
                    inp.write(read);
                }
            }
            String keys = new String(inp.toByteArray(), "UTF-8").replace('&', '\n');
            Properties keysProps = new Properties();
            keysProps.load(new StringReader(keys));
            String oauthToken = keysProps.getProperty("access_token");
            HttpSession session = request.getSession();
            session.setAttribute("access_token", oauthToken);
            GHMyself currentUser = GitHub.connectUsingOAuth(oauthToken).getMyself();
            session.setAttribute("user_name", currentUser.getName());
            session.setAttribute("user_login", currentUser.getLogin());
            //XXX: how to handle the access tokens?
            Config.getDefault().getPreferences().node("users").node(currentUser.getLogin()).put("access_token", oauthToken);
            return Response.seeOther(uriInfo.getBaseUri().resolve("/github")).build();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
