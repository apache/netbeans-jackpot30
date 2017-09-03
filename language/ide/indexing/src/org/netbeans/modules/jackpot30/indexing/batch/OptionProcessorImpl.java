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

package org.netbeans.modules.jackpot30.indexing.batch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;
import org.netbeans.modules.java.hints.spiimpl.MessageImpl;
import org.netbeans.modules.java.hints.spiimpl.Utilities;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.BatchResult;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.Folder;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities;
import org.netbeans.modules.java.hints.spiimpl.batch.ProgressHandleWrapper;
import org.netbeans.modules.java.hints.spiimpl.batch.Scopes;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.java.hints.HintContext.MessageKind;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Jan Lahoda
 */
@ServiceProvider(service=OptionProcessor.class)
public class OptionProcessorImpl extends OptionProcessor {

    private static final Logger LOG = Logger.getLogger(OptionProcessorImpl.class.getName());

    private static final String APPLY_TRANSFORMATIONS_PROJECT_OPTION = "apply-transformations-project";

    private static final Option LIST = Option.withoutArgument(Option.NO_SHORT_NAME, "list-hints-transformation");
    private static final Option APPLY_TRANSFORMATIONS = Option.shortDescription(
                                                            Option.requiredArgument(Option.NO_SHORT_NAME, "apply-transformations"),
                                                            "org.netbeans.modules.jackpot30.impl.batch.Bundle",
                                                            "SD_ApplyTransformations");
    private static final Option APPLY_TRANSFORMATIONS_PROJECT = Option.shortDescription(
                                                            Option.additionalArguments(Option.NO_SHORT_NAME, APPLY_TRANSFORMATIONS_PROJECT_OPTION),
                                                            "org.netbeans.modules.jackpot30.impl.batch.Bundle",
                                                            "SD_ApplyTransformationsProject");

    private static final Set<Option> OPTIONS = new HashSet<Option>(Arrays.asList(LIST, APPLY_TRANSFORMATIONS, APPLY_TRANSFORMATIONS_PROJECT));

