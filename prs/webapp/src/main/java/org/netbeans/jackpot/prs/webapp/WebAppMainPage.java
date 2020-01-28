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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 *
 * @author lahvac
 */
@Path("/github")
public class WebAppMainPage {
    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public static String main(@Context HttpServletRequest request) {
        String userName = (String) request.getSession().getAttribute("user_name");
        if (userName != null) {
            Set<String> allowedUsers = new HashSet<>(Arrays.asList(Config.getDefault().getPreferences().node("app").get("allowed_users", "").split(",")));
            String userLogin = (String) request.getSession().getAttribute("user_login");
            if (!allowedUsers.contains(userLogin)) {
                StringBuilder page = new StringBuilder();
                page.append("<html>");
                page.append("<body>");
                page.append("    Sorry, this site is only for invited users at this point.");
                page.append("</body>");
                return page.toString();
            }
            StringBuilder page = new StringBuilder();
            page.append("<html>");
            try {
                String accessToken = (String) request.getSession().getAttribute("access_token");
                page.append("<body>");
                page.append("<script>");
                page.append("function enableDisable(repo) {");
                page.append("    var xhr = new XMLHttpRequest();");
                page.append("    xhr.open('GET', '/github/enableDisable?repo=' + repo, true);");
                page.append("    xhr.send();");
                page.append("}");
                page.append("</script>");
                page.append("    <a href=\"\">" + userName + "</a>");
                page.append("Repositories:");
                page.append("<ul>");
                GitHub github = GitHub.connectUsingOAuth(accessToken);
                Preferences repositories = Config.getDefault().getPreferences().node("users").node(userLogin).node("repositories");
                for (Entry<String, GHRepository> e : github.getMyself().getRepositories().entrySet()) {//TODO: use list
                    String checked;
                    if (repositories.getBoolean(e.getValue().getName(), false)) {
                        checked = " checked='true'";
                    } else {
                        checked = "";
                    }
                    page.append("<li><input type='checkbox' onclick='enableDisable(\"" + userLogin + "/" + e.getValue().getName() + "\")'" + checked + "/><a href=\"/github/repopullrequests?repositoryName=" + userLogin + "/" + e.getValue().getName() + "\">" + e.getValue().getName() + "</a></li>");
                }
                page.append("</ul>");
                Set<String> admins = Set.of(Config.getDefault().getPreferences().node("app").get("admins", "").split(","));
                if (admins.contains(userLogin)) {
                    page.append("Remaining rate limit: " + github.rateLimit().toString());
                }
            } catch (IOException ex) {
                //TODO: handle
                ex.printStackTrace();
            }
            page.append("</body>");
            return page.toString();
        } else {
            String clientId = Config.getDefault().getPreferences().node("app").get("client_id", null);
            return "<html>" +
                   "<body>" +
                   "    <a href=\"https://github.com/login/oauth/authorize?client_id=" + clientId + "&scope=write:repo_hook%20repo:status&state=9843759384\">Login with GitHub.</a>" +
                   "</body>";
        }
    }
}
