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
package org.netbeans.modules.jackpot.prs.handler.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.editor.java.JavaKit;
import org.netbeans.modules.editor.tools.storage.api.ToolPreferences;
import org.netbeans.modules.java.source.indexing.JavaCustomIndexer;
import org.netbeans.modules.java.source.parsing.JavacParserFactory;
import org.netbeans.spi.editor.hints.settings.FileHintPreferences;
import org.netbeans.spi.editor.hints.settings.FileHintPreferences.GlobalHintPreferencesProvider;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=MimeDataProvider.class)
public class MimeLookupImpl implements MimeDataProvider {

    private final Lookup JAVA = Lookups.fixed(new JavaKit(), new JavacParserFactory(), new GlobalSettingsProvider(), new JavaCustomIndexer.Factory());
    private final Lookup EMPTY = Lookups.fixed(new DocumentFactoryImpl());

    @Override
    public Lookup getLookup(MimePath path) {
        if (path.getPath().equals("text/x-java")) {
            return JAVA;
        }
        if (path.getPath().isEmpty()) {
            return EMPTY;
        }
        return Lookup.EMPTY;
    }
    
    public static final class GlobalSettingsProvider implements GlobalHintPreferencesProvider {

        public static final String HINTS_TOOL_ID = "hints";

        private Preferences prefs;

        @Override
        public synchronized Preferences getGlobalPreferences() {
            if (prefs == null) {
                try {
                    File cfgHints = File.createTempFile("cfg_hints", "xml");
                    try (InputStream in = MimeLookupImpl.class.getResourceAsStream("/org/netbeans/modules/jackpot/prs/handler/impl/cfg_hints.xml");
                         OutputStream out = new FileOutputStream(cfgHints)) {
                        out.write(in.readAllBytes());
                    }
                    prefs = ToolPreferences.from(cfgHints.toURI()).getPreferences(HINTS_TOOL_ID, "text/x-java");
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            
            return prefs;
        }
        
    }
}
