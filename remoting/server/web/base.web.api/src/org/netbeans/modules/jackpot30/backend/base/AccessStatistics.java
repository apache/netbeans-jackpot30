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
