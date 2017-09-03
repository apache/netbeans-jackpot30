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
package org.netbeans.modules.jackpot30.ide.browsing;

import org.netbeans.modules.jackpot30.remoting.api.Utilities;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.editor.GuardedDocument;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.jackpot30.remoting.api.Utilities.RemoteSourceDescription;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;

/**
 *
 * @author lahvac
 */
@MimeRegistration(mimeType = "text/x-rjava", service = EditorKit.class)
public class RemoteEditorKit extends NbEditorKit {

    @Override
    public String getContentType() {
        return "text/x-rjava";
    }

    @Override
    public void read(Reader in, Document doc, int pos) throws IOException, BadLocationException {
        RemoteSourceDescription rsd = Utilities.remoteSource(doc);
        if (rsd != null) {
            //TODO: cache the content?
            try {
                URI sourceURI = new URI(rsd.idx.remote.toExternalForm() + "/source/cat?path=" + WebUtilities.escapeForQuery(rsd.idx.remoteSegment) + "&relative=" + WebUtilities.escapeForQuery(rsd.relative));

                in = new InputStreamReader(sourceURI.toURL().openStream(), "UTF-8");
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
        }

        super.read(in, doc, pos);

        if (doc instanceof GuardedDocument) {
            //XXX: bypassing the standard APIs
            ((GuardedDocument) doc).getGuardedBlockChain().addBlock(0, doc.getLength() + 1, true);
        }

        Highlighting.highlight(doc);
    }

}
