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
