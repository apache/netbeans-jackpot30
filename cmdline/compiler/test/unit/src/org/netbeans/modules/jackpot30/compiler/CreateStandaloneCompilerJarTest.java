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

package org.netbeans.modules.jackpot30.compiler;

import java.io.File;
import junit.extensions.TestSetup;
import junit.framework.Test;
import org.netbeans.junit.NbTestCase;
import org.netbeans.junit.NbTestSuite;

/**
 *
 * @author lahvac
 */
public class CreateStandaloneCompilerJarTest extends NbTestCase {

    public CreateStandaloneCompilerJarTest(String name) {
        super(name);
    }

    public static Test suite() {
        NbTestSuite suite = new NbTestSuite();
        
        suite.addTestSuite(HintsAnnotationProcessingTest.class);
        suite.addTestSuite(IndexingAnnotationProcessorTest.class);

        return new TestSetup(suite) {
            private File compiler;
            private File hintsList;
            protected void setUp() throws Exception {
                compiler = File.createTempFile("jackpotc", ".jar");
                hintsList = File.createTempFile("hints", "list");

//                if (!compiler.canRead()) {
                    new CreateStandaloneCompilerJar("").createCompiler(compiler, hintsList);
//                }
                System.setProperty("test.javacJar", compiler.getAbsolutePath());
            }
            protected void tearDown() {
                compiler.delete();
                hintsList.delete();
                compiler = null;
                hintsList = null;
            }
        };
    }

}
