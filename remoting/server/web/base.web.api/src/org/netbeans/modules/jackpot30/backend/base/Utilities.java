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
package org.netbeans.modules.jackpot30.backend.base;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author lahvac
 */
public class Utilities {

    public static <T> Map<String, List<T>> sortBySourceRoot(List<Entry<String, T>> found, CategoryStorage category) {
        Map<String, List<T>> result = new LinkedHashMap<String, List<T>>();

        for (Entry<String, T> e : found) {
            for (SourceRoot sourceRoot : category.getSourceRoots()) {
                if (e.getKey().startsWith(sourceRoot.getRelativePath())) {
                    List<T> current = result.get(sourceRoot.getRelativePath());

                    if (current == null) {
                        result.put(sourceRoot.getRelativePath(), current = new ArrayList<T>());
                    }

                    current.add(e.getValue());
                }
            }
        }

        return result;
    }

}
