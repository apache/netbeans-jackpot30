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
package org.netbeans.modules.jackpot30.jumpto;

import java.util.HashMap;
import java.util.Map;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.jackpot30.jumpto.RemoteGoToSymbol.RemoteSymbolDescriptor;

/**
 *
 * @author lahvac
 */
public class RemoteGoToSymbolTest extends NbTestCase {

    public RemoteGoToSymbolTest(String testName) {
        super(testName);
    }

    public void testSymbolName() {
        doTestSymbolName("test", "()V;", "test()");
        RemoteGoToSymbol.generateSimpleNames = false;
        doTestSymbolName("test", "<T:Ljava/lang/String;>(Ljava/util/Map<Ljava/util/List<Ljava/lang/String;>;TT;>;Z)V;", "test(java.util.Map<java.util.List<java.lang.String>, T>, boolean)");
        RemoteGoToSymbol.generateSimpleNames = true;
        doTestSymbolName("test", "<T:Ljava/lang/String;>(Ljava/util/Map<Ljava/util/List<Ljava/lang/String;>;TT;>;Z)V;", "test(Map<List<String>, T>, boolean)");
        RemoteGoToSymbol.generateSimpleNames = false;
        doTestSymbolName("test", "(Ljava/util/List<+Ljava/lang/String;>;)V;", "test(java.util.List<? extends java.lang.String>)");
    }

    private void doTestSymbolName(String name, String signature, String golden) {
        Map<String, Object> props = new HashMap<String, Object>();

        props.put("simpleName", name);
        props.put("signature", signature);
        assertEquals(golden, new RemoteSymbolDescriptor(null, props).getSymbolName());
    }
}
