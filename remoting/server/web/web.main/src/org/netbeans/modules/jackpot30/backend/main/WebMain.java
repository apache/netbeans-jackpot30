/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
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
package org.netbeans.modules.jackpot30.backend.main;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyOutputStream;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.netbeans.modules.jackpot30.backend.base.AccessStatistics;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.jackpot30.backend.base.RelStreamHandlerFactory;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class WebMain {

    /**
     * @param args the command line arguments
     */
    public static void main(String... origArgs) throws IOException, InterruptedException {
        int port = 9998;

        List<String> args = new ArrayList<String>(Arrays.asList(origArgs));

        if (args.size() > 1 && "--port".equals(args.get(0))) {
            args.remove(0);
            port = Integer.parseInt(args.remove(0));
        }

        if (args.size() != 1 && args.size() != 2) {
            System.err.println("Usage: java -jar " + WebMain.class.getProtectionDomain().getCodeSource().getLocation().getPath() + " [--port <port>] <cache> [<static-content>]");
            return ;
        }

        CategoryStorage.setCacheRoot(new File(args.get(0)));
        
//        org.netbeans.ProxyURLStreamHandlerFactory.register();
        URL.setURLStreamHandlerFactory(new RelStreamHandlerFactory());

        GrizzlyWebServer gws;

        if (args.size() == 2) {
            gws = new GrizzlyWebServer(port, args.get(1));
        } else {
            gws = new GrizzlyWebServer(port);
        }
        
        if (port == 0) {
            gws.getSelectorThread().setAddress(InetAddress.getByName("localhost"));
        }

        // Jersey web resources
        ServletAdapter jerseyAdapter = new ServletAdapter();
        jerseyAdapter.addInitParameter("com.sun.jersey.config.property.packages", "org.netbeans.modules.jackpot30");
        jerseyAdapter.addInitParameter("com.sun.jersey.spi.container.ContainerRequestFilters", AccessStatistics.class.getName());
//        jerseyAdapter.setContextPath("/");
        jerseyAdapter.setServletInstance(new ServletContainer());

        // register all above defined adapters
        gws.addGrizzlyAdapter(new GrizzlyAdapter(){
            public void service(GrizzlyRequest request, GrizzlyResponse response){
                if (request.getRequestURI().contains("/index/icons/")) {
                    String icon = request.getRequestURI().substring("/index".length());
                    URL iconURL = WebMain.class.getResource(icon);

                    if (iconURL == null) return;

                    InputStream in = null;
                    GrizzlyOutputStream out = null;

                    try {
                        in = iconURL.openStream();
                        out = response.createOutputStream();

                        int read;

                        while ((read = in.read()) != (-1)) {
                            out.write(read);
                        }
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    }
                }
                response.setStatus(404);
            }
        });
        gws.addGrizzlyAdapter(jerseyAdapter);

        // let Grizzly run
        gws.start();

        if (port == 0) {
            System.out.println("Running on port: " + gws.getSelectorThread().getPortLowLevel());
        }
    }

}
