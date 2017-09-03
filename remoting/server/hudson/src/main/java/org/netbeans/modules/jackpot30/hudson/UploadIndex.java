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
