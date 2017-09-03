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
import hudson.model.RootAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author lahvac
 */
@Extension
public class IndexGlobalAction implements RootAction {

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Jackpot 3.0 Index";
    }

    public String getUrlName() {
        return "index";
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        InputStream in = null;
        OutputStream out = null;
        try {
            URI delegateURI = new URI("http://localhost:9998/index" + req.getRestOfPath() + "?" + req.getQueryString());
            URLConnection inConnection = delegateURI.toURL().openConnection();
            in = inConnection.getInputStream();

            rsp.setContentType(inConnection.getContentType());
            rsp.setCharacterEncoding(inConnection.getContentEncoding());

            out = rsp.getOutputStream();
            int read;

            while ((read = in.read()) != (-1)) {
                out.write(read);
            }
        } catch (URISyntaxException ex) {
            Logger.getLogger(IndexGlobalAction.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }
}
