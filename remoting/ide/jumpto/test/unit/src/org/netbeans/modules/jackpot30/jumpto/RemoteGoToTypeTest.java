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
