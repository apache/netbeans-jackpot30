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
package org.netbeans.modules.java.debugjavac;

import java.util.Arrays;
import java.util.Collection;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.spi.lexer.EmbeddingPresence;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author lahvac
 */
public enum TopLevelTokenId implements TokenId {
    SECTION_HEADER,
    JAVA,
    ASM,
    OTHER;

    @Override
    public String primaryCategory() {
        return this == SECTION_HEADER ? "comment" : "code";
    }
    
    private static final Language<TopLevelTokenId> LANGUAGE = new LanguageHierarchy<TopLevelTokenId>() {
        @Override protected Collection<TopLevelTokenId> createTokenIds() {
            return Arrays.asList(TopLevelTokenId.values());
        }
        @Override protected Lexer<TopLevelTokenId> createLexer(LexerRestartInfo<TopLevelTokenId> info) {
            return new TopLevelLexer(info);
        }
        @Override protected String mimeType() {
            return "text/x-java-decompiled";
        }
        @Override protected LanguageEmbedding<?> embedding(Token<TopLevelTokenId> token, LanguagePath languagePath, InputAttributes inputAttributes) {
            switch (token.id()) {
                case JAVA:
                    return LanguageEmbedding.create(Language.find("text/x-java"), 0, 0);
                default:
                    return null;
            }
        }

    }.language();
    
    @MimeRegistration(mimeType="text/x-java-decompiled", service=Language.class)
    public static final Language<TopLevelTokenId> language() {
        return LANGUAGE;
    }
}