    @Override
    protected Set<Option> getOptions() {
        return OPTIONS;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> optionValues) throws CommandException {
        List<Project> projects = new LinkedList<Project>();
        Map<String, List<ClassPath>> classPaths = new HashMap<String, List<ClassPath>>();

        if (optionValues.containsKey(APPLY_TRANSFORMATIONS_PROJECT)) {
            String[] projectNames = optionValues.get(APPLY_TRANSFORMATIONS_PROJECT);

            if (projectNames.length == 0) {
                env.getErrorStream().println("At least one parameter needed for " + APPLY_TRANSFORMATIONS_PROJECT_OPTION);
                throw new CommandException(1);
            }

            FileObject currentDirectory = FileUtil.toFileObject(env.getCurrentDirectory());

            OUTER: for (String p : projectNames) {
                FileObject projectFile = currentDirectory.getFileObject(p);

                if (projectFile == null) {
                    projectFile = FileUtil.toFileObject(new File(p));
                }

                if (projectFile == null) {
                    env.getErrorStream().println("Ignoring file " + p + " - cannot be found.");
                    continue;
                }

                if (!projectFile.isFolder()) {
                    env.getErrorStream().println("Ignoring file " + p + " - not a folder.");
                    continue;
                }

                Project project = null;
                String  error   = null;

                try {
                    project = ProjectManager.getDefault().findProject(projectFile);
                } catch (IOException ex) {
                    error = ex.getLocalizedMessage();
                } catch (IllegalArgumentException ex) {
                    error = ex.getLocalizedMessage();
                }

                if (project == null) {
                    if (error == null) {
                        env.getErrorStream().println("Ignoring file " + p + " - not a project.");
                    } else {
                        env.getErrorStream().println("Ignoring file " + p + " - not a project (" + error + ").");
                    }

                    continue;
                }

                for (SourceGroup sg : ProjectUtils.getSources(project).getSourceGroups("java")) {
                    FileObject root = sg.getRootFolder();

                    for (String type : Arrays.asList(ClassPath.BOOT, ClassPath.COMPILE, ClassPath.SOURCE)) {
                        if (!handleClassPath(root, type, env, p, classPaths)) {
                            continue OUTER;
                        }
                    }
                }

                projects.add(project);
            }
        } else {
            projects.addAll(Arrays.asList(OpenProjects.getDefault().getOpenProjects()));
        }

        if (optionValues.containsKey(LIST)) {
            env.getOutputStream().println("Supported Hints:");

            Set<ClassPath> cps = new HashSet<ClassPath>();

            for (List<ClassPath> c : classPaths.values()) {
                cps.addAll(c);
            }

            Set<String> displayNames = Utilities.sortOutHints(Utilities.listAllHints(cps), new TreeMap<String, Collection<HintDescription>>()).keySet();

            for (String displayName : displayNames) {
                env.getOutputStream().println(displayName);
            }
        }

        if (optionValues.containsKey(APPLY_TRANSFORMATIONS)) {
            String hintsArg = optionValues.get(APPLY_TRANSFORMATIONS)[0];
            List<HintDescription> hintDescriptions = new LinkedList<HintDescription>();
            Set<ClassPath> cps = new HashSet<ClassPath>();

            for (List<ClassPath> c : classPaths.values()) {
                cps.addAll(c);
            }

            Map<String, Collection<HintDescription>> sorted = Utilities.sortOutHints(Utilities.listAllHints(cps), new TreeMap<String, Collection<HintDescription>>());

            for (String hint : hintsArg.split(":")) {
                Collection<HintDescription> descs = sorted.get(hint);

                if (descs == null) {
                    env.getErrorStream().println("Unknown hint: " + hint);
                    continue;
                }

                hintDescriptions.addAll(descs);
            }

            Collection<Folder> roots = new ArrayList<Folder>();

            for (FileObject f : BatchUtilities.getSourceGroups(projects)) {
                roots.add(new Folder(f));
            }

            BatchResult candidates = BatchSearch.findOccurrences(hintDescriptions, Scopes.specifiedFoldersScope(roots.toArray(new Folder[0])));
            List<MessageImpl> problems = new LinkedList<MessageImpl>(candidates.problems);
            Collection<? extends ModificationResult> res = BatchUtilities.applyFixes(candidates, new ProgressHandleWrapper(100), null, problems);
            Set<FileObject> modified = new HashSet<FileObject>();

            for (ModificationResult mr : res) {
                try {
                    mr.commit();
                } catch (IOException ex) {
                    ex.printStackTrace(env.getErrorStream());
                    problems.add(new MessageImpl(MessageKind.ERROR, "Cannot apply changes: " + ex.getLocalizedMessage()));
                }
                modified.addAll(mr.getModifiedFileObjects());
            }

            try {
                org.netbeans.modules.jackpot30.indexing.batch.BatchUtilities.removeUnusedImports(modified);
            } catch (IOException ex) {
                ex.printStackTrace(env.getErrorStream());
                problems.add(new MessageImpl(MessageKind.ERROR, "Cannot remove unused imports: " + ex.getLocalizedMessage()));
            }

            if (!problems.isEmpty()) {
                env.getErrorStream().println("Problem encountered while applying the transformations:");

                for (MessageImpl problem : problems) {
                    env.getErrorStream().println(problem.text);
                }
            }
        }

    }

    private boolean handleClassPath(FileObject root, String type, Env env, String p, Map<String, List<ClassPath>> classPaths) {
        ClassPath cp = ClassPath.getClassPath(root, type);

        if (cp == null) {
            env.getErrorStream().println("Ignoring file " + p + " - no " + type + " classpath for source group: " + FileUtil.getFileDisplayName(root));
            return false;
        }

        List<ClassPath> cps = classPaths.get(type);

        if (cps == null) {
            classPaths.put(type, cps = new LinkedList<ClassPath>());
        }

        cp = ClassPathSupport.createProxyClassPath(cp);

        cps.add(cp);

        return true;
    }

}
