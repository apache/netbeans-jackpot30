/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.ide.browsing;

import org.netbeans.modules.jackpot30.remoting.api.Utilities;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.codeviation.pojson.Pojson;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.jackpot30.remoting.api.Utilities.RemoteSourceDescription;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author lahvac
 */
public class Highlighting {

    @MimeRegistration(mimeType = "text/x-rjava", service = HighlightsLayerFactory.class)
    public static class Factory implements HighlightsLayerFactory {
        @Override
        public HighlightsLayer[] createLayers(Context context) {
            return new HighlightsLayer[] {
                HighlightsLayer.create(Highlighting.class.getName() + ".coloring", ZOrder.SYNTAX_RACK, true, getColoringBag(context.getDocument())),
                HighlightsLayer.create(Highlighting.class.getName() + ".occurrences", ZOrder.CARET_RACK, true, getOccurrencesBag(context.getDocument()))
            };
        }
    }

    private static final RequestProcessor WORKER = new RequestProcessor(Highlighting.class.getName(), 10, false, false);

    public static void highlight(final Document doc) {
        final RemoteSourceDescription rsd = Utilities.remoteSource(doc);

        if (rsd == null) return ;

        WORKER.post(new Runnable() {
            @Override public void run() {
                doHighlight(doc, rsd.idx, rsd.relative);
            }
        });
    }

    private static void doHighlight(Document doc, RemoteIndex idx, String relativePath) {
        OffsetsBag target = new OffsetsBag(doc);

        try {
            URI sourceURI = new URI(idx.remote.toExternalForm() + "/ui/highlightData?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&relative=" + WebUtilities.escapeForQuery(relativePath));
            HighlightData highlightData = Pojson.load(HighlightData.class, sourceURI.toURL().openStream());
            int o = 0;
            Iterator<String> categoriesIt = highlightData.categories.iterator();
            Iterator<Long> spansIt = highlightData.spans.iterator();

            while (categoriesIt.hasNext() && spansIt.hasNext()) {
                Long span = spansIt.next();
                target.addHighlight(o, (int) (o + span), ColoringManager.getColoringImpl(categoriesIt.next()));
                o += span;
            }
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        getColoringBag(doc).setHighlights(target);
    }

    private static OffsetsBag getColoringBag(Document doc) {
        OffsetsBag bag = (OffsetsBag) doc.getProperty(Highlighting.class);

        if (bag == null) {
            doc.putProperty(Highlighting.class, bag = new OffsetsBag(doc));
        }

        return bag;
    }

    private static final Object KEY_OCCURRENCES_BAG = new Object();
    private static OffsetsBag getOccurrencesBag(Document doc) {
        OffsetsBag bag = (OffsetsBag) doc.getProperty(KEY_OCCURRENCES_BAG);

        if (bag == null) {
            doc.putProperty(KEY_OCCURRENCES_BAG, bag = new OffsetsBag(doc));
        }

        return bag;
    }

    public static final class HighlightData {
        final List<String> categories;
        final List<Long> spans;
        public HighlightData(List<String> cats, List<Long> spans) {
            this.categories = cats;
            this.spans = spans;
        }

        public HighlightData() {
            this(null, null);
        }

    }

    public static void registerComponentListener() {
        EditorRegistry.addPropertyChangeListener(L);
    }

    private static final PropertyChangeListener L = new PropertyChangeListener() {
        private JTextComponent old;
        private CaretListener caret;
        @Override public void propertyChange(PropertyChangeEvent evt) {
            JTextComponent current = EditorRegistry.focusedComponent();

            if (current == old) return ;

            if (old != null) {
                old.removeCaretListener(caret);
            }
            
            if (current != null) {
                final Document doc = current.getDocument();
                final RemoteSourceDescription rsd = Utilities.remoteSource(doc);

                if (rsd != null) {
                    current.addCaretListener(caret = new CaretListener() {
                        @Override public void caretUpdate(CaretEvent e) {
                            final int caret = e.getDot();

                            WORKER.post(new Runnable() {
                                @Override public void run() {
                                    try {
                                        URI sourceURI = new URI(rsd.idx.remote.toExternalForm() + "/ui/target?path=" + WebUtilities.escapeForQuery(rsd.idx.remoteSegment) + "&relative=" + WebUtilities.escapeForQuery(rsd.relative) + "&position=" + caret);
                                        Map<Object, Object> targetData = Pojson.load(HashMap.class, sourceURI.toURL().openStream());
                                        Object signature = targetData.get("signature");

                                        if (signature instanceof String) {
                                            URI localUsagesURI = new URI(rsd.idx.remote.toExternalForm() + "/ui/localUsages?path=" + WebUtilities.escapeForQuery(rsd.idx.remoteSegment) + "&relative=" + WebUtilities.escapeForQuery(rsd.relative) + "&signature=" + WebUtilities.escapeForQuery(String.valueOf(signature)) + "&usages=true");
                                            long[][] spans = Pojson.load(long[][].class, localUsagesURI.toURL().openStream());
                                            OffsetsBag bag = new OffsetsBag(doc);

                                            for (long[] span : spans) {
                                                bag.addHighlight((int) span[0], (int) span[1], ColoringManager.getColoringImpl(ColoringManager.KEY_MARK_OCCURRENCES));
                                            }

                                            getOccurrencesBag(doc).setHighlights(bag);
                                        }
                                    } catch (URISyntaxException ex) {
                                        Exceptions.printStackTrace(ex);
                                    } catch (IOException ex) {
                                        Exceptions.printStackTrace(ex);
                                    }
                                }
                            }, 300);//should reuse one task...
                        }
                    });

                }
            }
            
            old = current;
        }
    };
}
