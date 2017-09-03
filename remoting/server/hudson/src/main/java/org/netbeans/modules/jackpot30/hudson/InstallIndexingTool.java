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
