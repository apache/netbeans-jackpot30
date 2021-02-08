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
package org.netbeans.modules.java.debugjavac.impl;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.netbeans.modules.java.debugjavac.Decompiler.Input;
import org.netbeans.modules.java.debugjavac.Decompiler.Result;
import org.netbeans.modules.java.debugjavac.DecompilerDescription;

/**
 *
 * @author lahvac
 */
public class Main {
    public static void main(String... args) {
        try {
            Input input;

            try (XMLDecoder decoder = new XMLDecoder(System.in)) {
                input = (Input) decoder.readObject();
            }

            String id = args[0];

            for (DecompilerDescription desc : DecompilerDescription.getDecompilers()) {
                if (id.equals(desc.id)) {
                    Result result = desc.createDecompiler(Main.class.getClassLoader()).decompile(input);
                    try (XMLEncoder enc = new XMLEncoder(System.out)) {
                        enc.writeObject(result);
                    }
                    return ;
                }
            }

            throw new IllegalStateException("Cannot find: " + id);
        } catch (Throwable t) {
            try (XMLEncoder enc = new XMLEncoder(System.out)) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                pw.close();
                enc.writeObject(new Result(sw.toString()));
            }
        }
    }
}
