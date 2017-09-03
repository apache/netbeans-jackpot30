/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.backend.base;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author lahvac
 */
public class AccessStatistics implements ContainerRequestFilter {

    private static Map<String, Long> statistics = null;
    private static long lastModifyStamp = 0;
    private static long lastSaveStamp = -1;
    private static ScheduledExecutorService store = Executors.newSingleThreadScheduledExecutor();

    private static synchronized void incrementUsage(String key) {
        if (statistics == null) {
            statistics = new HashMap<String, Long>();

            File accessStatistics = CategoryStorage.getAccessStatisticsFile();

            if (accessStatistics.canRead()) {
                InputStream in = null;

                try {
                    in = new BufferedInputStream(new FileInputStream(accessStatistics));
                    Properties p = new Properties();

                    p.load(in);

                    for (String propertyKey : p.stringPropertyNames()) {
                        try {
                            long count = Long.parseLong(p.getProperty(propertyKey));

                            statistics.put(propertyKey, count);
                        } catch (NumberFormatException ex) {
                            //ignore...
                            Logger.getLogger(AccessStatistics.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(AccessStatistics.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Logger.getLogger(AccessStatistics.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        }

        Long l = statistics.get(key);

        if (l == null) l = 0L;

        statistics.put(key, l + 1);

        lastModifyStamp++;

        store.schedule(new Runnable() {
            @Override public void run() {
                storeStatistics();
            }
        }, 1, TimeUnit.SECONDS);
    }

    private static void storeStatistics() {
        Properties p = new Properties();

        synchronized (AccessStatistics.class) {
            if (lastSaveStamp == lastModifyStamp) return;
            lastSaveStamp = lastModifyStamp;
            for (Entry<String, Long> e : statistics.entrySet()) {
                p.setProperty(e.getKey(), Long.toString(e.getValue()));
            }
        }

        File accessStatistics = CategoryStorage.getAccessStatisticsFile();
        File tempFile = new File(accessStatistics.getParentFile(), accessStatistics.getName() + ".new");
        OutputStream out = null;

        try {
            out = new BufferedOutputStream(new FileOutputStream(tempFile));

            p.store(out, "");
        } catch (IOException ex) {
            Logger.getLogger(AccessStatistics.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    Logger.getLogger(AccessStatistics.class.getName()).log(Level.SEVERE, null, ex);
                }

                tempFile.renameTo(accessStatistics);
            }
        }
    }

    public static synchronized Map<String, Long> getStatistics() {
        return Collections.unmodifiableMap(new HashMap<String, Long>(statistics));
    }

    @Override
    public ContainerRequest filter(ContainerRequest request) {
        StringBuilder statisticsKey = new StringBuilder();
        List<String> paths = request.getQueryParameters().get("path");

        statisticsKey.append(request.getPath());

        if (paths != null) {
            for (String path : paths) {
                statisticsKey.append(":").append(path);
            }
        }

        incrementUsage(statisticsKey.toString());
        
        return request;
    }

}
