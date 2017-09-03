/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.jumpto;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.core.startup.Main;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.jackpot30.impl.TestUtils;
import org.netbeans.modules.jackpot30.jumpto.RemoteGoToType.RemoteTypeDescriptor;
import org.netbeans.modules.jackpot30.jumpto.RemoteQuery.ResultWrapper;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.jumpto.type.SearchType;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class RemoteGoToTypeTest extends NbTestCase {

    public RemoteGoToTypeTest(String name) {
        super(name);
    }

    private FileObject wd;
    private FileObject src1;
    private FileObject src2;

    @Override
    protected void setUp() throws Exception {
        Main.initializeURLFactory();
        super.setUp();

        clearWorkDir();
        wd = FileUtil.toFileObject(getWorkDir());
        assertNotNull(wd);

        src1 = FileUtil.createFolder(wd, "src1");
        src2 = FileUtil.createFolder(wd, "src2");
    }

    public void testQuery() throws Exception {
        URL index = new URL("test://index");

        RemoteIndex ri = RemoteIndex.create(wd.getURL(), index, "s1");

        RemoteIndex.saveIndices(Arrays.asList(ri));
        
        URL t1 = new URL("test://index/type/search?path=s1&prefix=Test");
        TestUtils.addRemoteContent(t1, "{ \"src1/\": [ \"test.Test\" ] }");
        ResultWrapperImpl rw = new ResultWrapperImpl();

        new RemoteGoToType(true).performQuery("Test", SearchType.PREFIX, rw);

        List<String> actual = new ArrayList<String>();

        for (RemoteTypeDescriptor d : rw.result) {
            actual.add(d.getContextName() + "." + d.getSimpleName());
        }

        assertEquals(Arrays.asList(" (test).Test"), actual);
    }

    public void testFiltering() throws Exception {
        ClassPath[] registeredCP = new ClassPath[] {ClassPathSupport.createClassPath(src1)};
        GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, registeredCP);
        URL index = new URL("test://index");

        RemoteIndex ri = RemoteIndex.create(wd.getURL(), index, "s1");

        RemoteIndex.saveIndices(Arrays.asList(ri));

        TestUtils.addRemoteContent(new URL("test://index/type/search?path=s1&prefix=Test"), "{ \"src1/\": [ \"test.Test\" ], \"src2/\": [ \"test.Test\" ] }");
        ResultWrapperImpl rw = new ResultWrapperImpl();

        new RemoteGoToType(true).performQuery("Test", SearchType.PREFIX, rw);

        List<String> actual = new ArrayList<String>();

        for (RemoteTypeDescriptor d : rw.result) {
            actual.add(d.getContextName() + "." + d.getSimpleName());
        }

        assertEquals(Arrays.asList(" (test).Test"), actual);
        GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, registeredCP);
    }

    private class ResultWrapperImpl implements ResultWrapper<RemoteTypeDescriptor> {

        public ResultWrapperImpl() {
        }
        private String message;
        private List<RemoteTypeDescriptor> result = new ArrayList<RemoteTypeDescriptor>();

        @Override
        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public void addResult(RemoteTypeDescriptor r) {
            result.add(r);
        }
    }

}
