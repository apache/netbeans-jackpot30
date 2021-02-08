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
package org.netbeans.modules.java.debugjavac;

import java.util.Arrays;

/**
 *
 * @author lahvac
 */
public final class DecompilerDescription {
    public final String id;
    public final String displayName;
    public final String className;

    private DecompilerDescription(String id, String displayName, String className) {
        this.id = id;
        this.displayName = displayName;
        this.className = className;
    }

    public Decompiler createDecompiler(ClassLoader from) {
        try {
            Class<?> loadClass = from.loadClass(className);

            return Decompiler.class.cast(loadClass.newInstance());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final Iterable<? extends DecompilerDescription> DECOMPILERS = Arrays.asList(
        new DecompilerDescription("javap", "javap", "org.netbeans.modules.java.debugjavac.impl.JavapDecompilerImpl"),
        new DecompilerDescription("lower", "Desugared source", "org.netbeans.modules.java.debugjavac.impl.DesugarDecompilerImpl")
    );

    public static Iterable<? extends DecompilerDescription> getDecompilers() {
        return DECOMPILERS;
    }
}
