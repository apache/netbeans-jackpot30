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
package org.netbeans.modules.jackpot30.indexer.usages;

import com.sun.source.util.TreePath;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.ElementFilter;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.jackpot30.backend.impl.spi.IndexAccessor;
import org.netbeans.modules.java.source.indexing.JavaCustomIndexer;
import org.netbeans.modules.java.source.parsing.JavacParser;
import org.netbeans.modules.java.source.parsing.JavacParserFactory;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.modules.parsing.impl.indexing.FileObjectIndexable;
import org.netbeans.modules.parsing.impl.indexing.MimeTypes;
import org.netbeans.modules.parsing.impl.indexing.SPIAccessor;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class IndexerImplTest extends NbTestCase {

    public IndexerImplTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        Set<String> mimeTypes = MimeTypes.getAllMimeTypes();
        if (mimeTypes == null) {
            mimeTypes = new HashSet<String>();
        } else {
            mimeTypes = new HashSet<String>(mimeTypes);
        }

        mimeTypes.add("text/x-java");
        MimeTypes.setAllMimeTypes(mimeTypes);
        
        org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
        clearWorkDir();
        CacheFolder.setCacheFolder(FileUtil.toFileObject(getWorkDir()));
        super.setUp();
    }

    public void testMethodSignatures() throws IOException {
        doMethodSignatureTest("package test; public class Test { public void test() {} }", "()V;");
        doMethodSignatureTest("package test; public class Test { public <T extends String> void test(java.util.Map<java.util.List<String>, T> m, boolean p) {} }", "<T:Ljava/lang/String;>(Ljava/util/Map<Ljava/util/List<Ljava/lang/String;>;TT;>;Z)V;");
        doMethodSignatureTest("package test; public class Test <T extends String> { public void test(java.util.Map<java.util.List<String>, T> m, boolean p) {} }", "(Ljava/util/Map<Ljava/util/List<Ljava/lang/String;>;TT;>;Z)V;");
        doMethodSignatureTest("package test; public class Test { public void test() throws java.io.IOException {} }", "()V^Ljava/io/IOException;;");
        doMethodSignatureTest("package test; public class Test { public void test(java.util.List<? extends String> l) {} }", "(Ljava/util/List<+Ljava/lang/String;>;)V;");
        doMethodSignatureTest("package test; public class Test <T extends String> { public <P extends T> void test(P p) {} }", "<P:TT;>(TP;)V;");
    }
    
    protected void doMethodSignatureTest(String code, final String signature) throws IOException {
        FileObject testFile = FileUtil.createData(new File(getWorkDir(), "Test.java"));

        copyToFile(testFile, code);

        final boolean[] invoked = new boolean[1];

        JavaSource.forFileObject(testFile).runUserActionTask(new Task<CompilationController>() {
            @Override public void run(CompilationController parameter) throws Exception {
                parameter.toPhase(JavaSource.Phase.RESOLVED);

                ExecutableElement method = ElementFilter.methodsIn(parameter.getTopLevelElements().get(0).getEnclosedElements()).iterator().next();

                assertEquals(signature, IndexerImpl.methodTypeSignature(parameter.getElements(), method));
                invoked[0] = true;
            }
        }, true);

        assertTrue(invoked[0]);
    }

    public void testOverriddenMethods() throws IOException {
        doOverriddenMethodsTest("package test; public class Test { public String toStr|ing() { return null; } }",
                                "METHOD:java.lang.Object:toString:()Ljava/lang/String;");
        doOverriddenMethodsTest("package test; public class Test extends A implements B { public void t|t() { } } class A implements B { public void tt() {} } interface B { public void tt(); }",
                                "METHOD:test.A:tt:()V",
                                "METHOD:test.B:tt:()V");
    }

    protected void doOverriddenMethodsTest(String code, final String... signature) throws IOException {
        final int pos = code.indexOf('|');

        code = code.replace("|", "");
        FileObject testFile = FileUtil.createData(new File(getWorkDir(), "Test.java"));
        
        copyToFile(testFile, code);

        final boolean[] invoked = new boolean[1];

        JavaSource.forFileObject(testFile).runUserActionTask(new Task<CompilationController>() {
            @Override public void run(CompilationController parameter) throws Exception {
                parameter.toPhase(JavaSource.Phase.RESOLVED);

                TreePath selected = parameter.getTreeUtilities().pathFor(pos);
                ExecutableElement method = (ExecutableElement) parameter.getTrees().getElement(selected);
                List<String> result = new ArrayList<String>();

                for (ExecutableElement ee : IndexerImpl.overrides(parameter.getTypes(), parameter.getElements(), method)) {
                    result.add(Common.serialize(ElementHandle.create(ee)));
                }

                assertEquals(Arrays.asList(signature), result);
                invoked[0] = true;
            }
        }, true);

        assertTrue(invoked[0]);
    }

    public void testRepeatedIndexing() throws IOException {
        final FileObject root = FileUtil.toFileObject(getWorkDir());
        FileObject testFile = FileUtil.createData(root, "Test.java");
        copyToFile(testFile, "public class Test {}");

        Directory store = new RAMDirectory();
        IndexWriter iw = new IndexWriter(store, new KeywordAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);
        IndexAccessor.current = new IndexAccessor(iw, root);

        iw.addDocument(fakeDocument(testFile));

        doIndex(root, testFile);
        
        iw.close();
        IndexReader ir = IndexReader.open(store);

        int expectedDocumentsCount = ir.numDocs();

        assertEquals(3 + 1, expectedDocumentsCount);

        store = new RAMDirectory();
        iw = new IndexWriter(store, new KeywordAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);
        IndexAccessor.current = new IndexAccessor(iw, root);

        iw.addDocument(fakeDocument(testFile));

        doIndex(root, testFile);
        doIndex(root, testFile);

        iw.close();
        ir = IndexReader.open(store);

        assertEquals(expectedDocumentsCount, ir.numDocs());
    }

    public void testSubdirIndexing() throws IOException {
        final FileObject root = FileUtil.toFileObject(getWorkDir());
        FileObject aFile = FileUtil.createData(root, "a/A.java");
        copyToFile(aFile, "public class A {}");
        FileObject bFile = FileUtil.createData(root, "b/B.java");
        copyToFile(bFile, "public class B {}");

        Directory store = new RAMDirectory();
        IndexWriter iw = new IndexWriter(store, new KeywordAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);
        IndexAccessor.current = new IndexAccessor(iw, root.getFileObject("a"));

        doIndex(root, aFile, bFile);

        iw.close();

        IndexReader ir = IndexReader.open(store);
        int maxDocs = ir.maxDoc();
        boolean foundA = false;
        
        for (int i = 0; i < maxDocs; i++) {
            Fieldable f = ir.document(i).getFieldable("file");
            
            if (f != null) {
                assertFalse(f.stringValue(), f.stringValue().contains("B"));
                if (f.stringValue().contains("A.java")) {
                    foundA = true;
                }
            }
        }

        assertTrue(foundA);
    }

    public void testTreePositions() throws IOException {
        doPositionTests("package test; public class Test { private Test() { ^Sy|stem.err.println(1); } }");
        doPositionTests("package test; public class Test { private Test() { System.^e|rr.println(1); } }");
        doPositionTests("package test; public class Test { private Test() { System.err.^p|rintln(1); } }");
    }

    private void doPositionTests(String code) throws IOException {
        final int caret = code.replace("^", "").indexOf('|');

        assertTrue("" + caret, caret != (-1));

        code = code.replace("|", "");

        final int expected = code.indexOf('^');

        assertTrue("" + expected, expected != (-1));

        FileObject testFile = FileUtil.createData(new File(getWorkDir(), "Test.java"));

        copyToFile(testFile, code.replace("^", ""));

        final boolean[] invoked = new boolean[1];

        JavaSource.forFileObject(testFile).runUserActionTask(new Task<CompilationController>() {
            @Override public void run(CompilationController parameter) throws Exception {
                parameter.toPhase(JavaSource.Phase.RESOLVED);

                TreePath tp = parameter.getTreeUtilities().pathFor(caret);

                assertEquals(expected, IndexerImpl.treePosition(parameter.getTrees(), tp));

                invoked[0] = true;
            }
        }, true);

        assertTrue(invoked[0]);
    }

    private void copyToFile(FileObject testFile, String code) throws IOException {
        OutputStream out = testFile.getOutputStream();
        
        try {
            out.write(code.getBytes());
        } finally {
            out.close();
        }
    }

    private Document fakeDocument(FileObject testFile) {
        //to test that unrelated document are not deleted:
        Document doc = new Document();

        doc.add(new Field("file", IndexAccessor.getCurrent().getPath(testFile.toURL()), Store.YES, Index.NOT_ANALYZED));

        return doc;
    }

    private void doIndex(final FileObject root, FileObject... testFiles) throws IOException, IllegalArgumentException {
        final boolean[] invoked = new boolean[1];

        for (FileObject testFile : testFiles) {
            JavaSource.forFileObject(testFile).runUserActionTask(new Task<CompilationController>() {
                @Override public void run(CompilationController parameter) throws Exception {
                    parameter.toPhase(JavaSource.Phase.RESOLVED);

                    new IndexerImpl(root.toURL()).process(parameter.getCompilationUnit(), SPIAccessor.getInstance().create(new FileObjectIndexable(root, parameter.getFileObject())), Lookups.fixed(parameter.getTrees(), parameter.getElements(), parameter.getTypes()));
                    invoked[0] = true;
                }
            }, true);
        }

        assertTrue(invoked[0]);
    }

    @ServiceProvider(service=MimeDataProvider.class)
    public static final class JavacParserProvider implements MimeDataProvider {

        private Lookup javaLookup = Lookups.fixed(new JavacParserFactory(), new JavaCustomIndexer.Factory());

        public Lookup getLookup(MimePath mimePath) {
            if (mimePath.getPath().endsWith(JavacParser.MIME_TYPE)) {
                return javaLookup;
            }

            return Lookup.EMPTY;
        }

    }

    @ServiceProvider(service=MIMEResolver.class)
    public static final class JavaMimeResolver extends MIMEResolver {

        public JavaMimeResolver() {
            super(JavacParser.MIME_TYPE);
        }

        @Override
        public String findMIMEType(FileObject fo) {
            if ("java".equals(fo.getExt())) {
                return JavacParser.MIME_TYPE;
            }

            return null;
        }

    }
}
