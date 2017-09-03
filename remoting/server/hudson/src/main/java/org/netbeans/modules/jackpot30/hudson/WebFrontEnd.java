/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011-2012 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2011-2012 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.hudson;

import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.cli.CLICommand;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Executable;
import hudson.model.Queue.FlyweightTask;
import hudson.model.Queue.NonBlockingTask;
import hudson.model.Queue.Task;
import hudson.model.Queue.TransientTask;
import hudson.model.ResourceList;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import hudson.util.ArgumentListBuilder;
import hudson.util.LogTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.jackpot30.hudson.IndexingBuilder.DescriptorImpl;

/**
 *
 * @author lahvac
 */
public class WebFrontEnd {

    public static boolean disable = false;

    private static Proc frontend;
    private static long requestId;

    public static synchronized void ensureStarted() {
        if (disable) return ;
        
        try {
            if (frontend != null && frontend.isAlive()) {
                frontend = null;
            }

            Queue.getInstance().schedule(new StartIndexingFrontEnd(++requestId), 0);
        } catch (IOException ex) {
            Logger.getLogger(WebFrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(WebFrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static synchronized void stop() {
        try {
            requestId++;

            if (frontend == null || !frontend.isAlive()) {
                frontend = null;
                return;
            }

            frontend.kill();
        } catch (IOException ex) {
            Logger.getLogger(WebFrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(WebFrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static synchronized void doStart(ProcStarter proc, long originalRequestId) throws IOException {
        if (requestId == originalRequestId) {
            frontend = proc.start();
        }
    }

    public static void restart() {
        stop();
        ensureStarted();
    }

    @Extension
    public static final class RestartIndexingFrontend extends CLICommand {

        @Override
        public String getShortDescription() {
            return "Restart indexing frontend";
        }

        @Override
        protected int run() throws Exception {
            stop();
            ensureStarted();
            return 0;
        }

    }

    private static class StartIndexingFrontEnd implements Task, TransientTask, FlyweightTask, NonBlockingTask {

        private final long requestId;

        public StartIndexingFrontEnd(long requestId) {
            this.requestId = requestId;
        }

        public boolean isBuildBlocked() {
            return false;
        }

        public String getWhyBlocked() {
            return null;
        }

        public CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        public String getName() {
            return getDisplayName();
        }

        public String getFullDisplayName() {
            return getDisplayName();
        }

        public void checkAbortPermission() {
            Hudson.getInstance().getACL().checkPermission(Hudson.ADMINISTER);
        }

        public boolean hasAbortPermission() {
            return Hudson.getInstance().getACL().hasPermission(Hudson.ADMINISTER);
        }

        public String getUrl() {
            return "index";
        }

        public boolean isConcurrentBuild() {
            return false;
        }

        public Collection<? extends SubTask> getSubTasks() {
            return Collections.singletonList(this);
        }

        public String getDisplayName() {
            return "Start Indexing Web Frontend";
        }

        public Label getAssignedLabel() {
            return Hudson.getInstance().getSelfLabel();
        }

        public Node getLastBuiltOn() {
            return null;
        }

        public long getEstimatedDuration() {
            return -1;
        }

        public Executable createExecutable() throws IOException {
            return new Executable() {
                public SubTask getParent() {
                    return StartIndexingFrontEnd.this;
                }

                public void run() {
        try {
            IndexingTool[] tools = Hudson.getInstance().getDescriptorByType(IndexingTool.DescriptorImpl.class).getInstallations();

            if (tools.length == 0) return;

            File cacheDir = Hudson.getInstance().getDescriptorByType(IndexingBuilder.DescriptorImpl.class).getCacheDir();

            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            LogTaskListener listener = new LogTaskListener(Logger.global, Level.INFO);
            IndexingTool tool = tools[0].forNode(Hudson.getInstance(), listener);

            ArgumentListBuilder args = new ArgumentListBuilder();
            Launcher launcher = new Launcher.LocalLauncher(listener);
            args.add(new File(tool.getHome(), "web.sh")); //XXX
            args.add(cacheDir);

            doStart(launcher.launch().cmds(args)
                                     .envs(Collections.singletonMap("JACKPOT_WEB_OPTS", ((DescriptorImpl) DescriptorImpl.find(DescriptorImpl.class.getName())).getWebVMOptions()))
                                     .stdout(listener),
                    requestId);
        } catch (IOException ex) {
            Logger.getLogger(WebFrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(WebFrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        }
                }

                public long getEstimatedDuration() {
                    return -1;
                }

                @Override
                public String toString() {
                    return getDisplayName();
                }

            };
        }

        public Task getOwnerTask() {
            return this;
        }

        public Object getSameNodeConstraint() {
            return null;
        }

        public ResourceList getResourceList() {
            return ResourceList.EMPTY;
        }

    }
}
