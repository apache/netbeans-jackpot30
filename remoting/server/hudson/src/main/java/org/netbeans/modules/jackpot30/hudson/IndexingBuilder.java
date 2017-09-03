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

package org.netbeans.modules.jackpot30.hudson;

import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author lahvac
 */
public class IndexingBuilder extends Builder {

    private final String projectName;
    private final String toolName;
    private final String indexSubDirectory;
    private final String ignorePatterns;
    
    public IndexingBuilder(StaplerRequest req, JSONObject json) throws FormException {
        projectName = json.getString("projectName");
        toolName = json.optString("toolName", IndexingTool.DEFAULT_INDEXING_NAME);
        indexSubDirectory = json.optString("indexSubDirectory", "");
        ignorePatterns = json.optString("ignorePatterns", "");
    }

    @DataBoundConstructor
    public IndexingBuilder(String projectName, String toolName, String indexSubDirectory, String ignorePatterns) {
        this.projectName = projectName;
        this.toolName = toolName;
        this.indexSubDirectory = indexSubDirectory;
        this.ignorePatterns = ignorePatterns != null ? ignorePatterns : "";
    }

    public String getProjectName() {
        return projectName;
    }

    public String getToolName() {
        return toolName;
    }

    public String getIndexSubDirectory() {
        return indexSubDirectory;
    }

    public String getIgnorePatterns() {
        return ignorePatterns;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        IndexingTool t = findSelectedTool();

        if (t == null) {
            listener.getLogger().println("Cannot find indexing tool: " + toolName);
            return false;
        }

        t = t.forNode(build.getBuiltOn(), listener);

        listener.getLogger().println("Looking for projects in: " + build.getWorkspace().getRemote());

        FilePath base = indexSubDirectory == null || indexSubDirectory.isEmpty() ? build.getWorkspace() : build.getWorkspace().child(indexSubDirectory); //XXX: child also supports absolute paths! absolute paths should not be allowed here (for security)
        RemoteResult res = base.act(new FindProjects(t.getHome(), getIgnorePatterns()));

        listener.getLogger().println("Running: " + toolName + " on projects: " + res);

        String codeName = codeNameForJob(build.getParent());
        ArgumentListBuilder args = new ArgumentListBuilder();
        FilePath targetZip = build.getBuiltOn().getRootPath().createTempFile(codeName, "zip");

        //XXX: there should be a way to specify Java runtime!
        args.add(new File(t.getHome(), "index.sh")); //XXX
        args.add(codeName);
        args.add(projectName); //XXX
        args.add(targetZip);
        args.add(res.root);
        args.add(res.foundProjects.toArray(new String[0]));

        Proc indexer = launcher.launch().pwd(base)
                                        .cmds(args)
                                        .envs("JPT30_INFO=BUILD_ID=" + build.getNumber())
                                        .stdout(listener)
                                        .start();

        indexer.join();

        InputStream ins = targetZip.read();

        try {
            UploadIndex.uploadIndex(codeName, ins);
        } finally {
            ins.close();
            targetZip.delete();
        }

        return true;
    }

    public IndexingTool findSelectedTool() {
        for (IndexingTool t : getDescriptor().getIndexingTools()) {
            if (toolName.equals(t.getName())) return t;
        }

        return null;
    }

