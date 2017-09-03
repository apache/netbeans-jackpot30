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
