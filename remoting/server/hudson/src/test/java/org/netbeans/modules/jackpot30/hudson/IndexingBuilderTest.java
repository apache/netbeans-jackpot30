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

package org.netbeans.modules.jackpot30.hudson;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.remoting.Channel;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jvnet.hudson.test.HudsonHomeLoader;
import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;

/**
 *
 * @author lahvac
 */
public class IndexingBuilderTest extends HudsonTestCase {

    public IndexingBuilderTest() {
    }

    @Override
    protected void setUp() throws Exception {
        WebFrontEnd.disable = true;
        
        super.setUp();

        checkoutDir = HudsonHomeLoader.NEW.allocate();
        toolDir = HudsonHomeLoader.NEW.allocate();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        //XXX: some of the working directories seems to be kept by the testing infra, workarounding:
        new FilePath(checkoutDir).deleteRecursive();
        new FilePath(toolDir).deleteRecursive();
        hudson.getRootPath().deleteRecursive();
    }

    private File checkoutDir;
    private File toolDir;

    public void testUpdate() throws Exception {
        IndexBuilderImpl indexer = new IndexBuilderImpl("test", "test");
        File projectMarkers = new File(toolDir, "indexer/test-cluster/patterns/project-marker-test");
        projectMarkers.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(projectMarkers);
        try {
            out.write(("(.*)/nbproject/project.xml\n" +
                       "(.*)/share/classes\n").getBytes("UTF-8"));
        } finally {
            out.close();
        }
        IndexingTool t = new IndexingTool("test", toolDir.getAbsolutePath(), NO_PROPERTIES);
        Hudson.getInstance().getDescriptorByType(IndexingTool.DescriptorImpl.class).setInstallations(t);
        FreeStyleProject p = createFreeStyleProject();
        SCM scm = new ProjectSCM();
        p.setScm(scm);
        p.getBuildersList().add(indexer);

        doRunProject(p);

        assertTrue(indexer.called);

        assertEquals(new File(toolDir, "index.sh").getAbsolutePath(), indexer.commands.get(0));
        assertEquals("test0", indexer.commands.get(1));
        assertEquals("test", indexer.commands.get(2));
        assertEquals(Arrays.asList("prj1", "src/prj2"), indexer.commands.subList(5, indexer.commands.size()));
    }

    private void doRunProject(FreeStyleProject p) throws SAXException, IOException, InterruptedException {
        WebClient w = new WebClient();
        w.getPage(p, "build?delay=0sec");

        Thread.sleep(5000);

        while (p.isBuilding()) {
            Thread.sleep(100);
        }

        assertEquals(p.getLastBuild().getLog(Integer.MAX_VALUE).toString(), Result.SUCCESS, p.getLastBuild().getResult());
    }

    private static final class ProjectSCM extends NullSCM {

        @Override
        public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher, FilePath remoteDir, BuildListener listener, File changeLogFile) throws IOException, InterruptedException {
            writeFile(remoteDir, "prj1/nbproject/project.xml");
            writeFile(remoteDir, "src/prj2/share/classes/");
            writeFile(remoteDir, "prj3/nothing");
            return true;
        }

        private void writeFile(FilePath remoteDir, String path) throws IOException, InterruptedException {
            FilePath target = new FilePath(remoteDir, path);
            target.getParent().mkdirs();
            target.write("", "UTF-8");
        }
    }
    private static final class IndexBuilderImpl extends IndexingBuilder {

        private boolean called;
        private List<String> commands;

        public IndexBuilderImpl(String projectName, String toolName) {
            super(projectName, toolName, "", "");
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            Launcher testLauncher = new Launcher(listener, launcher.getChannel()) {
                @Override
                public Proc launch(ProcStarter starter) throws IOException {
                    called = true;
                    commands = new ArrayList<String>(starter.cmds());
                    return new Proc() {
                        @Override
                        public boolean isAlive() throws IOException, InterruptedException {
                            return false;
                        }
                        @Override
                        public void kill() throws IOException, InterruptedException {}
                        @Override
                        public int join() throws IOException, InterruptedException {
                            return 0;
                        }
                        @Override
                        public InputStream getStdout() {
                            return new ByteArrayInputStream(new byte[0]);
                        }
                        @Override
                        public InputStream getStderr() {
                            return new ByteArrayInputStream(new byte[0]);
                        }
                        @Override
                        public OutputStream getStdin() {
                            return listener.getLogger(); //???
                        }
                    };
                }

                @Override
                public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                    throw new UnsupportedOperationException();
                }
            };
            return super.perform(build, testLauncher, listener);
        }

    }

}