    private static void findProjects(File root, Collection<String> result, Iterable<Pattern> markers, Iterable<Pattern> ignores, Pattern perProjectIgnore, StringBuilder relPath) {
        int len = relPath.length();
        boolean first = relPath.length() == 0;

        for (Pattern marker : markers) {
            Matcher m = marker.matcher(relPath);

            if (m.matches()) {
                if (perProjectIgnore == null || !perProjectIgnore.matcher(relPath).matches()) {
                    result.add(m.group(1));
                }
                break;
            }
        }

        File[] children = root.listFiles();

        if (children != null) {
            Arrays.sort(children, new Comparator<File>() {
                public int compare(File o1, File o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            OUTER: for (File c : children) {
                for (Pattern ignore : ignores) {
                    if (ignore.matcher(c.getName()).matches()) continue OUTER;
                }
                if (!first)
                    relPath.append("/");
                relPath.append(c.getName());
                findProjects(c, result, markers, ignores, perProjectIgnore, relPath);
                relPath.delete(len, relPath.length());
            }
        }
    }

    public static String codeNameForJob(Job<?, ?> job) {
        return job.getName();
    }

    private static final class RemoteResult implements Serializable {
        private final Collection<String> foundProjects;
        private final String root;
        public RemoteResult(Collection<String> foundProjects, String root) {
            this.foundProjects = foundProjects;
            this.root = root;
        }
    }
    
    @Extension // this marker indicates Hudson that this is an implementation of an extension point.
    public static class DescriptorImpl extends Descriptor<Builder> { //non-final for tests

        private File cacheDir;
        private String webVMOptions;

        public DescriptorImpl() {
            cacheDir = new File(Hudson.getInstance().getRootDir(), "index").getAbsoluteFile();
            webVMOptions = "";
            load();
        }

        public File getCacheDir() {
            return cacheDir;
        }

        public String getWebVMOptions() {
            return webVMOptions;
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new IndexingBuilder(req, formData);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            cacheDir = new File(json.getString("cacheDir"));

            String newWebVMOptions = json.getString("webVMOptions");

            if (newWebVMOptions == null) newWebVMOptions = "";

            boolean restartWebFrontEnd = !webVMOptions.equals(newWebVMOptions);

            webVMOptions = newWebVMOptions;
            
            save();
            
            boolean result = super.configure(req, json);

            if (restartWebFrontEnd)
                WebFrontEnd.restart();
            
            return result;
        }

        @Override
        public String getDisplayName() {
            return "Run Indexers";
        }

        public List<? extends IndexingTool> getIndexingTools() {
            return Arrays.asList(Hudson.getInstance().getDescriptorByType(IndexingTool.DescriptorImpl.class).getInstallations());
        }

        public boolean hasNonStandardIndexingTool() {
            return getIndexingTools().size() > 1;
        }

        public FormValidation doCheckIndexSubDirectory(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, InterruptedException {
            FilePath workspace = project.getSomeWorkspace();
            if (workspace == null || !workspace.exists() || value == null || value.isEmpty() || workspace.child(value).isDirectory()) {
                return FormValidation.ok();
            } else {
                return workspace.validateRelativeDirectory(value);
            }
        }

        public FormValidation doCheckIgnorePatterns(@AncestorInPath AbstractProject project, @QueryParameter String ignorePatterns) throws IOException, InterruptedException {
            FilePath workspace = project.getSomeWorkspace();
            if (workspace == null || !workspace.exists() || ignorePatterns == null || ignorePatterns.isEmpty()) {
                return FormValidation.ok();
            } else {
                try {
                    Pattern.compile(ignorePatterns);
                    return FormValidation.ok();
                } catch (PatternSyntaxException ex) {
                    return FormValidation.error("Not a valid regular expression (" + ex.getDescription() + ")");
                }
            }
        }

    }

    private static class FindProjects implements FileCallable<RemoteResult> {
        private final String toolHome;
        private final String perProjectIgnore;
        public FindProjects(String toolHome, String perProjectIgnore) {
            this.toolHome = toolHome;
            this.perProjectIgnore = perProjectIgnore;
        }
        public RemoteResult invoke(File file, VirtualChannel vc) throws IOException, InterruptedException {
            List<Pattern> projectMarkers = new ArrayList<Pattern>();
            List<Pattern> ignorePatterns = new ArrayList<Pattern>();
            FilePath indexerPath = new FilePath(new File(toolHome, "indexer"));
            for (FilePath clusters : indexerPath.listDirectories()) {
                FilePath patternsDirectory = clusters.child("patterns");
                if (!patternsDirectory.isDirectory()) continue;
                for (FilePath patterns : patternsDirectory.list()) {
                    if (patterns.getName().startsWith("project-marker-")) {
                        projectMarkers.addAll(readPatterns(patterns));
                    } else if (patterns.getName().startsWith("ignore-")) {
                        ignorePatterns.addAll(readPatterns(patterns));
                    }
                }
            }
            
            Set<String> projects = new HashSet<String>();

            findProjects(file, projects, projectMarkers, ignorePatterns, perProjectIgnore == null || perProjectIgnore.trim().isEmpty() ? null : Pattern.compile(perProjectIgnore), new StringBuilder());

            return new RemoteResult(projects, file.getCanonicalPath()/*XXX: will resolve symlinks!!!*/);
        }
    }

    private static List<Pattern> readPatterns(FilePath source) {
        BufferedReader in = null;

        List<Pattern> result = new ArrayList<Pattern>();
        try {
            in = new BufferedReader(new InputStreamReader(source.read(), "UTF-8"));
            String line;

            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    result.add(Pattern.compile(line));
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(IndexingBuilder.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                    Logger.getLogger(IndexingBuilder.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return result;
    }

}
