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

package org.netbeans.modules.jackpot30.compiler;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 *
 * @author lahvac
 */
public class CompilerResourcesImpl extends ResourceBundle {

    private final ResourceBundle delegate;

    public CompilerResourcesImpl() {
        String baseName = getClass().getName();

        delegate = CompilerResourcesImpl.getBundle(baseName, Control.getControl(Arrays.asList("java.properties")));
    }

    @Override
    protected Object handleGetObject(String key) {
        try {
            return delegate.getObject(key);
        } catch (MissingResourceException ex) {
            return null;
        }
    }

    @Override
    public Enumeration<String> getKeys() {
        return delegate.getKeys();
    }

}
