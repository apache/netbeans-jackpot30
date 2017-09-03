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
package org.netbeans.modules.jackpot30.indexer.usages;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.SourceUtils;

/**XXX: Copied between indexing and ide!
 *
 * @author lahvac
 */
public class Common {

    public static final Set<ElementKind> SUPPORTED_KINDS = EnumSet.of(ElementKind.PACKAGE, ElementKind.CLASS,
            ElementKind.INTERFACE, ElementKind.ENUM, ElementKind.ANNOTATION_TYPE, ElementKind.METHOD,
            ElementKind.CONSTRUCTOR, ElementKind.INSTANCE_INIT, ElementKind.STATIC_INIT,
            ElementKind.FIELD, ElementKind.ENUM_CONSTANT);
    
    public static String serialize(ElementHandle<?> h) {
        StringBuilder result = new StringBuilder();

        result.append(h.getKind());

        String[] signatures = SourceUtils.getJVMSignature(h);

        if (h.getKind().isField()) {
            signatures = Arrays.copyOf(signatures, signatures.length - 1);
        }
        
        for (String sig : signatures) {
            result.append(":");
            result.append(sig);
        }

        return result.toString();
    }

}
