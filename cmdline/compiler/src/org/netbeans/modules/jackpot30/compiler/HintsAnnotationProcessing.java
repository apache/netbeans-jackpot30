/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.compiler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.annotation.processing.ProcessingEnvironment;
import javax.swing.text.BadLocationException;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.netbeans.api.java.source.ClasspathInfo.PathKind;
import org.netbeans.api.java.source.CompilationInfoHack;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.modules.jackpot30.indexing.batch.BatchUtilities;
import org.netbeans.modules.java.hints.declarative.DeclarativeHintRegistry;
import org.netbeans.modules.java.hints.jackpot.spi.HintsRunner;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;
import org.netbeans.modules.java.hints.providers.spi.HintMetadata;
import org.netbeans.modules.java.hints.spiimpl.JavaFixImpl;
import org.netbeans.modules.java.hints.spiimpl.RulesManager;
import org.netbeans.modules.java.hints.spiimpl.Utilities;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.Severity;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintSeverity;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=AbstractHintsAnnotationProcessing.class)
public class HintsAnnotationProcessing extends AbstractHintsAnnotationProcessing {

    static final String CLASSPATH_HINTS_ENABLE = "jackpot30_enable_cp_hints";
    static final String CLASSPATH_HINTS_FIXES_ENABLE = "jackpot30_apply_cp_hints";
    static final String HARDCODED_HINTS_ENABLE = "jackpot30_enabled_hc_hints";
    static final String HARDCODED_HINTS_FIXES_ENABLE = "jackpot30_apply_hc_hints";
    static final String EXTRA_HINTS = "jackpot30_extra_hints";

