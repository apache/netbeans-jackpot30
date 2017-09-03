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
package org.netbeans.modules.jackpot30.indexer.source;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.netbeans.modules.jackpot30.backend.impl.spi.StatisticsGenerator;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=StatisticsGenerator.class)
public class SourceStatisticsGenerator extends StatisticsGenerator {

    @Override
    protected void amendStatistics(IndexReader r, Document doc) throws IOException {
        if (doc.getFieldable("relativePath") != null) {
            increment("files/total");
            Fieldable mimeType = doc.getFieldable("fileMimeType");
            if (mimeType != null) {
                increment("files/" + mimeType.stringValue());
            }
            Fieldable sizeInBytes = doc.getFieldable("sizeInBytes");
            if (sizeInBytes != null) {
                long size = Long.parseLong(sizeInBytes.stringValue());
                add("fileSize/total", size);
                if (mimeType != null) {
                    add("fileSize/" + mimeType.stringValue(), size);
                }
            }
        }
    }

}
