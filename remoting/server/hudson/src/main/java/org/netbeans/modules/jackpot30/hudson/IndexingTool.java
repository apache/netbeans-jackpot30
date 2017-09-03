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
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import java.io.IOException;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author lahvac
 */
public class IndexingTool extends ToolInstallation implements NodeSpecific<IndexingTool> {

    public static final String DEFAULT_INDEXING_NAME = "Main NetBeans Indexing";
    
    @DataBoundConstructor
    public IndexingTool(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public IndexingTool forNode(Node node, TaskListener tl) throws IOException, InterruptedException {
        return new IndexingTool(getName(), translateFor(node, tl), getProperties().toList());
    }

    @Extension
    public static final class DescriptorImpl extends ToolDescriptor<IndexingTool> {

        @Override
        public String getDisplayName() {
            return "Indexing Tool";
        }

        private IndexingTool[] installations;

        @Override
        public IndexingTool[] getInstallations() {
            if (installations == null) {
                load();

                if (installations == null) {
                    installations = new IndexingTool[0];
                }
            }

            return installations.clone();
        }

        @Override
        public void setInstallations(IndexingTool... installations) {
            this.installations = installations.clone();
            save();
        }


    }
}
