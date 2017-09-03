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
package org.netbeans.modules.jackpot30.remotingapi.options;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 *
 * @author lahvac
 */
public class Utils {

    public static String toDisplayName(URL url) {
        if ("file".equals(url.getProtocol())) {
            return url.getPath();
        } else {
            return url.toExternalForm();
        }
    }

    public static URL fromDisplayName(String path) {
        try {
            return new URL(path);
        } catch (MalformedURLException ex) {
            try {
                if (path.isEmpty()) return File.listRoots()[0].toURI().toURL();
                //local FS path?
                return new URL("file://" + path);
            } catch (MalformedURLException ex1) {
                try {
                    //no handler?
                    return new URL(null, path, new FakeURLStreamHandler());
                } catch (MalformedURLException ex2) {
                    //giving up
                    throw new IllegalStateException(ex2);
                }
            }
        }
    }

    private static final class FakeURLStreamHandler extends URLStreamHandler {
        @Override protected URLConnection openConnection(URL u) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
