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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author lahvac
 */
@Path("/github/notify")
public class WebAppNotify {
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public static void webhook(String data) throws IOException {
        Map<String, Object> inputParsed = new ObjectMapper().readValue(data, Map.class);
        Object action = inputParsed.get("action");
        if (!"opened".equals(action))
            return ;
        Map<String, Object> pullRequest = (Map<String, Object>) inputParsed.get("pull_request");
        if (pullRequest == null) {
            return ;
        }
        Map<String, Object> repository = (Map<String, Object>) inputParsed.get("repository");
        if (repository == null) {
            return ;
        }
        String[] userAndRepo = ((String) repository.get("full_name")).split("/");
        Preferences repositories = Config.getDefault().getPreferences().node("users").node(userAndRepo[0]).node("repositories");
        if (!repositories.getBoolean(userAndRepo[1], false)) {
            return ;
        }
        Preferences handlerPrefs = Config.getDefault().getPreferences().node("handler");
        String handler = handlerPrefs.get("handler", "handler.local");
        String remoteHost = handlerPrefs.get("remoteHost", null);
        String remotePath = handlerPrefs.get("remotePath", null);
        ProcessBuilder builder;
        if (remoteHost != null && remotePath != null) {
            builder = new ProcessBuilder(System.getProperty("install.dir") + "/handler/bin/handler.remote", remoteHost, remotePath, handler);
        } else {
            builder = new ProcessBuilder(System.getProperty("install.dir") + "/handler/bin/" + handler);
        }
        builder.environment().put("PR_CONTENT", data);
        //XXX: how to handle the access tokens?
        builder.environment().put("OAUTH_TOKEN", Config.getDefault().getPreferences().node("users").node(userAndRepo[0]).get("access_token", ""));
        builder.environment().put("OAUTH_APP_TOKEN", Config.getDefault().getPreferences().node("app").get("access_token", ""));
        //TODO: logs!
        builder.inheritIO().start();
    }
}
