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
package org.netbeans.modules.jackpot.prs.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import io.reflectoring.diffparser.api.model.Hunk;
import io.reflectoring.diffparser.api.model.Line;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities;
import org.netbeans.modules.java.hints.spiimpl.hints.HintsInvoker;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.parsing.impl.indexing.implspi.CacheFolderProvider;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.ModuleInfo;
import org.openide.modules.Places;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public class HandlePullRequest {
    public static void main(String... args) throws Exception {
        String content = System.getenv().get("PR_CONTENT");
        String token = System.getenv().get("OAUTH_TOKEN");
        String appToken = System.getenv().get("OAUTH_APP_TOKEN");
        processPullRequest(content, token, appToken);
    }

    public static void processPullRequest(String inputData, String oauthToken, String oauthAppToken) throws Exception {
        Path tempDir = Files.createTempDirectory("pull.requests");
        File userdir = tempDir.resolve("scratch-user").toFile();
        File cachedir = tempDir.resolve("scratch-cache").toFile();
        System.setProperty("netbeans.user", userdir.getAbsolutePath());
        File varLog = new File(new File(userdir, "var"), "log");
        varLog.mkdirs();
        System.setProperty("jdk.home", System.getProperty("java.home")); //for j2seplatform
        Class<?> main = Class.forName("org.netbeans.core.startup.Main");
        main.getDeclaredMethod("initializeURLFactory").invoke(null);
        new File(cachedir, "index").mkdirs();
//        DefaultCacheFolderProvider.getInstance().setCacheFolder(FileUtil.toFileObject(new File(cachedir, "index")));
        CacheFolderProvider.getCacheFolderForRoot(Places.getUserDirectory().toURI().toURL(), EnumSet.noneOf(CacheFolderProvider.Kind.class), CacheFolderProvider.Mode.EXISTENT);

        Lookup.getDefault().lookup(ModuleInfo.class);

        Map<String, Object> inputParsed = new ObjectMapper().readValue(inputData, Map.class);
        Object action = inputParsed.get("action");
        if (!"opened".equals(action))
            return ;
        Map<String, Object> pullRequest = (Map<String, Object>) inputParsed.get("pull_request");
        if (pullRequest == null) {
            return ;
        }
        Map<String, Object> base = (Map<String, Object>) pullRequest.get("base");
        Map<String, Object> baseRepo = (Map<String, Object>) base.get("repo");
        Map<String, Object> head = (Map<String, Object>) pullRequest.get("head");
        Map<String, Object> headRepo = (Map<String, Object>) head.get("repo");
        
        GitHub statusGithub = oauthToken != null ? GitHub.connectUsingOAuth(oauthToken) : GitHub.connect();
        String fullRepoName = (String) baseRepo.get("full_name");
        GHRepository statusTarget = statusGithub.getRepository(fullRepoName);
        String sha = (String) head.get("sha");
        
        statusTarget.createCommitStatus(sha, GHCommitState.PENDING, null, "Running Jackpot verification");

        String cloneURL = (String) headRepo.get("clone_url");
        new ProcessBuilder("git", "clone", cloneURL, "workdir").directory(tempDir.toFile()).inheritIO().start().waitFor();
        FileObject workdir = FileUtil.toFileObject(tempDir.resolve("workdir").toFile());
        new ProcessBuilder("git", "checkout", sha).directory(FileUtil.toFile(workdir)).inheritIO().start().waitFor();
        Set<Project> projects = new HashSet<>();
        Map<FileObject, FileData> file2Remap = new HashMap<>();
        String diffURL = (String) pullRequest.get("diff_url");
        List<Diff> diffs = new UnifiedDiffParser().parse(new URL(diffURL).openStream().readAllBytes());
        for (Diff diff : diffs) {
            String filename = diff.getToFileName().substring(2);
            if (filename.endsWith(".java")) {
                FileObject file = workdir.getFileObject(filename);
                if (file == null) {
                    //TODO: how to handle? log?
                    continue;
                }
                Project project = FileOwnerQuery.getOwner(file);
                if (project != null) {
                    int[] remap = new int[file.asLines().size()]; //TODO: encoding?
                    Arrays.fill(remap, -1);
                    int idx = 1;
                    for (Hunk hunk : diff.getHunks()) {
                        int pointer = hunk.getToFileRange().getLineStart();
                        for (Line line : hunk.getLines()) {
                            switch (line.getLineType()) {
                                case NEUTRAL: pointer++; idx++; break;
                                case TO: remap[pointer++] = idx++; break;
                            }
                        }
                    }
                    projects.add(project);
                    file2Remap.put(file, new FileData(filename, remap));
                }
            }
        }
        int prId = (int) pullRequest.get("number");
        GHPullRequest[] pr = new GHPullRequest[1];
        boolean[] hasWarnings = {false};
        for (Project project : projects) {
            switch (project.getClass().getName()) {//XXX: ensure that the environment variables are dropped here!
                case "org.netbeans.modules.maven.NbMavenProjectImpl":
                    new ProcessBuilder("mvn", "dependency:go-offline").directory(FileUtil.toFile(project.getProjectDirectory())).inheritIO().start().waitFor();
                    break;
                case "org.netbeans.modules.apisupport.project.NbModuleProject":
                    FileObject nbbuild = project.getProjectDirectory().getFileObject("../../nbbuild");
                    if (nbbuild == null) {
                        nbbuild = project.getProjectDirectory().getFileObject("../nbbuild");
                    }
                    if (nbbuild != null) {
                        new ProcessBuilder("ant", "-autoproxy", "download-all-extbins").directory(FileUtil.toFile(nbbuild)).inheritIO().start().waitFor();
                    }
                    //TODO: download extbins!
                    break;
                default:
                    System.err.println("project name: " + project.getClass().getName());
                    break;
            }
        }
        OpenProjects.getDefault().open(projects.toArray(new Project[0]), false);
        Map<ClasspathInfo, Collection<FileObject>> sorted = BatchUtilities.sortFiles(file2Remap.keySet());
        for (Entry<ClasspathInfo, Collection<FileObject>> e : sorted.entrySet()) {
            JavaSource.create(e.getKey(), e.getValue()).runWhenScanFinished(cc -> {
                FileData fileData = file2Remap.get(cc.getFileObject());

                if (fileData == null)
                    return ;

                cc.toPhase(JavaSource.Phase.RESOLVED); //XXX

                List<ErrorDescription> warnings = new HintsInvoker(HintsSettings.getGlobalSettings(), new AtomicBoolean()).computeHints(cc);
                System.err.println("warnings=" + warnings);
                for (ErrorDescription ed : warnings) {
                    int startLine = ed.getRange().getBegin().getLine() + 1;
                    int targetPosition = fileData.remap[startLine];
                    if (targetPosition == (-1))
                        continue;
                    String comment = "Jackpot:\nwarning: " + ed.getDescription();
                    //TODO: fixes
//                    if (additions != null) {
//                        comment += "```suggestion\n" + additions.toString() + "```";
//                    }
                    hasWarnings[0] = true;
                    if (pr[0] == null) {
                        GitHub commentGithub = oauthAppToken != null ? GitHub.connectUsingOAuth(oauthAppToken) : GitHub.connect();
                        GHRepository commentTarget = commentGithub.getRepository(fullRepoName);
                        pr[0] = commentTarget.getPullRequest(prId);
                    }
                    pr[0].createReviewComment(comment, sha, fileData.filename, targetPosition);
                }
            }, false).get();
        }
//                String file = (String) m.get("file");
//                int startLine = (Integer) m.get("startLine");
//                List<String> fixes = (List<String>) m.get("fixes");
//                StringBuilder additions = null;
//                if (fixes != null && fixes.size() > 1) { //TODO: or == 1?
//                    List<Diff> fixDiffs = new UnifiedDiffParser().parse(fixes.get(0).getBytes("UTF-8"));
//                    if (fixDiffs.size() == 1 && fixDiffs.get(0).getHunks().size() == 1) {
//                        int start = fixDiffs.get(0).getHunks().get(0).getToFileRange().getLineStart();
//                        additions = new StringBuilder();
//                        boolean seenRemoval = false;
//                        for (Line line : fixDiffs.get(0).getHunks().get(0).getLines()) {
//                            if (line.getLineType() == Line.LineType.FROM) {
//                                if (seenRemoval) {
//                                    start = -1;
//                                    break;
//                                } else {
//                                    seenRemoval = true;
//                                }
//                            } else if (line.getLineType() == Line.LineType.TO) {
//                                additions.append(line.getContent());
//                                additions.append("\n");
//                            }
//                        }
//                        if (start != (-1) && seenRemoval) {
//                            startLine = start;
//                        } else {
//                            additions = null;
//                        }
//                    }
//                }
//            }
//        }
//        
        String mainComment;
        if (!hasWarnings[0]) {
            mainComment = "Jackpot: no warnings.";
        } else {
            mainComment = "Jackpot: warnings found.";
        }

        //TODO: set status on crash/error!
        statusTarget.createCommitStatus(sha, GHCommitState.SUCCESS, null, mainComment);
    }
    
    private static final class FileData {
        public final String filename;
        public final int[] remap;

        public FileData(String filename, int[] remap) {
            this.filename = filename;
            this.remap = remap;
        }
        
    }

}
