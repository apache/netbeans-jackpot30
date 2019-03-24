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

package org.netbeans.modules.jackpot30.cmdline;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.modules.editor.tools.storage.api.ToolPreferences;
import org.netbeans.modules.jackpot30.cmdline.lib.Utils;
//import org.netbeans.modules.jackpot30.ui.settings.XMLHintPreferences;
import org.netbeans.modules.java.hints.declarative.DeclarativeHintRegistry;
import org.netbeans.modules.java.hints.declarative.test.TestParser;
import org.netbeans.modules.java.hints.declarative.test.TestParser.TestCase;
import org.netbeans.modules.java.hints.declarative.test.TestPerformer;
import org.netbeans.modules.java.hints.declarative.test.TestPerformer.TestClassPathProvider;
import org.netbeans.modules.java.hints.jackpot.spi.PatternConvertor;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;
import org.netbeans.modules.java.hints.providers.spi.HintMetadata;
import org.netbeans.modules.java.hints.providers.spi.HintMetadata.Options;
import org.netbeans.modules.java.hints.spiimpl.MessageImpl;
import org.netbeans.modules.java.hints.spiimpl.RulesManager;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.BatchResult;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.Folder;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.Resource;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.VerifiedSpansCallBack;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities;
import org.netbeans.modules.java.hints.spiimpl.batch.ProgressHandleWrapper;
import org.netbeans.modules.java.hints.spiimpl.batch.ProgressHandleWrapper.ProgressHandleAbstraction;
import org.netbeans.modules.java.hints.spiimpl.batch.Scopes;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.modules.parsing.impl.indexing.RepositoryUpdater;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.Severity;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.java.hints.Hint.Kind;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation2;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class Main {

    private static final String OPTION_APPLY = "apply";
    private static final String OPTION_NO_APPLY = "no-apply";
    private static final String OPTION_FAIL_ON_WARNINGS = "fail-on-warnings";
    private static final String RUN_TESTS = "run-tests";
    private static final String SOURCE_LEVEL_DEFAULT = "1.7";
    private static final String ACCEPTABLE_SOURCE_LEVEL_PATTERN = "(1\\.)?[2-9][0-9]*";
    
    public static void main(String... args) throws IOException, ClassNotFoundException {
        System.exit(compile(args));
    }

    public static int compile(String... args) throws IOException, ClassNotFoundException {
        try {
            Class.forName("javax.lang.model.element.ModuleElement");
        } catch (ClassNotFoundException ex) {
            System.err.println("Error: no suitable javac found, please run on JDK 11+.");
            return 1;
        }
        System.setProperty("netbeans.user", "/tmp/tmp-foo");
        System.setProperty("SourcePath.no.source.filter", "true");

        OptionParser parser = new OptionParser();
//        ArgumentAcceptingOptionSpec<File> projects = parser.accepts("project", "project(s) to refactor").withRequiredArg().withValuesSeparatedBy(File.pathSeparatorChar).ofType(File.class);
        GroupOptions globalGroupOptions = setupGroupParser(parser);
        ArgumentAcceptingOptionSpec<File> cache = parser.accepts("cache", "a cache directory to store working data").withRequiredArg().ofType(File.class);
        ArgumentAcceptingOptionSpec<File> out = parser.accepts("out", "output diff").withRequiredArg().ofType(File.class);
        ArgumentAcceptingOptionSpec<File> configFile = parser.accepts("config-file", "configuration file").withRequiredArg().ofType(File.class);
        ArgumentAcceptingOptionSpec<String> hint = parser.accepts("hint", "hint name").withRequiredArg().ofType(String.class);
        ArgumentAcceptingOptionSpec<String> config = parser.accepts("config", "configurations").withRequiredArg().ofType(String.class);
        ArgumentAcceptingOptionSpec<File> hintFile = parser.accepts("hint-file", "file with rules that should be performed").withRequiredArg().ofType(File.class);
        ArgumentAcceptingOptionSpec<String> group = parser.accepts("group", "specify roots to process alongside with their classpath").withRequiredArg().ofType(String.class);

        parser.accepts("list", "list all known hints");
        parser.accepts("progress", "show progress");
        parser.accepts("debug", "enable debugging loggers");
        parser.accepts("help", "prints this help");
        parser.accepts(OPTION_NO_APPLY, "do not apply changes - only print locations were the hint would be applied");
        parser.accepts(OPTION_APPLY, "apply changes");
        parser.accepts("show-gui", "show configuration dialog");
        parser.accepts(OPTION_FAIL_ON_WARNINGS, "fail when warnings are detected");
        parser.accepts(RUN_TESTS, "run tests for declarative rules that were used");

        OptionSet parsed;

        try {
            parsed = parser.parse(inlineParameterFiles(args));
        } catch (OptionException ex) {
            System.err.println(ex.getLocalizedMessage());
            parser.printHelpOn(System.out);
            return 1;
        }

        if (!parsed.has("debug")) {
            prepareLoggers();
        }

        if (parsed.has("help")) {
            parser.printHelpOn(System.out);
            return 0;
        }

        List<FileObject> roots = new ArrayList<FileObject>();
        List<Folder> rootFolders = new ArrayList<Folder>();

        for (String sr : parsed.nonOptionArguments()) {
            File r = new File(sr);
            FileObject root = FileUtil.toFileObject(r);

            if (root != null) {
                roots.add(root);
                rootFolders.add(new Folder(root));
            }
        }

        final List<RootConfiguration> groups = new ArrayList<>();

        groups.add(new RootConfiguration(parsed, globalGroupOptions));

        for (String groupValue : parsed.valuesOf(group)) {
            OptionParser groupParser = new OptionParser();
            GroupOptions groupOptions = setupGroupParser(groupParser);
            OptionSet parsedGroup = groupParser.parse(splitGroupArg(groupValue));

            groups.add(new RootConfiguration(parsedGroup, groupOptions));
        }

        if (parsed.has("show-gui")) {
            if (parsed.has(configFile)) {
                final File settingsFile = parsed.valueOf(configFile);
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override public void run() {
                            try {
                                Pair<ClassPath, ClassPath> sourceAndBinaryCP = jointSourceAndBinaryCP(groups);
                                showGUICustomizer(settingsFile, sourceAndBinaryCP.second(), sourceAndBinaryCP.first());
                            } catch (IOException ex) {
                                Exceptions.printStackTrace(ex);
                            } catch (BackingStoreException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    });
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (InvocationTargetException ex) {
                    Exceptions.printStackTrace(ex);
                }

                return 0;
            } else {
                System.err.println("show-gui requires config-file");
                return 1;
            }
        }

        File cacheDir = parsed.valueOf(cache);
        boolean deleteCacheDir = false;

        try {
            if (cacheDir == null) {
                cacheDir = File.createTempFile("jackpot", "cache");
                cacheDir.delete();
                if (!(deleteCacheDir = cacheDir.mkdirs())) {
                    System.err.println("cannot create temporary cache");
                    return 1;
                }
            }

            if (cacheDir.isFile()) {
                System.err.println("cache directory exists and is a file");
                return 1;
            }

            String[] cacheDirContent = cacheDir.list();

            if (cacheDirContent != null && cacheDirContent.length > 0 && !new File(cacheDir, "segments").exists()) {
                System.err.println("cache directory is not empty, but was not created by this tool");
                return 1;
            }

            cacheDir.mkdirs();

            CacheFolder.setCacheFolder(FileUtil.toFileObject(FileUtil.normalizeFile(cacheDir)));

            org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
            RepositoryUpdater.getDefault().start(false);

            if (parsed.has("list")) {
                Pair<ClassPath, ClassPath> sourceAndBinaryCP = jointSourceAndBinaryCP(groups);
                printHints(sourceAndBinaryCP.first(),
                           sourceAndBinaryCP.second());
                return 0;
            }

            int totalGroups = 0;

            for (RootConfiguration groupConfig : groups) {
                if (!groupConfig.rootFolders.isEmpty()) totalGroups++;
            }

            ProgressHandleWrapper progress = parsed.has("progress") ? new ProgressHandleWrapper(new ConsoleProgressHandleAbstraction(), ProgressHandleWrapper.prepareParts(totalGroups)) : new ProgressHandleWrapper(1);

            Preferences hintSettingsPreferences;
            boolean apply;
            boolean runDeclarative;
            boolean runDeclarativeTests;
            boolean useDefaultEnabledSetting;

            if (parsed.has(configFile)) {
                ToolPreferences toolPrefs = ToolPreferences.from(parsed.valueOf(configFile).toURI());
                hintSettingsPreferences = toolPrefs.getPreferences("hints", "text/x-java");
                Preferences toolSettings = toolPrefs.getPreferences("standalone", "text/x-java");
                apply = toolSettings.getBoolean("apply", false);
                runDeclarative = toolSettings.getBoolean("runDeclarative", true);
                runDeclarativeTests = toolSettings.getBoolean("runDeclarativeTests", false);
                useDefaultEnabledSetting = true; //TODO: read from the configuration file?
                if (parsed.has(hint)) {
                    System.err.println("cannot specify --hint and --config-file together");
                    return 1;
                } else if (parsed.has(hintFile)) {
                    System.err.println("cannot specify --hint-file and --config-file together");
                    return 1;
                }
            } else {
                hintSettingsPreferences = null;
                apply = false;
                runDeclarative = true;
                runDeclarativeTests = parsed.has(RUN_TESTS);
                useDefaultEnabledSetting = false;
            }

            if (parsed.has(config) && !parsed.has(hint)) {
                System.err.println("--config cannot specified when no hint is specified");
                return 1;
            }

            if (parsed.has(OPTION_NO_APPLY)) {
                apply = false;
            } else if (parsed.has(OPTION_APPLY)) {
                apply = true;
            }

            GroupResult result = GroupResult.NOTHING_TO_DO;

            try (Writer outS = parsed.has(out) ? new BufferedWriter(new OutputStreamWriter(new FileOutputStream(parsed.valueOf(out)))) : null) {
                GlobalConfiguration globalConfig = new GlobalConfiguration(hintSettingsPreferences, apply, runDeclarative, runDeclarativeTests, useDefaultEnabledSetting, parsed.valueOf(hint), parsed.valueOf(hintFile), outS, parsed.has(OPTION_FAIL_ON_WARNINGS));

                for (RootConfiguration groupConfig : groups) {
                    result = result.join(handleGroup(groupConfig, progress, globalConfig, parsed.valuesOf(config)));
                }
            }

            progress.finish();

            if (result == GroupResult.NOTHING_TO_DO) {
                System.err.println("no source roots to work on");
                return 1;
            }

            if (result == GroupResult.NO_HINTS_FOUND) {
                System.err.println("no hints specified");
                return 1;
            }

            return result == GroupResult.SUCCESS ? 0 : 1;
        } catch (Throwable e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        } finally {
            if (deleteCacheDir) {
                FileObject cacheDirFO = FileUtil.toFileObject(cacheDir);

                if (cacheDirFO != null) {
                    //TODO: would be better to do j.i.File.delete():
                    cacheDirFO.delete();
                }
            }
        }
    }

    private static String[] inlineParameterFiles(String... args) {
        List<String> inlinedArgs = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("@")) {
                try (BufferedReader r = new BufferedReader(new FileReader(arg.substring(1)))) {
                    String line;

                    while ((line = r.readLine()) != null) {
                        inlinedArgs.add(line);
                    }
                } catch (IOException ex) {
                    throw new OptionException(Collections.emptySet(), ex) {};
                }
            } else {
                inlinedArgs.add(arg);
            }
        }

        return inlinedArgs.toArray(new String[0]);
    }

    private static Pair<ClassPath, ClassPath> jointSourceAndBinaryCP(List<RootConfiguration> groups) {
        Set<FileObject> sourceRoots = new HashSet<>();
        Set<FileObject> binaryRoots = new HashSet<>();
        for (RootConfiguration groupConfig : groups) {
            sourceRoots.addAll(Arrays.asList(groupConfig.sourceCP.getRoots()));
            binaryRoots.addAll(Arrays.asList(groupConfig.binaryCP.getRoots()));
        }
        return Pair.of(ClassPathSupport.createClassPath(sourceRoots.toArray(new FileObject[0])),
                       ClassPathSupport.createClassPath(binaryRoots.toArray(new FileObject[0])));
    }

    private static GroupOptions setupGroupParser(OptionParser parser) {
        return new GroupOptions(parser.accepts("classpath", "classpath").withRequiredArg().withValuesSeparatedBy(File.pathSeparatorChar).ofType(File.class),
                                parser.accepts("bootclasspath", "bootclasspath").withRequiredArg().withValuesSeparatedBy(File.pathSeparatorChar).ofType(File.class),
                                parser.accepts("sourcepath", "sourcepath").withRequiredArg().withValuesSeparatedBy(File.pathSeparatorChar).ofType(File.class),
                                parser.accepts("source", "source level").withRequiredArg().ofType(String.class).defaultsTo(SOURCE_LEVEL_DEFAULT));
    }

    private static final class GroupOptions {
        private final ArgumentAcceptingOptionSpec<File> classpath;
        private final ArgumentAcceptingOptionSpec<File> bootclasspath;
        private final ArgumentAcceptingOptionSpec<File> sourcepath;
        private final ArgumentAcceptingOptionSpec<String> source;

        public GroupOptions(ArgumentAcceptingOptionSpec<File> classpath, ArgumentAcceptingOptionSpec<File> bootclasspath, ArgumentAcceptingOptionSpec<File> sourcepath, ArgumentAcceptingOptionSpec<String> source) {
            this.classpath = classpath;
            this.bootclasspath = bootclasspath;
            this.sourcepath = sourcepath;
            this.source = source;
        }

    }

    private static Map<HintMetadata, Collection<? extends HintDescription>> listHints(ClassPath sourceFrom, ClassPath binaryFrom) {
        Map<HintMetadata, Collection<? extends HintDescription>> result = new HashMap<HintMetadata, Collection<? extends HintDescription>>();

        for (Entry<HintMetadata, ? extends Collection<? extends HintDescription>> entry: RulesManager.getInstance().readHints(null, Arrays.asList(sourceFrom, binaryFrom), null).entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    private static GroupResult handleGroup(RootConfiguration rootConfiguration, ProgressHandleWrapper w, GlobalConfiguration globalConfig, List<String> config) throws IOException {
        Iterable<? extends HintDescription> hints;

        if (rootConfiguration.rootFolders.isEmpty()) {
            return GroupResult.NOTHING_TO_DO;
        }

        WarningsAndErrors wae = new WarningsAndErrors();

        ProgressHandleWrapper progress = w.startNextPartWithEmbedding(1);
        Preferences settings = globalConfig.configurationPreferences != null ? globalConfig.configurationPreferences : new MemoryPreferences();
        HintsSettings hintSettings = HintsSettings.createPreferencesBasedHintsSettings(settings, globalConfig.useDefaultEnabledSetting, null);

        if (globalConfig.hint != null) {
            hints = findHints(rootConfiguration.sourceCP, rootConfiguration.binaryCP, globalConfig.hint, hintSettings);
        } else if (globalConfig.hintFile != null) {
            FileObject hintFileFO = FileUtil.toFileObject(globalConfig.hintFile);
            assert hintFileFO != null;
            hints = PatternConvertor.create(hintFileFO.asText());
            for (HintDescription hd : hints) {
                hintSettings.setEnabled(hd.getMetadata(), true);
            }
        } else {
            hints = readHints(rootConfiguration.sourceCP, rootConfiguration.binaryCP, hintSettings, settings, globalConfig.runDeclarative);
            if (globalConfig.runDeclarativeTests) {
                Set<String> enabledHints = new HashSet<>();
                for (HintDescription desc : hints) {
                    enabledHints.add(desc.getMetadata().id);
                }
                ClassPath combined = ClassPathSupport.createProxyClassPath(rootConfiguration.sourceCP, rootConfiguration.binaryCP);
                Map<FileObject, FileObject> testFiles = new HashMap<>();
                for (FileObject upgrade : combined.findAllResources("META-INF/upgrade")) {
                    for (FileObject c : upgrade.getChildren()) {
                        if (c.getExt().equals("test")) {
                            FileObject hintFile = FileUtil.findBrother(c, "hint");

                            for (HintMetadata hm : DeclarativeHintRegistry.parseHintFile(hintFile).keySet()) {
                                if (enabledHints.contains(hm.id)) {
                                    testFiles.put(c, hintFile);
                                    break;
                                }
                            }
                        }
                    }
                }
                for (Entry<FileObject, FileObject> e : testFiles.entrySet()) {
                    TestCase[] testCases = TestParser.parse(e.getKey().asText()); //XXX: encoding
                    try {
                        Map<TestCase, Collection<String>> testResult = TestPerformer.performTest(e.getValue(), e.getKey(), testCases, new AtomicBoolean());
                        for (TestCase tc : testCases) {
                            List<String> expected = Arrays.asList(tc.getResults());
                            List<String> actual = new ArrayList<>(testResult.get(tc));
                            if (!expected.equals(actual)) {
                                int pos = tc.getTestCaseStart();
                                String id = "test-failure";
                                ErrorDescription ed = ErrorDescriptionFactory.createErrorDescription(id, Severity.ERROR, "Actual results did not match the expected test results. Actual results: " + expected, null, ErrorDescriptionFactory.lazyListForFixes(Collections.<Fix>emptyList()), e.getKey(), pos, pos);
                                print(ed, wae, Collections.singletonMap(id, id));
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        if (config != null && !config.isEmpty()) {
            Iterator<? extends HintDescription> hit = hints.iterator();
            HintDescription hd = hit.next();

            if (hit.hasNext()) {
                System.err.println("--config cannot specified when more than one hint is specified");

                return GroupResult.FAILURE;
            }

            Preferences prefs = hintSettings.getHintPreferences(hd.getMetadata());

            boolean stop = false;

            for (String c : config) {
                int assign = c.indexOf('=');

                if (assign == (-1)) {
                    System.err.println("configuration option is missing '=' (" + c + ")");
                    stop = true;
                    continue;
                }

                prefs.put(c.substring(0, assign), c.substring(assign + 1));
            }

            if (stop) {
                return GroupResult.FAILURE;
            }
        }

        String sourceLevel = rootConfiguration.sourceLevel;

        if (!Pattern.compile(ACCEPTABLE_SOURCE_LEVEL_PATTERN).matcher(sourceLevel).matches()) {
            System.err.println("unrecognized source level specification: " + sourceLevel);
            return GroupResult.FAILURE;
        }

        if (globalConfig.apply && !hints.iterator().hasNext()) {
            return GroupResult.NO_HINTS_FOUND;
        }

        RootConfiguration prevConfig = currentRootConfiguration.get();

        try {
            currentRootConfiguration.set(rootConfiguration);

            try {
                if (globalConfig.apply) {
                    apply(hints, rootConfiguration.rootFolders.toArray(new Folder[0]), progress, hintSettings, globalConfig.out);

                    return GroupResult.SUCCESS; //TODO: WarningsAndErrors?
                } else {
                    findOccurrences(hints, rootConfiguration.rootFolders.toArray(new Folder[0]), progress, hintSettings, wae);

                    if (wae.errors != 0 || (wae.warnings != 0 && globalConfig.failOnWarnings)) {
                        return GroupResult.FAILURE;
                    } else {
                        return GroupResult.SUCCESS;
                    }
                }
            } catch (IOException t) {
                throw new UncheckedIOException(t);
            }
        } finally {
            currentRootConfiguration.set(prevConfig);
        }
    }

    private static class MemoryPreferences extends AbstractPreferences {

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, MemoryPreferences> nodes = new HashMap<>();

        public MemoryPreferences() {
            this(null, "");
        }

        public MemoryPreferences(MemoryPreferences parent, String name) {
            super(parent, name);
        }
        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            ((MemoryPreferences) parent()).nodes.remove(name());
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return nodes.keySet().toArray(new String[0]);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            MemoryPreferences result = nodes.get(name);

            if (result == null) {
                nodes.put(name, result = new MemoryPreferences(this, name));
            }

            return result;
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }
    }

    private enum GroupResult {
        NOTHING_TO_DO {
            @Override
            public GroupResult join(GroupResult other) {
                return other;
            }
        },
        NO_HINTS_FOUND {
            @Override
            public GroupResult join(GroupResult other) {
                if (other == NOTHING_TO_DO) return this;
                return other;
            }
        },
        SUCCESS {
            @Override
            public GroupResult join(GroupResult other) {
                if (other == FAILURE) return other;
                return this;
            }
        },
        FAILURE {
            @Override
            public GroupResult join(GroupResult other) {
                return this;
            }
        };

        public abstract GroupResult join(GroupResult other);
    }
    
    private static Iterable<? extends HintDescription> findHints(ClassPath sourceFrom, ClassPath binaryFrom, String name, HintsSettings toEnableIn) {
        List<HintDescription> descs = new LinkedList<HintDescription>();

        for (Entry<HintMetadata, Collection<? extends HintDescription>> e : listHints(sourceFrom, binaryFrom).entrySet()) {
            if (e.getKey().displayName.equals(name)) {
                descs.addAll(e.getValue());
                toEnableIn.setEnabled(e.getKey(), true);
            }
        }

        return descs;
    }

    private static Iterable<? extends HintDescription> allHints(ClassPath sourceFrom, ClassPath binaryFrom, HintsSettings toEnableIn) {
        List<HintDescription> descs = new LinkedList<HintDescription>();

        for (Entry<HintMetadata, Collection<? extends HintDescription>> e : listHints(sourceFrom, binaryFrom).entrySet()) {
            if (e.getKey().kind != Kind.INSPECTION) continue;
            if (!e.getKey().enabled) continue;
            descs.addAll(e.getValue());
            toEnableIn.setEnabled(e.getKey(), true);
        }

        return descs;
    }

    private static Iterable<? extends HintDescription> readHints(ClassPath sourceFrom, ClassPath binaryFrom, HintsSettings toEnableIn, Preferences toEnableInPreferencesHack, boolean declarativeEnabledByDefault) {
        Map<HintMetadata, ? extends Collection<? extends HintDescription>> hardcoded = RulesManager.getInstance().readHints(null, Arrays.<ClassPath>asList(), null);
        Map<HintMetadata, ? extends Collection<? extends HintDescription>> all = RulesManager.getInstance().readHints(null, Arrays.asList(sourceFrom, binaryFrom), null);
        List<HintDescription> descs = new LinkedList<HintDescription>();

        for (Entry<HintMetadata, ? extends Collection<? extends HintDescription>> entry: all.entrySet()) {
            if (hardcoded.containsKey(entry.getKey())) {
                if (toEnableIn.isEnabled(entry.getKey()) && entry.getKey().kind == Kind.INSPECTION && !entry.getKey().options.contains(Options.NO_BATCH)) {
                    descs.addAll(entry.getValue());
                }
            } else {
                if (/*XXX: hack*/toEnableInPreferencesHack.node(entry.getKey().id).getBoolean("enabled", declarativeEnabledByDefault)) {
                    descs.addAll(entry.getValue());
                }
            }
        }

        return descs;
    }

    private static final Logger TOP_LOGGER = Logger.getLogger("");

    private static void prepareLoggers() {
        TOP_LOGGER.setLevel(Level.OFF);
        System.setProperty("RepositoryUpdate.increasedLogLevel", "OFF");
    }
    
    private static void findOccurrences(Iterable<? extends HintDescription> descs, Folder[] sourceRoot, ProgressHandleWrapper progress, HintsSettings settings, final WarningsAndErrors wae) throws IOException {
        final Map<String, String> id2DisplayName = Utils.computeId2DisplayName(descs);
        ProgressHandleWrapper w = progress.startNextPartWithEmbedding(1, 1);
        BatchResult occurrences = BatchSearch.findOccurrences(descs, Scopes.specifiedFoldersScope(sourceRoot), w, settings);

        List<MessageImpl> problems = new LinkedList<MessageImpl>();
        BatchSearch.getVerifiedSpans(occurrences, w, new VerifiedSpansCallBack() {
            @Override public void groupStarted() {}
            @Override public boolean spansVerified(CompilationController wc, Resource r, Collection<? extends ErrorDescription> hints) throws Exception {
                hints = hints.stream()
                             .sorted((ed1, ed2) -> ed1.getRange().getBegin().getOffset() - ed2.getRange().getBegin().getOffset())
                             .collect(Collectors.toList());
                for (ErrorDescription ed : hints) {
                    print(ed, wae, id2DisplayName);
                }
                return true;
            }
            @Override public void groupFinished() {}
            @Override public void cannotVerifySpan(Resource r) {
                //TODO: ignored - what to do?
            }
        }, true, problems, new AtomicBoolean());
    }

    private static void print(ErrorDescription error, WarningsAndErrors wae, Map<String, String> id2DisplayName) throws IOException {
        int lineNumber = error.getRange().getBegin().getLine();
        String line = error.getFile().asLines().get(lineNumber);
        int column = error.getRange().getBegin().getColumn();
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < column; i++) {
            if (Character.isWhitespace(line.charAt(i))) {
                b.append(line.charAt(i));
            } else {
                b.append(' ');
            }
        }

        b.append('^');

        String idDisplayName = Utils.categoryName(error.getId(), id2DisplayName);
        String severity;
        if (error.getSeverity() == Severity.ERROR) {
            severity = "error";
            wae.errors++;
        } else {
            severity = "warning";
            wae.warnings++;
        }
        System.out.println(FileUtil.getFileDisplayName(error.getFile()) + ":" + (lineNumber + 1) + ": " + severity + ": " + idDisplayName + error.getDescription());
        System.out.println(line);
        System.out.println(b);
    }

    private static void apply(Iterable<? extends HintDescription> descs, Folder[] sourceRoot, ProgressHandleWrapper progress, HintsSettings settings, Writer out) throws IOException {
        ProgressHandleWrapper w = progress.startNextPartWithEmbedding(1, 1);
        BatchResult occurrences = BatchSearch.findOccurrences(descs, Scopes.specifiedFoldersScope(sourceRoot), w, settings);

        List<MessageImpl> problems = new LinkedList<MessageImpl>();
        Collection<ModificationResult> diffs = BatchUtilities.applyFixes(occurrences, w, new AtomicBoolean(), new ArrayList<RefactoringElementImplementation>(), null, true, problems);

        if (out != null) {
            for (ModificationResult mr : diffs) {
                //XXX:
//                org.netbeans.modules.jackpot30.indexing.batch.BatchUtilities.exportDiff(mr, null, out);
            }
        } else {
            for (ModificationResult mr : diffs) {
                mr.commit();
            }
        }
    }

    private static void printHints(ClassPath sourceFrom, ClassPath binaryFrom) throws IOException {
        Set<String> hints = new TreeSet<String>();

        for (Entry<HintMetadata, Collection<? extends HintDescription>> e : listHints(sourceFrom, binaryFrom).entrySet()) {
            hints.add(e.getKey().displayName);
        }

        for (String h : hints) {
            System.out.println(h);
        }
    }

    private static ClassPath createClassPath(Iterable<? extends File> roots, ClassPath def) {
        if (roots == null) return def;

        List<URL> rootURLs = new ArrayList<URL>();

        for (File r : roots) {
            rootURLs.add(FileUtil.urlForArchiveOrDir(r));
        }

        return ClassPathSupport.createClassPath(rootURLs.toArray(new URL[0]));
    }

    private static void showGUICustomizer(File settingsFile, ClassPath binaryCP, ClassPath sourceCP) throws IOException, BackingStoreException {
//        GlobalPathRegistry.getDefault().register(ClassPath.COMPILE, new ClassPath[] {binaryCP});
//        GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, new ClassPath[] {sourceCP});
//        ClassPathBasedHintWrapper hints = new ClassPathBasedHintWrapper();
//        final Preferences p = XMLHintPreferences.from(settingsFile);
//        JPanel hintPanel = new HintsPanel(p.node("settings"), hints, true);
//        final JCheckBox runDeclarativeHints = new JCheckBox("Always Run Declarative Rules");
//
//        runDeclarativeHints.setToolTipText("Always run the declarative rules found on classpath? (Only those selected above will be run when unchecked.)");
//        runDeclarativeHints.setSelected(p.getBoolean("runDeclarative", true));
//        runDeclarativeHints.addActionListener(new ActionListener() {
//            @Override public void actionPerformed(ActionEvent e) {
//                p.putBoolean("runDeclarative", runDeclarativeHints.isSelected());
//            }
//        });
//
//        JPanel customizer = new JPanel(new BorderLayout());
//
//        customizer.add(hintPanel, BorderLayout.CENTER);
//        customizer.add(runDeclarativeHints, BorderLayout.SOUTH);
//        JOptionPane jop = new JOptionPane(customizer, JOptionPane.PLAIN_MESSAGE);
//        JDialog dialog = jop.createDialog("Select Hints");
//
//        jop.selectInitialValue();
//        dialog.setVisible(true);
//        dialog.dispose();
//
//        Object result = jop.getValue();
//
//        if (result.equals(JOptionPane.OK_OPTION)) {
//            p.flush();
//        }
    }

    static String[] splitGroupArg(String arg) {
        List<String> result = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();

        for (int i = 0; i < arg.length(); i++) {
            switch (arg.charAt(i)) {
                case '\\':
                    if (++i < arg.length()) {
                        currentPart.append(arg.charAt(i));
                    }
                    break;
                case ' ':
                    if (currentPart.length() > 0) {
                        result.add(currentPart.toString());
                        currentPart.delete(0, currentPart.length());
                    }
                    break;
                default:
                    currentPart.append(arg.charAt(i));
                    break;
            }
        }

        if (currentPart.length() > 0) {
            result.add(currentPart.toString());
        }

        return result.toArray(new String[0]);
    }

    private static final class WarningsAndErrors {
        private int warnings;
        private int errors;
    }

    private static final class RootConfiguration {
        private final List<Folder> rootFolders;
        private final ClassPath bootCP;
        private final ClassPath compileCP;
        private final ClassPath sourceCP;
        private final ClassPath binaryCP;
        private final String    sourceLevel;

        public RootConfiguration(OptionSet parsed, GroupOptions groupOptions) throws IOException {
            this.rootFolders = new ArrayList<>();

            List<FileObject> roots = new ArrayList<>();

            for (String sr : parsed.nonOptionArguments()) {
                File r = new File(sr);
                FileObject root = FileUtil.toFileObject(r);

                if (root != null) {
                    roots.add(root);
                    rootFolders.add(new Folder(root));
                }
            }

            this.bootCP = createClassPath(parsed.has(groupOptions.bootclasspath) ? parsed.valuesOf(groupOptions.bootclasspath) : null, Utils.createDefaultBootClassPath());
            this.compileCP = createClassPath(parsed.has(groupOptions.classpath) ? parsed.valuesOf(groupOptions.classpath) : null, ClassPath.EMPTY);
            this.sourceCP = createClassPath(parsed.has(groupOptions.sourcepath) ? parsed.valuesOf(groupOptions.sourcepath) : null, ClassPathSupport.createClassPath(roots.toArray(new FileObject[0])));
            this.binaryCP = ClassPathSupport.createProxyClassPath(bootCP, compileCP);
            this.sourceLevel = parsed.valueOf(groupOptions.source);
        }

    }

    private static final class GlobalConfiguration {
        private final Preferences configurationPreferences;
        private final boolean apply;
        private final boolean runDeclarative;
        private final boolean runDeclarativeTests;
        private final boolean useDefaultEnabledSetting;
        private final String hint;
        private final File hintFile;
        private final Writer out;
        private final boolean failOnWarnings;

        public GlobalConfiguration(Preferences configurationPreferences, boolean apply, boolean runDeclarative, boolean runDeclarativeTests, boolean useDefaultEnabledSetting, String hint, File hintFile, Writer out, boolean failOnWarnings) {
            this.configurationPreferences = configurationPreferences;
            this.apply = apply;
            this.runDeclarative = runDeclarative;
            this.runDeclarativeTests = runDeclarativeTests;
            this.useDefaultEnabledSetting = useDefaultEnabledSetting;
            this.hint = hint;
            this.hintFile = hintFile;
            this.out = out;
            this.failOnWarnings = failOnWarnings;
        }

    }

    @ServiceProvider(service=Lookup.class)
    public static final class LookupProviderImpl extends ProxyLookup {

        public LookupProviderImpl() {
            super(Lookups.forPath("Services/AntBasedProjectTypes"));
        }
    }

    private static final ThreadLocal<RootConfiguration> currentRootConfiguration = new ThreadLocal<>();

    @ServiceProvider(service=ClassPathProvider.class, position=100)
    public static final class ClassPathProviderImpl implements ClassPathProvider {

        @Override
        public ClassPath findClassPath(FileObject file, String type) {
            RootConfiguration rootConfiguration = currentRootConfiguration.get();

            if (rootConfiguration == null) {
                return null;
            }

            if (rootConfiguration.sourceCP.findOwnerRoot(file) != null) {
                if (ClassPath.BOOT.equals(type)) {
                    return rootConfiguration.bootCP;
                } else if (ClassPath.COMPILE.equals(type)) {
                    return rootConfiguration.compileCP;
                } else  if (ClassPath.SOURCE.equals(type)) {
                    return rootConfiguration.sourceCP;
                }
            }

            return null;
        }
    }

    @ServiceProvider(service=SourceLevelQueryImplementation2.class, position=100)
    public static final class SourceLevelQueryImpl implements SourceLevelQueryImplementation2 {

        @Override
        public Result getSourceLevel(FileObject javaFile) {
            RootConfiguration rootConfiguration = currentRootConfiguration.get();

            if (rootConfiguration == null) {
                return null;
            }

            if (rootConfiguration.sourceCP.findOwnerRoot(javaFile) != null) {
                return new Result() {
                    @Override public String getSourceLevel() {
                        return rootConfiguration.sourceLevel;
                    }
                    @Override public void addChangeListener(ChangeListener listener) {}
                    @Override public void removeChangeListener(ChangeListener listener) {}
                };
            } else {
                return null;
            }
        }

    }

    private static final class ConsoleProgressHandleAbstraction implements ProgressHandleAbstraction {

        private final int width = 80 - 2;

        private int total = -1;
        private int current = 0;

        public ConsoleProgressHandleAbstraction() {
        }

        @Override
        public synchronized void start(int totalWork) {
            if (total != (-1)) throw new UnsupportedOperationException();
            total = totalWork;
            update();
        }

        @Override
        public synchronized void progress(int currentWorkDone) {
            current = currentWorkDone;
            update();
        }

        @Override
        public void progress(String message) {
        }

        @Override
        public synchronized void finish() {
            current = total;
            RequestProcessor.getDefault().post(new Runnable() {
                @Override
                public void run() {
                    doUpdate(false);
                    System.out.println();
                }
            });
        }

        private void update() {
            RequestProcessor.getDefault().post(new Runnable() {
                @Override
                public void run() {
                    doUpdate(true);
                }
            });
        }

        private int currentShownDone = -1;

        private void doUpdate(boolean moveCaret) {
            int done;

            synchronized(this) {
                done = (int) ((((double) width) / total) * current);

                if (done == currentShownDone) {
                    return;
                }

                currentShownDone = done;
            }
            
            int todo = width - done;
            PrintStream pw = System.out;

            pw.print("[");


            while (done-- > 0) {
                pw.print("=");
            }

            while (todo-- > 0) {
                pw.print(" ");
            }

            pw.print("]");

            if (moveCaret)
                pw.print("\r");
        }

    }

    @ServiceProvider(service=ClassPathProvider.class, position=9999/*DefaultClassPathProvider has 10000*/)
    public static final class BCPFallBack implements ClassPathProvider {

        @Override
        public ClassPath findClassPath(FileObject file, String type) {
            //hack, TestClassPathProvider does not have a reasonable position:
            for (ClassPathProvider p : Lookup.getDefault().lookupAll(ClassPathProvider.class)) {
                if (p instanceof TestClassPathProvider) {
                    ClassPath cp = ((TestClassPathProvider) p).findClassPath(file, type);

                    if (cp != null) {
                        return cp;
                    }
                }
            }

            if (ClassPath.BOOT.equals(type)) {
                return Utils.createDefaultBootClassPath();
            }
            return null;
        }
    
    }
}
