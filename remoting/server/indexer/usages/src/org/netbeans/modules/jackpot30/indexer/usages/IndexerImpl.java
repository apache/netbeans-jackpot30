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

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.apache.lucene.document.CompressionTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.modules.jackpot30.backend.impl.spi.IndexAccessor;
import org.netbeans.modules.java.preprocessorbridge.spi.JavaIndexerPlugin;
import org.netbeans.modules.java.source.usages.ClassFileUtil;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public class IndexerImpl implements JavaIndexerPlugin {

    private static final boolean NAVIGABLE = Boolean.getBoolean("jackpot.navigable.index");
            static final String KEY_SIGNATURES = "signatures";
            static final String KEY_MARKER = "usagesIndexMarker";

    private final URL root;

    public IndexerImpl(URL root) {
        this.root = root;
    }

    static long treePosition(Trees trees, TreePath tree) {
        switch (tree.getLeaf().getKind()) {
            case MEMBER_SELECT:
                return trees.getSourcePositions().getEndPosition(tree.getCompilationUnit(), tree.getLeaf()) - ((MemberSelectTree) tree.getLeaf()).getIdentifier().length();
        }

        return trees.getSourcePositions().getStartPosition(tree.getCompilationUnit(), tree.getLeaf());
    }

    @Override
    public void process(CompilationUnitTree toProcess, Indexable indexable, Lookup services) {
        if (!IndexAccessor.getCurrent().isAcceptable(indexable.getURL())) return;
        try {
            doDelete(indexable);
            
            final String file = IndexAccessor.getCurrent().getPath(indexable.getURL());
            final Trees trees = services.lookup(Trees.class);
            final Elements elements = services.lookup(Elements.class);
            final Types types = services.lookup(Types.class);
            final Document usages = new Document();

            usages.add(new Field("file", file, Store.YES, Index.NOT_ANALYZED));
            usages.add(new Field(KEY_MARKER, "true", Store.NO, Index.NOT_ANALYZED));

            final StringBuilder attributedSignatures = new StringBuilder();
            final StringBuilder attributedSubSignatures = new StringBuilder();

            new TreePathScanner<Void, Void>() {
                private final Set<String> SEEN_SIGNATURES = new HashSet<String>();
                @Override public Void visitIdentifier(IdentifierTree node, Void p) {
                    handleNode();
                    return super.visitIdentifier(node, p);
                }
                @Override public Void visitMemberSelect(MemberSelectTree node, Void p) {
                    handleNode();
                    return super.visitMemberSelect(node, p);
                }
                @Override public Void visitNewClass(NewClassTree node, Void p) {
                    handleNode();
                    return super.visitNewClass(node, p);
                }
                private void handleNode() {
                    Element el = trees.getElement(getCurrentPath());

                    if (el != null && Common.SUPPORTED_KINDS.contains(el.getKind())) {
                        String serialized = Common.serialize(ElementHandle.create(el));

                        if (SEEN_SIGNATURES.add(serialized)) {
                            usages.add(new Field(KEY_SIGNATURES, serialized, Store.YES, Index.NOT_ANALYZED));
                        }

                        long pos = treePosition(trees, getCurrentPath());

                        if (NAVIGABLE) {
                            attributedSignatures.append(Long.toString(pos));
                            attributedSignatures.append(":");
                            attributedSignatures.append(serialized);
                            attributedSignatures.append(",");
                            attributedSubSignatures.append(Long.toString(pos));
                            attributedSubSignatures.append(":");
                            attributedSubSignatures.append(serialized);
                            attributedSubSignatures.append(",");
                        }

                        if (el.getKind() == ElementKind.METHOD) {
                            for (ExecutableElement e : overrides(types, elements, (ExecutableElement) el)) {
                                serialized = Common.serialize(ElementHandle.create(e));

                                if (SEEN_SIGNATURES.add(serialized)) {
                                    usages.add(new Field(KEY_SIGNATURES, serialized, Store.YES, Index.NOT_ANALYZED));
                                }

                                if (NAVIGABLE) {
                                    attributedSubSignatures.append(Long.toString(pos));
                                    attributedSubSignatures.append(":");
                                    attributedSubSignatures.append(serialized);
                                    attributedSubSignatures.append(",");
                                }
                            }
                        }
                    }
                }

                private String currentClassFQN;
                @Override public Void visitClass(ClassTree node, Void p) {
                    String oldClassFQN = currentClassFQN;
                    boolean oldInMethod = inMethod;

                    try {
                        Element el = trees.getElement(getCurrentPath());

                        if (el != null) {
                            try {
                                TypeElement tel = (TypeElement) el;
                                currentClassFQN = elements.getBinaryName(tel).toString();
                                Document currentClassDocument = new Document();

                                currentClassDocument.add(new Field("classFQN", currentClassFQN, Store.YES, Index.NO));
                                currentClassDocument.add(new Field("classSimpleName", node.getSimpleName().toString(), Store.YES, Index.NOT_ANALYZED));
                                currentClassDocument.add(new Field("classSimpleNameLower", node.getSimpleName().toString().toLowerCase(), Store.YES, Index.NOT_ANALYZED));
                                currentClassDocument.add(new Field("classKind", el.getKind().name(), Store.YES, Index.NO));
                                for (Modifier m : el.getModifiers()) {
                                    currentClassDocument.add(new Field("classModifiers", m.name(), Store.YES, Index.NO));
                                }

                                recordSuperTypes(currentClassDocument, tel, new HashSet<String>(Arrays.asList(tel.getQualifiedName().toString())));

                                currentClassDocument.add(new Field("file", file, Store.YES, Index.NOT_ANALYZED));
                                currentClassDocument.add(new Field(KEY_MARKER, "true", Store.NO, Index.NOT_ANALYZED));

                                if (NAVIGABLE) {
                                    currentClassDocument.add(new Field("declarationSignature", Common.serialize(ElementHandle.create(el)), Store.YES, Index.NOT_ANALYZED));
                                    currentClassDocument.add(new Field("declarationPosition", Long.toString(trees.getSourcePositions().getStartPosition(getCurrentPath().getCompilationUnit(), node)), Store.YES, Index.NO));
                                }

                                IndexAccessor.getCurrent().getIndexWriter().addDocument(currentClassDocument);
                            } catch (CorruptIndexException ex) {
                                Exceptions.printStackTrace(ex);
                            } catch (IOException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }

                        inMethod = false;

                        return super.visitClass(node, p);
                    } finally {
                        currentClassFQN = oldClassFQN;
                        inMethod = oldInMethod;
                    }
                }

                private boolean inMethod;
                @Override public Void visitMethod(MethodTree node, Void p) {
                    boolean oldInMethod = inMethod;

                    try {
                        handleFeature();
                        inMethod = true;
                        return super.visitMethod(node, p);
                    } finally {
                        inMethod = oldInMethod;
                    }
                }

                @Override public Void visitVariable(VariableTree node, Void p) {
                    if (!inMethod)
                        handleFeature();
                    return super.visitVariable(node, p);
                }

                public void handleFeature() {
                    Element el = trees.getElement(getCurrentPath());

                    if (el != null) {
                        try {
                            Document currentFeatureDocument = new Document();

                            currentFeatureDocument.add(new Field("featureClassFQN", currentClassFQN, Store.YES, Index.NO));
                            currentFeatureDocument.add(new Field("featureSimpleName", el.getSimpleName().toString(), Store.YES, Index.NOT_ANALYZED));
                            currentFeatureDocument.add(new Field("featureSimpleNameLower", el.getSimpleName().toString().toLowerCase(), Store.YES, Index.NOT_ANALYZED));
                            currentFeatureDocument.add(new Field("featureKind", el.getKind().name(), Store.YES, Index.NO));
                            for (Modifier m : el.getModifiers()) {
                                currentFeatureDocument.add(new Field("featureModifiers", m.name(), Store.YES, Index.NO));
                            }
                            currentFeatureDocument.add(new Field("file", file, Store.YES, Index.NOT_ANALYZED));
                            currentFeatureDocument.add(new Field(KEY_MARKER, "true", Store.NO, Index.NOT_ANALYZED));

                            if (el.getKind() == ElementKind.METHOD || el.getKind() == ElementKind.CONSTRUCTOR) {
                                String featureSignature = methodTypeSignature(elements, (ExecutableElement) el);

                                currentFeatureDocument.add(new Field("featureSignature", featureSignature, Store.YES, Index.NO));
                                currentFeatureDocument.add(new Field("featureVMSignature", ClassFileUtil.createExecutableDescriptor((ExecutableElement) el)[2], Store.YES, Index.NO));

                                for (ExecutableElement e : overrides(types, elements, (ExecutableElement) el)) {
                                    currentFeatureDocument.add(new Field("featureOverrides", Common.serialize(ElementHandle.create(e)), Store.YES, Index.NOT_ANALYZED));
                                }
                            }

                            if (NAVIGABLE) {
                                currentFeatureDocument.add(new Field("declarationSignature", Common.serialize(ElementHandle.create(el)), Store.YES, Index.NOT_ANALYZED));
                                currentFeatureDocument.add(new Field("declarationPosition", Long.toString(trees.getSourcePositions().getStartPosition(getCurrentPath().getCompilationUnit(), getCurrentPath().getLeaf())), Store.YES, Index.NO));
                            }

                            IndexAccessor.getCurrent().getIndexWriter().addDocument(currentFeatureDocument);
                        } catch (CorruptIndexException ex) {
                            Exceptions.printStackTrace(ex);
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }.scan(toProcess, null);

            if (NAVIGABLE) {
                usages.add(new Field("attributedSignatures", CompressionTools.compressString(attributedSignatures.toString())));
                usages.add(new Field("attributedSubSignatures", CompressionTools.compressString(attributedSubSignatures.toString())));
            }
            
            IndexAccessor.getCurrent().getIndexWriter().addDocument(usages);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    static Collection<? extends ExecutableElement> overrides(Types types, Elements elements, ExecutableElement method) {
        TypeElement enclosing = (TypeElement) method.getEnclosingElement();
        List<TypeMirror> todo = new LinkedList<TypeMirror>(types.directSupertypes(enclosing.asType()));
        List<TypeMirror> seen = new ArrayList<TypeMirror>();
        List<ExecutableElement> result = new LinkedList<ExecutableElement>();

        OUTER: while (!todo.isEmpty()) {
            TypeMirror type = todo.remove(0);

            if (type.getKind() != TypeKind.DECLARED) continue;

            for (TypeMirror s : seen) {
                if (types.isSameType(s, type)) continue OUTER;
            }

            TypeElement te = (TypeElement) ((DeclaredType) type).asElement();

            for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
                if (elements.overrides(method, m, enclosing))
                    result.add(m);
            }

        }

        return result;
    }
    
    private static void recordSuperTypes(Document target, TypeElement tel, Set<String> alreadySeen) {
        String fqn = tel.getQualifiedName().toString();

        if (alreadySeen.add(fqn)) {
            target.add(new Field("classSupertypes", fqn, Store.YES, Index.NOT_ANALYZED));
        }

        if (tel.getSuperclass().getKind() == TypeKind.DECLARED) {
            recordSuperTypes(target, (TypeElement) ((DeclaredType) tel.getSuperclass()).asElement(), alreadySeen);
        }

        for (TypeMirror i : tel.getInterfaces()) {
            if (i.getKind() == TypeKind.DECLARED) {
                recordSuperTypes(target, (TypeElement) ((DeclaredType) i).asElement(), alreadySeen);
            }
        }
    }

    private static void encodeTypeParameters(Elements elements, Collection<? extends TypeParameterElement> params, StringBuilder result) {
        if (params.isEmpty()) return;
        result.append("<");
        for (TypeParameterElement tpe : params) {
            result.append(tpe.getSimpleName());
            boolean wasClass = false;
            
            for (TypeMirror tm : tpe.getBounds()) {
                if (tm.getKind() == TypeKind.DECLARED && !((DeclaredType) tm).asElement().getKind().isClass() && !wasClass) {
                    result.append(":Ljava/lang/Object;");
                }
                
                wasClass = true;
                result.append(':');
                encodeType(elements, tm, result);
            }
        }
        result.append(">");
    }
    
    static String methodTypeSignature(Elements elements, ExecutableElement ee) {
        StringBuilder sb = new StringBuilder ();
        encodeTypeParameters(elements, ee.getTypeParameters(), sb);
        sb.append('(');             // NOI18N
        for (VariableElement pd : ee.getParameters()) {
            encodeType(elements, pd.asType(),sb);
        }
        sb.append(')');             // NOI18N
        encodeType(elements, ee.getReturnType(), sb);
        for (TypeMirror tm : ee.getThrownTypes()) {
            sb.append('^');
            encodeType(elements, tm, sb);
        }
        sb.append(';'); //TODO: unsure about this, but classfile signatures seem to have it
        return sb.toString();
    }

    private static void encodeType(Elements elements, final TypeMirror type, final StringBuilder sb) {
	switch (type.getKind()) {
	    case VOID:
		sb.append('V');	    // NOI18N
		break;
	    case BOOLEAN:
		sb.append('Z');	    // NOI18N
		break;
	    case BYTE:
		sb.append('B');	    // NOI18N
		break;
	    case SHORT:
		sb.append('S');	    // NOI18N
		break;
	    case INT:
		sb.append('I');	    // NOI18N
		break;
	    case LONG:
		sb.append('J');	    // NOI18N
		break;
	    case CHAR:
		sb.append('C');	    // NOI18N
		break;
	    case FLOAT:
		sb.append('F');	    // NOI18N
		break;
	    case DOUBLE:
		sb.append('D');	    // NOI18N
		break;
	    case ARRAY:
		sb.append('[');	    // NOI18N
		assert type instanceof ArrayType;
		encodeType(elements, ((ArrayType)type).getComponentType(),sb);
		break;
	    case DECLARED:
            {
		sb.append('L');	    // NOI18N
                DeclaredType dt = (DeclaredType) type;
		TypeElement te = (TypeElement) dt.asElement();
                sb.append(elements.getBinaryName(te).toString().replace('.', '/'));
                if (!dt.getTypeArguments().isEmpty()) {
                    sb.append('<');
                    for (TypeMirror tm : dt.getTypeArguments()) {
                        encodeType(elements, tm, sb);
                    }
                    sb.append('>');
                }
		sb.append(';');	    // NOI18N
		break;
            }
	    case TYPEVAR:
            {
		assert type instanceof TypeVariable;
		TypeVariable tr = (TypeVariable) type;
                sb.append('T');
                sb.append(tr.asElement().getSimpleName());
                sb.append(';');
		break;
            }
            case WILDCARD: {
                WildcardType wt = (WildcardType) type;

                if (wt.getExtendsBound() != null) {
                    sb.append('+');
                    encodeType(elements, wt.getExtendsBound(), sb);
                } else if (wt.getSuperBound() != null) {
                    sb.append('-');
                    encodeType(elements, wt.getSuperBound(), sb);
                } else {
                    sb.append('*');
                }
                break;
            }
            case ERROR:
            {
                TypeElement te = (TypeElement) ((ErrorType)type).asElement();
                if (te != null) {
                    sb.append('L');
                    sb.append(elements.getBinaryName(te).toString().replace('.', '/'));
                    sb.append(';');	    // NOI18N
                    break;
                }
            }
	    default:
		throw new IllegalArgumentException (type.getKind().name());
	}
    }


    @Override
    public void delete(Indexable indexable) {
        try {
            doDelete(indexable);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void finish() {}

    private void doDelete(Indexable indexable) throws IOException {
        BooleanQuery q = new BooleanQuery();

        q.add(new BooleanClause(new TermQuery(new Term("file", IndexAccessor.getCurrent().getPath(indexable.getURL()))), Occur.MUST));
        q.add(new BooleanClause(new TermQuery(new Term(KEY_MARKER, "true")), Occur.MUST));

        IndexAccessor.getCurrent().getIndexWriter().deleteDocuments(q);
    }

    @MimeRegistration(mimeType="text/x-java", service=Factory.class)
    public static final class FactoryImpl implements Factory {

        @Override
        public JavaIndexerPlugin create(URL root, FileObject cacheFolder) {
            return new IndexerImpl(root);
        }

    }

}
