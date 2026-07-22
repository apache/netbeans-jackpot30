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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

/**
 *
 * @author lahvac
 */
@Path("/github/repopullrequests")
public class WebAppRepoPullRequests {
    @GET
    @Produces(MediaType.TEXT_HTML)
    public static String repoPullRequests(@Context HttpServletRequest request, @QueryParam("repositoryName") String repositoryName) throws IOException {
        String userName = (String) request.getSession().getAttribute("user_name");
        if (userName != null) {
            if (repositoryName.startsWith(userName + "/")) {
                return "<html>" +
                       "<body>" +
                       "    Repository: " + repositoryName + " not owned by: " + userName +
                       "</body>";
            }
            StringBuilder page = new StringBuilder();
            page.append("<html>");
            page.append("<body>");
            page.append("Pull Requests of: " + repositoryName);
            page.append("<ul>");
            java.nio.file.Path targetDir = Config.getDefault().getRunDir().resolve("github").resolve(repositoryName);
            List<String> prs = new ArrayList<>();
            try (DirectoryStream<java.nio.file.Path> ds = Files.newDirectoryStream(targetDir)) {
                for (java.nio.file.Path p : ds) {
                    prs.add(p.getFileName().toString());
                }
            } catch (IOException ex) {
                WebApp.LOG.log(Level.FINE, null, ex);
            }
            Collections.sort(prs);
            for (String pr : prs) {
                java.nio.file.Path p = targetDir.resolve(pr);
                java.nio.file.Path buildInfo = p.resolve("info");
                Properties infoProps = new Properties();
                if (Files.isReadable(buildInfo)) {
                    try (InputStream in = Files.newInputStream(buildInfo)) {
                        infoProps.load(in);
                    }
                }
                page.append("<li>Pull Request #");
                page.append(pr);
                page.append(": ");
                page.append(infoProps.get("name"));
                page.append(" by ");
                page.append(infoProps.get("user"));
                page.append("<ul>");
                List<String> builds = new ArrayList<>();
                try (DirectoryStream<java.nio.file.Path> buildStream = Files.newDirectoryStream(p)) {
                    for (java.nio.file.Path b : buildStream) {
                        if (!Files.isDirectory(b)) continue;
                        builds.add(b.getFileName().toString());
                    }
                }
                Collections.sort(builds);
                for (String build : builds) {
                    java.nio.file.Path b = p.resolve(build);
                    page.append("<li>Build #");
                    page.append(build);
                    page.append("&nbsp;");
                    if (Files.exists(b.resolve("preparing"))) {
                        page.append("preparing");
                    }
                    if (Files.exists(b.resolve("running"))) {
                        page.append("running");
                    }
                    if (Files.exists(b.resolve("finished"))) {
                        page.append("finished");
                    }
                    if (Files.exists(b.resolve("stdout")) || Files.exists(b.resolve("stdout.gz"))) {
                        page.append("<a href=\"/github/repopullrequests/stdout?repositoryName=" + repositoryName + "&pr=" + pr + "&build=" + build + "\">stdout</a>");
                    }
                    if (Files.exists(b.resolve("stderr")) || Files.exists(b.resolve("stderr.gz"))) {
                        page.append("<a href=\"/github/repopullrequests/stderr?repositoryName=" + repositoryName + "&pr=" + pr + "&build=" + build + "\">stderr</a>");
                    }
                }
                page.append("</ul>");
                page.append("</li>");
            }
            page.append("</ul>");
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

    @GET
    @Path("/stdout")
    public static Response stdout(@Context HttpServletRequest request, @QueryParam("repositoryName") String repositoryName, @QueryParam("pr") String pr, @QueryParam("build") String build) throws IOException {
        return log(request, repositoryName, pr, build, "stdout");
    }

    @GET
    @Path("/stderr")
    public static Response stderr(@Context HttpServletRequest request, @QueryParam("repositoryName") String repositoryName, @QueryParam("pr") String pr, @QueryParam("build") String build) throws IOException {
        return log(request, repositoryName, pr, build, "stderr");
    }

    private static Response log(HttpServletRequest request, String repositoryName, String pr, String build, String log) throws IOException {
        String userName = (String) request.getSession().getAttribute("user_name");
        if (userName != null) {
            if (repositoryName.startsWith(userName + "/")) {
                return Response.ok("<html>" +
                                   "<body>" +
                                   "    Repository: " + repositoryName + " not owned by: " + userName +
                                   "</body>",
                                   MediaType.TEXT_HTML)
                               .build();
            }
            class StreamingOutputImpl implements StreamingOutput {
                @Override
                public void write(OutputStream out) throws IOException, WebApplicationException {
                    java.nio.file.Path logFile = Config.getDefault().getRunDir().resolve("github").resolve(repositoryName).resolve(pr).resolve(build).resolve(log);
                    try (InputStream in = Files.newInputStream(logFile)) {
                        in.transferTo(out);
                    } catch (IOException ex) {
                        java.nio.file.Path logFileGZ = Config.getDefault().getRunDir().resolve("github").resolve(repositoryName).resolve(pr).resolve(build).resolve(log + ".gz");
                        try (InputStream in = new GZIPInputStream(Files.newInputStream(logFileGZ))) {
                            in.transferTo(out);
                        }
                    }
                }
            }
            return Response.ok(new StreamingOutputImpl(), MediaType.TEXT_PLAIN)
                           .build();
        } else {
            String clientId = Config.getDefault().getPreferences().node("app").get("client_id", null);
            return Response.ok("<html>" +
                               "<body>" +
                               "    <a href=\"https://github.com/login/oauth/authorize?client_id=" + clientId + "&scope=write:repo_hook%20repo:status&state=9843759384\">Login with GitHub.</a>" +
                               "</body>",
                               MediaType.TEXT_HTML)
                           .build();

        }
    }
}
