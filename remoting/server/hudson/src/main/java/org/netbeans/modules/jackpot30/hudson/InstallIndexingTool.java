/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.hudson;

import hudson.Extension;
import hudson.FilePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author lahvac
 */
public class InstallIndexingTool extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public InstallIndexingTool(String id) {
        super(id);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        return super.performInstallation(tool, node, log);
    }

    @Extension
    public static class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<InstallIndexingTool> {

        @Override
        public String getDisplayName() {
            return "Install from web";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == IndexingTool.class;
        }

    }

    private static final String INSTALLER_DESCRIPTION =
            "{\"list\": [{\"id\": \"main\", \"name\": \"main\", \"url\": \"http://deadlock.netbeans.org/hudson/job/jackpot30/lastSuccessfulBuild/artifact/remoting/build/indexing-backend.zip\"}]}";

    //XXX:
    @Initializer(after=InitMilestone.JOB_LOADED)
    public static void prepareUpdates() throws IOException, InterruptedException {
        FilePath update = new FilePath(new FilePath(Hudson.getInstance().getRootPath(), "updates"), "org.netbeans.modules.jackpot30.hudson.InstallIndexingTool");

        InputStream in = new ByteArrayInputStream(INSTALLER_DESCRIPTION.getBytes());

        try {
            update.copyFrom(in);
        } finally {
            in.close();
        }

        //preinstall main tool if it does not exist:
        IndexingTool[] tools = Hudson.getInstance().getDescriptorByType(IndexingTool.DescriptorImpl.class).getInstallations();

        if (tools.length == 0) {
            ToolProperty<ToolInstallation> install = new InstallSourceProperty(Arrays.asList(new InstallIndexingTool("main")));
            Hudson.getInstance().getDescriptorByType(IndexingTool.DescriptorImpl.class).setInstallations(new IndexingTool(IndexingTool.DEFAULT_INDEXING_NAME, "", Arrays.asList(install)));
        }
    }

}
