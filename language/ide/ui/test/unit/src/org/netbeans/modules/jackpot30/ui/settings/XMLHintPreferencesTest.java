/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
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
