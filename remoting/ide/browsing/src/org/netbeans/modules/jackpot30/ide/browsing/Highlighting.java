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
