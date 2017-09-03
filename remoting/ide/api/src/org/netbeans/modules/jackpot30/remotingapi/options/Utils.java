/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
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
