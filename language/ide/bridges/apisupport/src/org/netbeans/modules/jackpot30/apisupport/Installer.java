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
package org.netbeans.modules.jackpot30.apisupport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import org.openide.modules.ModuleInstall;

public class Installer extends ModuleInstall {

    @Override
    @SuppressWarnings("unchecked")
    public void validate() throws IllegalStateException {
        try {
            java.lang.Class main = java.lang.Class.forName("org.netbeans.core.startup.Main", false,  //NOI18N
                    Thread.currentThread().getContextClassLoader());
            Method getModuleSystem = main.getMethod("getModuleSystem", new Class[0]); //NOI18N
            Object moduleSystem = getModuleSystem.invoke(null, new Object[0]);
            Method getManager = moduleSystem.getClass().getMethod("getManager", new Class[0]); //NOI18N
            Object moduleManager = getManager.invoke(moduleSystem, new Object[0]);
            Method moduleMeth = moduleManager.getClass().getMethod("get", new Class[] {String.class}); //NOI18N
            Object persistence = moduleMeth.invoke(moduleManager, "org.netbeans.modules.apisupport.project"); //NOI18N
            if (persistence != null) {
                Field frField = persistence.getClass().getSuperclass().getDeclaredField("friendNames"); //NOI18N
                frField.setAccessible(true);
                Set friends = (Set)frField.get(persistence);
                friends.add("org.netbeans.modules.jackpot30.apisupport"); //NOI18N
            }
        } catch (Exception ex) {
            new IllegalStateException("Cannot fix dependencies for org.netbeans.modules.jackpot30.apisupport.", ex); //NOI18N
        }
        super.validate();
    }
}
