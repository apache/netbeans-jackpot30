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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

/**
 *
 * @author lahvac
 */
public class TopLevelLexer implements Lexer<TopLevelTokenId> {
    
    private static final Pattern SECTION_PATTERN = Pattern.compile("#Section\\(([^)]*)\\)[^\n]*\n");
    private final TokenFactory<TopLevelTokenId> factory;
    private final LexerInput input;
    private TopLevelTokenId futureToken;

    public TopLevelLexer(LexerRestartInfo<TopLevelTokenId> restart) {
        this.factory = restart.tokenFactory();
        this.input = restart.input();
        this.futureToken = restart.state() != null ? (TopLevelTokenId) restart.state() : TopLevelTokenId.OTHER;
    }
    
    @Override
    public Token<TopLevelTokenId> nextToken() {
        StringBuilder text = new StringBuilder();
        int read;
        
        while ((read = input.read()) != LexerInput.EOF) {
            text.append((char) read);
            
            Matcher m = SECTION_PATTERN.matcher(text);
            
            if (m.find()) {
                if (m.start() == 0) {
                    String mimeType = m.group(1);
                    
                    switch (mimeType) {
                        case "text/x-java": futureToken = TopLevelTokenId.JAVA; break;
                        case "text/x-java-bytecode": futureToken = TopLevelTokenId.ASM; break;
                        default: futureToken = TopLevelTokenId.OTHER; break;
                    }
                    
                    return factory.createToken(TopLevelTokenId.SECTION_HEADER);
                } else {
                    input.backup(input.readLength() - m.start());
                    break;
                }
            }
        }
        
        if (input.readLength() > 0)
            return factory.createToken(futureToken);
        
        return null;
    }

    @Override
    public Object state() {
        return futureToken;
    }

    @Override
    public void release() {}
    
}
