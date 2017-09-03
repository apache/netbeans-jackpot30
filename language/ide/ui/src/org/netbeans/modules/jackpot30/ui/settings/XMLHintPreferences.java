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
package org.netbeans.modules.jackpot30.ui.settings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.openide.util.Exceptions;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author lahvac
 */
public class XMLHintPreferences extends AbstractPreferences {

    private final Element node;

    private XMLHintPreferences(XMLHintPreferences parent, String nodeName, Element node) {
        super(parent, nodeName);
        this.node = node;
    }

    @Override
    protected void putSpi(String key, String value) {
        node.setAttribute(escape(key), value);
    }

    @Override
    protected String getSpi(String key) {
        return node.hasAttribute(escape(key)) ? node.getAttribute(escape(key)) : null;
    }

    @Override
    protected void removeSpi(String key) {
        node.removeAttribute(key);
    }

    @Override
    protected void removeNodeSpi() throws BackingStoreException {
        node.getParentNode().removeChild(node);
    }

    @Override
    protected String[] keysSpi() throws BackingStoreException {
        List<String> keys = new ArrayList<String>();
        NamedNodeMap nnm = node.getAttributes();
        
        for (int i = 0; i < nnm.getLength(); i++) {
            keys.add(resolve(((Attr) nnm.item(i)).getName()));
        }

        return keys.toArray(new String[keys.size()]);
    }

    @Override
    protected String[] childrenNamesSpi() throws BackingStoreException {
        List<String> names = new ArrayList<String>();
        NodeList nl = node.getChildNodes();

        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);

            if (n instanceof Element) {
                names.add(resolve(((Element) n).getNodeName()));
            }
        }

        return names.toArray(new String[names.size()]);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        String escapedName = escape(name);
        NodeList nl = node.getChildNodes();

        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);

            if (n instanceof Element && escapedName.equals(((Element) n).getNodeName())) {
                return new XMLHintPreferences(this, name, (Element) n);
            }
        }

        Element nue = node.getOwnerDocument().createElement(escapedName);

        node.appendChild(nue);

        return new XMLHintPreferences(this, name, nue);
    }

    @Override
    protected void syncSpi() throws BackingStoreException {
        //TODO:
    }

    @Override
    public void flush() throws BackingStoreException {
        synchronized (lock) {
            parent().flush();
        }
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        throw new IllegalStateException();
    }

    private static String escape(String what) {
        return what.replace("__", "___").replace("$", "__d");
    }

    private static String resolve(String what) {
        return what.replace("__d", "$").replace("___", "__");
    }

    public static Preferences from(File file) throws IOException {
        if (!file.canRead()) {
            Document nueDocument = XMLUtil.createDocument("hints", null, null, null);
            
            return new RootXMLHintPreferences(nueDocument, file);
        }

        InputStream in = new BufferedInputStream(new FileInputStream(file));

        try {
            Document parsed = XMLUtil.parse(new InputSource(in), false, false, null, null);

            return new RootXMLHintPreferences(parsed, file);
        } catch (SAXException ex) {
            throw new IOException(ex);
        } finally {
            in.close();
        }
    }

    private static final class RootXMLHintPreferences extends XMLHintPreferences {
        private final Document doc;
        private final File file;

        public RootXMLHintPreferences(Document doc, File file) {
            super(null, "", doc.getDocumentElement());
            this.doc = doc;
            this.file = file;
        }

        @Override
        public void flush() throws BackingStoreException {
            synchronized (lock) {
                OutputStream out = null;
                try {
                    out = new BufferedOutputStream(new FileOutputStream(file));
                    XMLUtil.write(doc, out, "UTF-8");
                } catch (IOException ex) {
                    throw new BackingStoreException(ex);
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }

    }

}
