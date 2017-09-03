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
package org.netbeans.modules.jackpot30.ui.settings;

import java.io.File;
import java.util.Arrays;
import java.util.prefs.Preferences;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author lahvac
 */
public class XMLHintPreferencesTest extends NbTestCase {

    public XMLHintPreferencesTest(String name) {
        super(name);
    }

    public void testStorage() throws Exception {
        clearWorkDir();
        
        File storage = new File(getWorkDir(), "test.xml");
        Preferences p = XMLHintPreferences.from(storage);

        p.put("key", "value");
        p.node("subnode").put("innerkey", "innervalue");

        for (String phase : new String[] {"before-reload", "after-reload"}) {
            assertTrue(Arrays.equals(new String[] {"key"}, p.keys()));
            assertEquals("value", p.get("key", null));
            assertTrue(Arrays.equals(new String[] {"subnode"}, p.childrenNames()));

            Preferences subnode = p.node("subnode");

            assertEquals("innervalue", subnode.get("innerkey", null));

            p.flush();
            p = XMLHintPreferences.from(storage);
        }

        p.remove("key");
        p.put("key2", "value2");
        p.node("subnode").removeNode();
        p.node("subnode2").put("innerkey2", "innervalue2");

        for (String phase : new String[] {"before-reload", "after-reload"}) {
            assertTrue(Arrays.equals(new String[] {"key2"}, p.keys()));
            assertEquals("value2", p.get("key2", null));
            assertTrue(Arrays.equals(new String[] {"subnode2"}, p.childrenNames()));

            Preferences subnode = p.node("subnode2");

            assertEquals("innervalue2", subnode.get("innerkey2", null));

            p.flush();
            p = XMLHintPreferences.from(storage);
        }
    }

    public void testEscaping() throws Exception {
        clearWorkDir();

        File storage = new File(getWorkDir(), "test.xml");
        Preferences p = XMLHintPreferences.from(storage);

        p.put("a$b", "a$b");
        p.node("a$b").put("a$b", "a$b");

        for (String phase : new String[] {"before-reload", "after-reload"}) {
            assertTrue(Arrays.equals(new String[] {"a$b"}, p.keys()));
            assertEquals("a$b", p.get("a$b", null));
            assertTrue(Arrays.equals(new String[] {"a$b"}, p.childrenNames()));

            Preferences subnode = p.node("a$b");

            assertEquals("a$b", subnode.get("a$b", null));

            p.flush();
            p = XMLHintPreferences.from(storage);
        }
    }

}
