/*
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

package org.netbeans.jackpot.prs.webapp;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;

/**
 *
 * @author lahvac
 */
public class WebApp {
    public static void main(String... origArgs) throws IOException {
        int port = 9996;

        List<String> args = new ArrayList<String>(Arrays.asList(origArgs));

        if (args.size() > 1 && "--port".equals(args.get(0))) {
            args.remove(0);
            port = Integer.parseInt(args.remove(0));
        }
        
        Config.getDefault().setConfigDir(Paths.get(args.remove(0)));
        
//        if (args.size() != 1 && args.size() != 2) {
//            System.err.println("Usage: java -jar " + WebApp.class.getProtectionDomain().getCodeSource().getLocation().getPath() + " [--port <port>] <cache> [<static-content>]");
//            return ;
//        }
//
        GrizzlyWebServer gws;

//        if (args.size() == 2) {
//            gws = new GrizzlyWebServer(port, args.get(1));
//        } else {
            gws = new GrizzlyWebServer(port);
//        }
        
        if (port == 0) {
            gws.getSelectorThread().setAddress(InetAddress.getByName("localhost"));
        }

        // Jersey web resources
        ServletAdapter jerseyAdapter = new ServletAdapter();
        jerseyAdapter.addInitParameter(ServerProperties.PROVIDER_PACKAGES, WebAppNotify.class.getPackageName());
        jerseyAdapter.addInitParameter(ServerProperties.TRACING, "ALL");
        jerseyAdapter.setContextPath("/");
        jerseyAdapter.setServletInstance(new ServletContainer());

        // register all above defined adapters
        gws.addGrizzlyAdapter(jerseyAdapter);

        // let Grizzly run
        gws.start();

        if (port == 0) {
            System.out.println("Running on port: " + gws.getSelectorThread().getPortLowLevel());
        }
    }

    public static final Logger LOG = Logger.getLogger(WebApp.class.getName());
}
