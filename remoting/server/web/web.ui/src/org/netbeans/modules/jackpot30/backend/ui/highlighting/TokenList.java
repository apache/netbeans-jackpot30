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
package org.netbeans.modules.jackpot30.backend.ui.highlighting;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.jackpot30.resolve.api.CompilationInfo;

/**
 *
 * @author Jan Lahoda
 */
public class TokenList {

    private CompilationInfo info;
    private SourcePositions sourcePositions;
    private AtomicBoolean cancel;

    private TokenSequence ts;
        
    public TokenList(CompilationInfo info, TokenSequence<?> topLevel, AtomicBoolean cancel) {
        this.info = info;
        this.cancel = cancel;
        
        this.sourcePositions = info.getTrees().getSourcePositions();
        
                if (TokenList.this.cancel.get())
                    return ;
                
                assert topLevel.language() == JavaTokenId.language();
                
                    ts = topLevel;
                    ts.moveStart();
                    ts.moveNext(); //XXX: what about empty document
    }
    
    public void moveToOffset(long inputOffset) {
        final int offset = (int) inputOffset;

        if (offset < 0)
            return ;
                if (cancel.get())
                    return ;
                
                if (ts != null && !ts.isValid()) {
                    cancel.set(true);
                    return ;
                }

                    while (ts.offset() < offset) {
                        if (!ts.moveNext())
                            return ;
                    }
    }

    public void moveToEnd(Tree t) {
        if (t == null)
            return ;

        long end = sourcePositions.getEndPosition(info.getCompilationUnit(), t);

        if (end == (-1))
            return ;

        if (t.getKind() == Kind.ARRAY_TYPE) {
            moveToEnd(((ArrayTypeTree) t).getType());
            return ;
        }
        moveToOffset(end);
    }

    public void moveToEnd(Collection<? extends Tree> trees) {
        if (trees == null)
            return ;

        for (Tree t : trees) {
            moveToEnd(t);
        }
    }

    public void firstIdentifier(final TreePath tp, final String name, final Map<Tree, Token> tree2Token) {
                if (cancel.get())
                    return ;
                
                if (ts != null && !ts.isValid()) {
                    cancel.set(true);
                    return ;
                }
                
                if (ts == null)
                    return ;
                
                boolean next = true;

                while (ts.token().id() != JavaTokenId.IDENTIFIER && (next = ts.moveNext()))
                    ;

                if (next) {
                    if (name.equals(ts.token().text().toString())) {
                        tree2Token.put(tp.getLeaf(), ts.token());
                    } else {
//                            System.err.println("looking for: " + name + ", not found");
                    }
                }
    }

    public void identifierHere(final IdentifierTree tree, final Map<Tree, Token> tree2Token) {
                if (cancel.get())
                    return ;
                
                if (ts != null && !ts.isValid()) {
                    cancel.set(true);
                    return ;
                }
                
                if (ts == null)
                    return ;
                
                Token t = ts.token();

                if (t.id() == JavaTokenId.IDENTIFIER && tree.getName().toString().equals(t.text().toString())) {
    //                System.err.println("visit ident 1");
                    tree2Token.put(tree, ts.token());
                } else {
    //                System.err.println("visit ident 2");
                }
    }
    
    public void moveBefore(final List<? extends Tree> tArgs) {
                if (cancel.get())
                    return ;
                
                if (ts != null && !ts.isValid()) {
                    cancel.set(true);
                    return ;
                }
                
                if (ts == null)
                    return ;
                
                if (!tArgs.isEmpty()) {
                    int offset = (int) info.getTrees().getSourcePositions().getStartPosition(info.getCompilationUnit(), tArgs.get(0));
                    
                    if (offset < 0)
                        return ;
                    
                    while (ts.offset() >= offset) {
                        if (!ts.movePrevious()) {
                            return;
                        }
                    }
                }
    }

    public void moveNext() {
                if (cancel.get())
                    return ;
                
                if (ts != null && !ts.isValid()) {
                    cancel.set(true);
                    return ;
                }
                
                if (ts == null)
                    return ;
                
                ts.moveNext();
    }
    
}
