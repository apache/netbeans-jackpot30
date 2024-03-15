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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.queries.BinaryForSourceQuery;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.jackpot.prs.handler.impl.SiteWrapper.Factory;
import org.netbeans.modules.jackpot.prs.handler.impl.SiteWrapper.ReviewComment;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities;
import org.netbeans.modules.java.hints.spiimpl.hints.HintsInvoker;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.java.queries.BinaryForSourceQueryImplementation;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.LifecycleManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=OptionProcessor.class)
public class HandlePullRequest extends OptionProcessor {
    
    private static final Option HANDLE_PULL_REQUEST = Option.withoutArgument('\0', "handle-pull-request");
    public static Factory factory = GHSite.FACTORY;

    protected Set<Option> getOptions() {
        return Collections.singleton(HANDLE_PULL_REQUEST);
    }
    
    protected void process(Env env, Map<Option,String[]> optionValues) throws CommandException {
        try {
            String content = System.getenv().get("PR_CONTENT");
            String token = System.getenv().get("OAUTH_TOKEN");
            String appToken = System.getenv().get("OAUTH_APP_TOKEN");
            processPullRequest(content, token, appToken);
            LifecycleManager.getDefault().exit();
        } catch (Throwable ex) {
            System.err.println("error:");
            ex.printStackTrace();
            throw (CommandException) new CommandException(1).initCause(ex);
        }
    }

