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
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Project;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.netbeans.modules.jackpot30.hudson.IndexingBuilder.DescriptorImpl;

/**
 *
 * @author lahvac
 */
public class ClearIndexProperty extends JobProperty<Job<?, ?>> {
    
    @Override
    public Collection<? extends Action> getJobActions(Job<?, ?> job) {
        return Collections.singleton(new ClearIndexAction(job));
    }

    public static final class ClearIndexAction implements Action {

        private final Job<?, ?> job;

        public ClearIndexAction(Job<?, ?> job) {
            this.job = job;
        }

        public String getIconFileName() {
            return "folder-delete.gif";
        }

        public String getDisplayName() {
            return "Clear Job Index";
        }

        public String getUrlName() {
            return "clearIndex";
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            File cacheDir = ((DescriptorImpl) DescriptorImpl.find(DescriptorImpl.class.getName())).getCacheDir();
            File jobCacheDir = new File(cacheDir, IndexingBuilder.codeNameForJob(job));

            deleteRecursivelly(jobCacheDir);
            
            rsp.forwardToPreviousPage(req);
        }

        private static void deleteRecursivelly(File f) {
            File[] files = f.listFiles();

            if (files != null) {
                for (File c : files) {
                    deleteRecursivelly(c);
                }
            }

            f.delete();
        }
    }

    @Extension // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class ClearIndexPropertyDescription extends JobPropertyDescriptor  {

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ClearIndexProperty();
        }

        @Override
        public String getDisplayName() {
            return "Clear Indices Desc";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return Project.class.isAssignableFrom(jobType);
        }

    }
}
