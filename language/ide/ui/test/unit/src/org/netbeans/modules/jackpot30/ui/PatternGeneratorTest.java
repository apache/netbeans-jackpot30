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

package org.netbeans.modules.jackpot30.ui;

import com.sun.source.util.TreePath;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.modules.java.hints.infrastructure.TreeRuleTestBase;
import org.netbeans.modules.java.hints.jackpot.spi.PatternConvertor;
import org.netbeans.modules.java.hints.spiimpl.hints.HintsInvoker;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings.GlobalSettingsProvider;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class PatternGeneratorTest extends TreeRuleTestBase {

    public PatternGeneratorTest(String name) {
        super(name);
    }

    public void testStaticMethod() throws Exception {
        performTest("package test;\n" +
                    "public class Test {\n" +
                    "    public static void te|st(int i, String s) {\n" +
                    "        test(i, s);\n" +
                    "    }\n" +
                    "}\n",
                    "3:8-3:12:verifier:TODO: No display name");
    }

    public void testInstanceMethod() throws Exception {
        performTest("package test;\n" +
                    "public class Test {\n" +
                    "    public void te|st(int i, String s) {\n" +
                    "        new Test().test(i, s);\n" +
                    "        test(i, s);\n" +
                    "    }\n" +
                    "}\n",
                    "3:19-3:23:verifier:TODO: No display name",
                    "4:8-4:12:verifier:TODO: No display name");
    }

    public void testStaticField() throws Exception {
        performTest("package test;\n" +
                    "public class Test {\n" +
                    "    public static int I|I = 0;" +
                    "    public int test() {\n" +
                    "        return II;\n" +
                    "    }\n" +
                    "}\n",
                    "3:15-3:17:verifier:TODO: No display name");
    }

    public void testInstanceField() throws Exception {
        performTest("package test;\n" +
                    "public class Test {\n" +
                    "    public int I|I = 0;" +
                    "    public int test() {\n" +
                    "        if (true) return II; else return new Test().II;\n" +
                    "    }\n" +
                    "}\n",
                    "3:25-3:27:verifier:TODO: No display name",
                    "3:52-3:54:verifier:TODO: No display name");
    }

    private void performTest(String code, String... golden) throws Exception {
        performAnalysisTest("test/Test.java",
                            code,
                            golden);
    }

    @Override
    protected List<ErrorDescription> computeErrors(CompilationInfo info, TreePath path) {
        String script = PatternGenerator.generateFindUsagesScript(info, info.getTrees().getElement(path));

        return new HintsInvoker(HintsSettings.getSettingsFor(info.getFileObject()), new AtomicBoolean()).computeHints(info, PatternConvertor.create(script));
    }

    @Override
    public void testIssue105979() throws Exception {}

    @Override
    public void testIssue108246() throws Exception {}

    @Override
    public void testIssue113933() throws Exception {}

    @Override
    public void testNoHintsForSimpleInitialize() throws Exception {}

    @ServiceProvider(service=MimeDataProvider.class)
    public static class MimeLookupProviderImpl implements MimeDataProvider {
        private final Lookup lookup = Lookups.singleton(new GlobalSettingsProvider());

        @Override
        public Lookup getLookup(MimePath mimePath) {
            if ("text/x-java".equals(mimePath.getPath()))
                return lookup;
            return null;
        }
    }

}
