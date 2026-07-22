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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import java.util.zip.GZIPOutputStream;
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
        if (!"opened".equals(action) && !"synchronize".equals(action))
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
        String prName = (String) pullRequest.getOrDefault("title", "");
        String prUser = (String) ((Map<String, Object>) pullRequest.getOrDefault("user", Collections.emptyMap())).getOrDefault("login", "");
        String prURL = (String) pullRequest.getOrDefault("url", "");
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
        java.nio.file.Path targetDir = Config.getDefault().getRunDir().resolve("github").resolve((String) repository.get("full_name"));
        java.nio.file.Path thisPRDir = targetDir.resolve(String.valueOf((Integer) pullRequest.get("number")));
        java.nio.file.Path buildInfo = thisPRDir.resolve("info");
        Properties infoProps = new Properties();
        if (Files.isReadable(buildInfo)) {
            try (InputStream in = Files.newInputStream(buildInfo)) {
                infoProps.load(in);
            }
        }
        int buildNumber = 1;
        try {
            buildNumber = Integer.parseInt(infoProps.getProperty("nextBuild", "1"));
        } catch (IllegalArgumentException ex) {
            //ignore
        }
        infoProps.setProperty("nextBuild", String.valueOf(buildNumber + 1));
        infoProps.setProperty("name", prName);
        infoProps.setProperty("user", prUser);
        infoProps.setProperty("url", prURL);
        Files.createDirectories(buildInfo.getParent());
        try (OutputStream out = Files.newOutputStream(buildInfo)) {
            infoProps.store(out, "");
        }
        java.nio.file.Path thisRunDir = thisPRDir.resolve(String.valueOf(buildNumber));
        Files.createDirectories(thisRunDir);
        Files.deleteIfExists(thisRunDir.resolve("finished"));
        Files.newOutputStream(thisRunDir.resolve("preparing")).close();
        java.nio.file.Path stdout = thisRunDir.resolve("stdout");
        builder.redirectOutput(stdout.toFile());
        java.nio.file.Path stderr = thisRunDir.resolve("stderr");
        builder.redirectError(stderr.toFile());
        Process process = builder.start();
        Files.newOutputStream(thisRunDir.resolve("running")).close();
        Files.delete(thisRunDir.resolve("preparing"));
        new Thread(() -> {
            while (true) {
                try {
                    process.waitFor();
                    break;
                } catch (InterruptedException ex) {
                    //ignore...
                }
            }
            try {
                Files.newOutputStream(thisRunDir.resolve("finished")).close();
            } catch (IOException ex) {
                WebApp.LOG.log(Level.SEVERE, null, ex);
            }
            try {
                Files.delete(thisRunDir.resolve("running"));
            } catch (IOException ex) {
                WebApp.LOG.log(Level.SEVERE, null, ex);
            }
            pack(stdout);
            pack(stderr);
        }).start();
    }

    private static void pack(java.nio.file.Path log) {
        java.nio.file.Path logGZ = log.getParent().resolve(log.getFileName() + ".gz");
        try (InputStream in = Files.newInputStream(log);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(logGZ))) {
            int r;

            while ((r = in.read()) != (-1)) {
                out.write(r);
            }

            Files.delete(log);
        } catch (IOException ex) {
            WebApp.LOG.log(Level.SEVERE, null, ex);
        }
    }
}
