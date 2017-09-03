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
package org.netbeans.modules.jackpot30.cmdline.lib;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;

/**
 *
 * @author lahvac
 */
public class Utils {

    public static Map<String, String> computeId2DisplayName(Iterable<? extends HintDescription> descs) {
        final Map<String, String> id2DisplayName = new HashMap<>();

        for (HintDescription hd : descs) {
            if (hd.getMetadata() != null) {
                id2DisplayName.put(hd.getMetadata().id, hd.getMetadata().displayName);
            }
        }

        return id2DisplayName;
    }

    public static String categoryName(String id, Map<String, String> id2DisplayName) {
        if (id != null && id.startsWith("text/x-java:")) {
            id = id.substring("text/x-java:".length());
        }

        String idDisplayName = id2DisplayName.get(id);

        if (idDisplayName == null) {
            idDisplayName = "unknown";
        }

        for (Entry<String, String> remap : toIdRemap.entrySet()) {
            idDisplayName = idDisplayName.replace(remap.getKey(), remap.getValue());
        }

        idDisplayName = idDisplayName.replaceAll("[^A-Za-z0-9]", "_").replaceAll("_+", "_");

        idDisplayName = "[" + idDisplayName + "] ";

        return idDisplayName;
    }

    private static final Map<String, String> toIdRemap = new HashMap<String, String>() {{
        put("==", "equals");
        put("!=", "not_equals");
    }};

}
