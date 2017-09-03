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
package org.netbeans.modules.jackpot30.backend.impl.spi;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.index.IndexReader;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public abstract class StatisticsGenerator {

    protected abstract void amendStatistics(IndexReader r, Document doc) throws IOException;

    public static Map<String, Long> generateStatistics(IndexReader r) throws IOException {
        statistics = new HashMap<String, Long>();

        Collection<? extends StatisticsGenerator> generators = Lookup.getDefault().lookupAll(StatisticsGenerator.class);

        int maxDocs = r.maxDoc();

        for (int d = 0; d < maxDocs; d++) {
            Document doc = r.document(d, new FieldSelector() {
                @Override public FieldSelectorResult accept(String string) {
                    return FieldSelectorResult.LAZY_LOAD;
                }
            });

            for (StatisticsGenerator sg : generators) {
                sg.amendStatistics(r, doc);
            }
        }
        
        Map<String, Long> result = statistics;

        statistics = null;

        return result;
    }

    private static Map<String, Long> statistics;

    protected final void increment(String key) {
        add(key, 1);
    }

    protected void add(String key, long count) {
        Long val = statistics.get(key);

        if (val == null) val = 0L;

        statistics.put(key, val + count);
    }

}
