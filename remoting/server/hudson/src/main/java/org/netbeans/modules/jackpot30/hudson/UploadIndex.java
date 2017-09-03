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
import hudson.cli.CLICommand;
import hudson.model.RootAction;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import org.apache.commons.fileupload.FileItem;
import org.kohsuke.args4j.Argument;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.netbeans.modules.jackpot30.hudson.IndexingBuilder.DescriptorImpl;

/**
 *
 * @author lahvac
 */
public class UploadIndex {

    public static void uploadIndex(String codeName, InputStream ins) throws IOException, InterruptedException {
        File cacheDir = ((DescriptorImpl) DescriptorImpl.find(DescriptorImpl.class.getName())).getCacheDir();
        File oldCacheDir = new File(cacheDir, codeName + ".old");
        File segCacheDir = new File(cacheDir, codeName);
        File newCacheDir = new File(cacheDir, codeName + ".new");

        try {
            new FilePath(newCacheDir).unzipFrom(ins);

            segCacheDir.renameTo(oldCacheDir);

            new File(newCacheDir, codeName).renameTo(segCacheDir);

            try {
                new URL("http://localhost:9998/index/internal/indexUpdated").openStream().close();
            } catch (IOException ex) {
                //inability to refresh the web frontend should not crash the build...
                LOG.log(Level.FINE, null, ex);
            }
        } finally {
            new FilePath(newCacheDir).deleteRecursive();
            new FilePath(oldCacheDir).deleteRecursive();
        }
    }
    
    private static final Logger LOG = Logger.getLogger(UploadIndex.class.getName());

    @Extension
    public static final class UploadIndexCommand extends CLICommand {

        @Argument(metaVar="<jobname>", usage="Job name")
        public String job;

        @Override
        public String getShortDescription() {
            return "Upload indexing zip";
        }

        @Override
        protected int run() throws Exception {
            uploadIndex(job, stdin);
            stdin.close();
            return 0;
        }

    }

    @Extension
    public static class IndexGlobalAction implements RootAction {

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return "Jackpot 3.0 Index Upload";
        }

        public String getUrlName() {
            return "index-upload";
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            FileItem indexFile = req.getFileItem("index");
            String codeName = req.getHeader("codeName");
            InputStream ins = indexFile.getInputStream();

            try {
                uploadIndex(codeName, ins);
            } finally {
                ins.close();
            }
        }
    }
}
