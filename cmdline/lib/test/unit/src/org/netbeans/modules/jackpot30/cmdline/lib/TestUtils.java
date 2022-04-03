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
package org.netbeans.modules.jackpot30.cmdline.lib;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import static junit.framework.TestCase.assertNotNull;
import org.openide.filesystems.FileUtil;

public class TestUtils {

    //from TestUtilities:
    public final static File copyStringToFile (File f, String content) throws Exception {
        FileOutputStream os = new FileOutputStream(f);
        InputStream is = new ByteArrayInputStream(content.getBytes("UTF-8"));

        FileUtil.copy(is, os);

        os.close ();
        is.close();

        return f;

    }
    
    public final static String copyFileToString (java.io.File f) throws java.io.IOException {
        int s = (int)f.length ();
        byte[] data = new byte[s];
        int len = new FileInputStream (f).read (data);

        if (len != s)
            throw new EOFException("truncated file");

        return new String (data);
    }

}
