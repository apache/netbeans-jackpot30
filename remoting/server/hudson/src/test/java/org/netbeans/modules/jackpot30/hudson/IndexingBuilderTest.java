/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2010 Sun Microsystems, Inc.
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