    public static final Set<String> OPTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        CLASSPATH_HINTS_ENABLE, CLASSPATH_HINTS_FIXES_ENABLE,
        HARDCODED_HINTS_ENABLE,
        HARDCODED_HINTS_FIXES_ENABLE,
        EXTRA_HINTS
    )));

    private Writer diff;

    @Override
    protected boolean initialize(ProcessingEnvironment processingEnv) {
        try {
            File tmp = File.createTempFile("jackpot30", null);

            tmp.delete();
            tmp.mkdirs();
            tmp.deleteOnExit();

            tmp = FileUtil.normalizeFile(tmp);
            FileUtil.refreshFor(tmp.getParentFile());

            org.openide.filesystems.FileObject tmpFO = FileUtil.toFileObject(tmp);

            if (tmpFO == null) {
                return false;
            }

            CacheFolder.setCacheFolder(tmpFO);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return true;
    }

    @Override
    protected void doProcess(CompilationInfoHack info, ProcessingEnvironment processingEnv, Reporter reporter) {
        Set<HintDescription> hardCodedHints = new LinkedHashSet<HintDescription>();

        for (Collection<? extends HintDescription> v : RulesManager.getInstance().readHints(null, null, null).values()) {
            hardCodedHints.addAll(v);
        }

        ContainsChecker<String> enabledHints = getEnabledHardcodedHints(processingEnv);

        for (Iterator<HintDescription> it = hardCodedHints.iterator(); it.hasNext(); ) {
            HintMetadata current = it.next().getMetadata();

            if (   (current.kind == Hint.Kind.INSPECTION)
                && enabledHints.contains(current.id)) {
                continue;
            }

            it.remove();
        }

        ContainsChecker<String> enabledApplyHints = getApplyHardcodedFixes(processingEnv);

        List<HintDescription> hintDescriptions = new LinkedList<HintDescription>(hardCodedHints);

        if (isEnabled(processingEnv, CLASSPATH_HINTS_ENABLE)) {
            hintDescriptions.addAll(new LinkedList<HintDescription>(Utilities.listClassPathHints(Collections.singleton(info.getClasspathInfo().getClassPath(PathKind.SOURCE)), Collections.singleton(info.getClasspathInfo().getClassPath(PathKind.COMPILE)))));
        }

        boolean applyCPHints = isEnabled(processingEnv, CLASSPATH_HINTS_FIXES_ENABLE);

        Collection<? extends HintDescription> extraHints = getExtraHints(processingEnv);

        hintDescriptions.addAll(extraHints);

        Map<HintDescription, List<ErrorDescription>> hints = HintsRunner.computeErrors(info, hintDescriptions, new AtomicBoolean());

        try {
            boolean fixPerformed = false;

            for (Entry<HintDescription, List<ErrorDescription>> e : hints.entrySet()) {
                boolean applyFix = hardCodedHints.contains(e.getKey()) ? enabledApplyHints.contains(e.getKey().getMetadata().id) : extraHints.contains(e.getKey()) ? true : applyCPHints;

                for (ErrorDescription ed : e.getValue()) {
                    reporter.warning(ed.getRange().getBegin().getOffset(), ed.getDescription());

                    if (!applyFix) continue;

                    Fix f = ed.getFixes().getFixes().get(0);

                    if (!(f instanceof JavaFixImpl)) {
                        reporter.warning(ed.getRange().getBegin().getOffset(), "Cannot apply primary fix (not a JavaFix)");
                        continue;
                    }

                    JavaFixImpl jfi = (JavaFixImpl) f;

                    try {
                        JavaFixImpl.Accessor.INSTANCE.process(jfi.jf, info, false, null, new ArrayList<RefactoringElementImplementation>());
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }

                    fixPerformed = true;
                }
            }

            if (fixPerformed) {
                ModificationResult mr = info.computeResult();

                if (diff == null) {
                    FileObject upgradeDiffFO = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", "META-INF/upgrade/upgrade.diff");

                    diff = new OutputStreamWriter(upgradeDiffFO.openOutputStream());
                }

                BatchUtilities.exportDiff(mr, null, diff);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    protected void finish() {
        if (diff != null) {
            try {
                diff.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        diff = null;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return OPTIONS;
    }

    private boolean isEnabled(ProcessingEnvironment processingEnv, String key) {
        if (processingEnv.getOptions().containsKey(key)) {
            return Boolean.valueOf(processingEnv.getOptions().get(key));
        }

        return true;
    }

    private ContainsChecker<String> getApplyHardcodedFixes(ProcessingEnvironment processingEnv) {
        if (processingEnv.getOptions().containsKey(HARDCODED_HINTS_FIXES_ENABLE)) {
            String toSplit = processingEnv.getOptions().get(HARDCODED_HINTS_FIXES_ENABLE);

            if (toSplit == null) {
                return new HardcodedContainsChecker<String>(false);
            }

            return new SetBasedContainsChecker<String>(new HashSet<String>(Arrays.asList(toSplit.split(":"))));
        }

        return new HardcodedContainsChecker<String>(true);
    }

    private ContainsChecker<String> getEnabledHardcodedHints(ProcessingEnvironment processingEnv) {
        if (processingEnv.getOptions().containsKey(HARDCODED_HINTS_ENABLE)) {
            String toSplit = processingEnv.getOptions().get(HARDCODED_HINTS_ENABLE);

            if (toSplit == null) {
                return new HardcodedContainsChecker<String>(false);
            }
            if ("all".equals(toSplit)) {
                return new HardcodedContainsChecker<String>(true);
            }
            if ("defaults".equals(toSplit)) {
                return new SettingsBasedChecker();
            }

            return new SetBasedContainsChecker<String>(new HashSet<String>(Arrays.asList(toSplit.split(":"))));
        }

        return new HardcodedContainsChecker<String>(false);
    }

    private interface ContainsChecker<T> {
        public boolean contains(T t);
    }

    private static final class SetBasedContainsChecker<T> implements ContainsChecker<T> {
        private final Set<T> set;
        public SetBasedContainsChecker(Set<T> set) {
            this.set = set;
        }
        public boolean contains(T t) {
            return set.contains(t);
        }
    }

    private static final class HardcodedContainsChecker<T> implements ContainsChecker<T> {
        private final boolean result;
        public HardcodedContainsChecker(boolean result) {
            this.result = result;
        }
        public boolean contains(T t) {
            return result;
        }
    }

    private static final class SettingsBasedChecker implements ContainsChecker<String> {
        private static final Set<String> enabled = new HashSet<String>();
        public SettingsBasedChecker() {
            HintsSettings hintsSettings = HintsSettings.getGlobalSettings();
            for (HintMetadata hm : RulesManager.getInstance().readHints(null, null, null).keySet()) {
                if (   hintsSettings.isEnabled(hm)
                    && hintsSettings.getSeverity(hm) != Severity.HINT) {
                    enabled.add(hm.id);
                }
            }
        }
        public boolean contains(String t) {
            return enabled.contains(t);
        }
    }

    private Collection<? extends HintDescription> getExtraHints(ProcessingEnvironment processingEnv) {
        if (processingEnv.getOptions().containsKey(EXTRA_HINTS)) {
            String toSplit = processingEnv.getOptions().get(EXTRA_HINTS);

            if (toSplit == null || toSplit.length() == 0) {
                return Collections.emptyList();
            }

            List<HintDescription> result = new LinkedList<HintDescription>();

            for (String part : toSplit.split(Pattern.quote(System.getProperty("path.separator")))) {
                File resolved = FileUtil.normalizeFile(new File(part).getAbsoluteFile());

                if (!resolved.isFile()) {
                    processingEnv.getMessager().printMessage(Kind.WARNING, "Cannot resolve hint file: " + part);
                    continue;
                }

                for (Collection<? extends HintDescription> v : DeclarativeHintRegistry.parseHintFile(FileUtil.toFileObject(resolved)).values()) {
                    result.addAll(v);
                }
            }

            return result;
        }

        return Collections.emptyList();
    }

}
