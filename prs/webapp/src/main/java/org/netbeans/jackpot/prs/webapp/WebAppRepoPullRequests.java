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
            try (DirectoryStream<java.nio.file.Path> ds = Files.newDirectoryStream(targetDir)) {
                for (java.nio.file.Path p : ds) {
                    String pr = p.getFileName().toString();
                    page.append("<li>");
                    page.append(pr);
                    page.append("&nbsp;");
                    if (Files.exists(p.resolve("preparing"))) {
                        page.append("preparing");
                    }
                    if (Files.exists(p.resolve("running"))) {
                        page.append("running");
                    }
                    if (Files.exists(p.resolve("finished"))) {
                        page.append("finished");
                    }
                    if (Files.exists(p.resolve("stdout")) || Files.exists(p.resolve("stdout.gz"))) {
                        page.append("<a href=\"/github/repopullrequests/stdout?repositoryName=" + repositoryName + "&pr=" + pr + "\">stdout</a>");
                    }
                    if (Files.exists(p.resolve("stderr")) || Files.exists(p.resolve("stderr.gz"))) {
                        page.append("<a href=\"/github/repopullrequests/stderr?repositoryName=" + repositoryName + "&pr=" + pr + "\">stderr</a>");
                    }
                    page.append("</li>");
                }
            } catch (IOException ex) {
                WebApp.LOG.log(Level.FINE, null, ex);
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
    public static Response stdout(@Context HttpServletRequest request, @QueryParam("repositoryName") String repositoryName, @QueryParam("pr") String pr) throws IOException {
        return log(request, repositoryName, pr, "stdout");
    }

    @GET
    @Path("/stderr")
    public static Response stderr(@Context HttpServletRequest request, @QueryParam("repositoryName") String repositoryName, @QueryParam("pr") String pr) throws IOException {
        return log(request, repositoryName, pr, "stderr");
    }

    private static Response log(HttpServletRequest request, String repositoryName, String pr, String log) throws IOException {
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
                    java.nio.file.Path logFile = Config.getDefault().getRunDir().resolve("github").resolve(repositoryName).resolve(pr).resolve(log);
                    try (InputStream in = Files.newInputStream(logFile)) {
                        in.transferTo(out);
                    } catch (IOException ex) {
                        java.nio.file.Path logFileGZ = Config.getDefault().getRunDir().resolve("github").resolve(repositoryName).resolve(pr).resolve(log + ".gz");
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
