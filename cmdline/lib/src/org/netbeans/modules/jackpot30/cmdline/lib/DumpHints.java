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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
//import org.codeviation.pojson.Pojson;
import org.netbeans.modules.java.hints.providers.spi.HintMetadata;
import org.netbeans.modules.java.hints.spiimpl.RulesManager;

/**
 *
 * @author lahvac
 */
public class DumpHints {

    public static String dumpHints() {
//        List<Map<String, String>> data = new LinkedList<Map<String, String>>();
//
//        for (HintMetadata hm : RulesManager.getInstance().readHints(null, null, null).keySet()) {
//            Map<String, String> hintData = new HashMap<String, String>();
//
//            hintData.put("id", hm.id);
//            hintData.put("category", hm.category);
//            hintData.put("displayName", hm.displayName);
//            hintData.put("description", hm.description);
//            hintData.put("enabled", Boolean.toString(hm.enabled));
//
//            data.add(hintData);
//        }
//
//        return Pojson.save(data);
        throw new IllegalStateException();
    }

    public static void main(String[] args) {
        System.out.println(dumpHints());
    }

}