    public static void processPullRequest(String inputData, String oauthToken, String oauthAppToken) throws Exception {
        File tempDir = Places.getCacheSubdirectory("checkout");
        Map<String, Object> inputParsed = new ObjectMapper().readValue(inputData, Map.class);
        Object action = inputParsed.get("action");
        if (!"opened".equals(action) && !"synchronize".equals(action))
            return ;
        Map<String, Object> pullRequest = (Map<String, Object>) inputParsed.get("pull_request");
        if (pullRequest == null) {
            return ;
        }
        Map<String, Object> base = (Map<String, Object>) pullRequest.get("base");
        Map<String, Object> baseRepo = (Map<String, Object>) base.get("repo");
        Map<String, Object> head = (Map<String, Object>) pullRequest.get("head");
        Map<String, Object> headRepo = (Map<String, Object>) head.get("repo");
        
        SiteWrapper statusGithub = factory.create(oauthToken);
        String fullRepoName = (String) baseRepo.get("full_name");
        String sha = (String) head.get("sha");
        
        statusGithub.createCommitStatusPending(fullRepoName, sha, "Running Jackpot verification");

        String cloneURL = (String) headRepo.get("clone_url");
        new ProcessBuilder("git", "clone", cloneURL, "workdir").directory(tempDir).inheritIO().start().waitFor();
        FileObject workdir = FileUtil.toFileObject(new File(tempDir, "workdir"));
        new ProcessBuilder("git", "checkout", sha).directory(FileUtil.toFile(workdir)).inheritIO().start().waitFor();
        FileObject jlObject = workdir.getFileObject("src/java.base/share/classes/java/lang/Object.java");
        if (jlObject != null) {
            Project prj = FileOwnerQuery.getOwner(jlObject); //ensure external roots (tests) are registered
            ProjectUtils.getSources(prj).getSourceGroups(Sources.TYPE_GENERIC); //register external roots
        }
        FileObject jlmSourceVersion = workdir.getFileObject("src/java.compiler/share/classes/javax/lang/model/SourceVersion.java");
        if (jlmSourceVersion != null) {
            Project prj = FileOwnerQuery.getOwner(jlmSourceVersion); //ensure external roots (tests) are registered
            ProjectUtils.getSources(prj).getSourceGroups(Sources.TYPE_GENERIC); //register external roots
        }
        Set<Project> projects = new HashSet<>();
        Map<FileObject, FileData> file2Remap = new HashMap<>();
        String diffURL = (String) pullRequest.get("diff_url");
        URLConnection conn = new URL(diffURL).openConnection();
        String text;
        try (InputStream in = conn.getInputStream()) {
            String encoding = conn.getContentEncoding();
            if (encoding == null) encoding = "UTF-8";
            //workaround for the diff parser, which does not handle git diffs properly:
            text = new String(in.readAllBytes(), encoding)
                    .replace("\ndiff --git", "\n\ndiff --git");
        }
        List<Diff> diffs = new UnifiedDiffParser().parse(text.getBytes());
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
                    int[] remap = new int[file.asLines().size() + 1]; //TODO: encoding?
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
                } else {
                    System.err.println("no project found for: " + filename);
                }
            }
        }
        int prId = (int) pullRequest.get("number");
        SiteWrapper[] commentGitHub = new SiteWrapper[1];
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
                    if (nbbuild != null) { //TODO: only once
                        new ProcessBuilder("ant", "-autoproxy", "download-all-extbins").directory(FileUtil.toFile(nbbuild)).inheritIO().start().waitFor();
                    }
                    //TODO: download extbins!
                    break;
                case "org.netbeans.modules.java.openjdk.project.JDKProject":
                    //no bootstrap at this time
                    break;
                default:
                    System.err.println("project name: " + project.getClass().getName());
                    break;
            }
        }
        OpenProjects.getDefault().open(projects.toArray(new Project[0]), false);
        Map<ClasspathInfo, Collection<FileObject>> sorted = BatchUtilities.sortFiles(file2Remap.keySet());
        AtomicReference<List<ReviewComment>> reviewComments = new AtomicReference<>();
        for (Entry<ClasspathInfo, Collection<FileObject>> e : sorted.entrySet()) {
            System.err.println("Running hints for:");
            System.err.println("files: " + e.getValue());
            System.err.println("classpath: " + e.getKey());
            try {
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
                        if (commentGitHub[0] == null) {
                            commentGitHub[0] = factory.create(oauthAppToken);
                            reviewComments.set(commentGitHub[0].getReviewComments(fullRepoName, prId));
                        }
                        boolean writeComment = true;
                        for (ReviewComment rc : reviewComments.get()) {
                            if (fileData.filename.equals(rc.filename) &&
                                targetPosition == rc.linenumber &&
                                comment.equals(rc.comment)) {
                                //skip comment:
                                writeComment = false;
                                break;
                            }
                        }
                        if (writeComment) {
                            commentGitHub[0].createReviewComment(fullRepoName, prId, comment, sha, fileData.filename, targetPosition);
                        }
                    }
                }, false).get();
            } catch (Throwable ex) {
                System.err.println("error while processing: " + e.getValue());
                Exceptions.printStackTrace(ex);
            }
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
        statusGithub.createCommitStatusSuccess(fullRepoName, sha, mainComment);
    }
    
    private static final class FileData {
        public final String filename;
        public final int[] remap;

        public FileData(String filename, int[] remap) {
            this.filename = filename;
            this.remap = remap;
        }
        
    }

    //if java.base/share/classes indexing fails, copy classfiles from the default JDK platform
    //these will typically not match the sources, but if the classfiles would be missing
    //altogether, all subsequent indexing would fail as well:
    @ServiceProvider(service=BinaryForSourceQueryImplementation.class, position=0)
    public static class BinaryForSourceQueryImpl implements BinaryForSourceQueryImplementation {

        @Override
        public BinaryForSourceQuery.Result findBinaryRoots(URL sourceRoot) {
            if (sourceRoot.toString().contains("src/java.base/share/classes")) {
                return new BinaryForSourceQuery.Result() {
                    @Override
                    public URL[] getRoots() {
                        return JavaPlatform.getDefault().getBootstrapLibraries().entries().stream().map(e -> e.getURL()).filter(u -> "java.base".equals(SourceUtils.getModuleName(u))).toArray(s -> new URL[s]);
                    }

                    @Override
                    public void addChangeListener(ChangeListener l) {
                    }

                    @Override
                    public void removeChangeListener(ChangeListener l) {
                    }
                };
            }
            return null;
        }
        
    }
}
