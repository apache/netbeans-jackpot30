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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 *
 * @author lahvac
 */
@Path("/github/enableDisable")
public class WebAppEnableDisable {
    private static final String NOTIFY_HOOK_URL = "http://jackpot.findusages.cloud/github/notify";
    @GET
    public static void authCallBack(@Context HttpServletRequest request, @QueryParam("repo") String repo) {
        try {
            String[] userAndRepo = repo.split("/");
            Preferences repositories = Config.getDefault().getPreferences().node("users").node(userAndRepo[0]).node("repositories");
            boolean enabled = !repositories.getBoolean(userAndRepo[1], false);
            repositories.putBoolean(userAndRepo[1], enabled);
            String accessToken = (String) request.getSession().getAttribute("access_token");
            GHRepository repository = GitHub.connectUsingOAuth(accessToken).getRepository(repo);
            Optional<GHHook> hook = repository.getHooks().stream().filter(h -> NOTIFY_HOOK_URL.equals(h.getConfig().get("url"))).findAny();
            if (hook.isPresent()) {
                if (!enabled) {
                    //TODO: would be better to simply deactive the hook
                    hook.get().delete();
                }
            } else {
                if (enabled) {
                    Map<String, String> config = new HashMap<>();
                    config.put("url", NOTIFY_HOOK_URL);
                    config.put("content_type", "json");
                    config.put("content_type", "json");
                    repository.createHook("web", config, EnumSet.of(GHEvent.PULL_REQUEST), true);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
