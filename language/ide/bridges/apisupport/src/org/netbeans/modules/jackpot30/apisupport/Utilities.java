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

package org.netbeans.modules.jackpot30.apisupport;

import org.netbeans.modules.java.hints.spiimpl.ipi.upgrade.ProjectDependencyUpgrader;
import org.openide.modules.SpecificationVersion;

/**
 *
 * @author lahvac
 */
public class Utilities {

    public static final ProjectDependencyUpgrader UPGRADER = Boolean.getBoolean("jackpot.upgrade.apisupport.use.api") ? new APIProjectDependencyUpgraderImpl() : new HackyProjectDependencyUpgraderImpl();

    public static ParsedDependency parseDependency(String spec) {
        String cnb;
        String releaseVersion;
        SpecificationVersion specVersion;

        String[] spaceSplit = spec.split(" ");
        String[] slashSplit = spaceSplit[0].split(" ");

        cnb = slashSplit[0];

        if (slashSplit.length > 1) {
            releaseVersion = slashSplit[1];
        } else {
            releaseVersion = null;
        }

        if (spaceSplit.length > 1) {
            specVersion = new SpecificationVersion(spaceSplit[1]);//XXX verify correct format
        } else {
            specVersion = null;
        }

        return new ParsedDependency(cnb, releaseVersion, specVersion);
    }
    
    public static class ParsedDependency {
        public final String cnb;
        public final String releaseVersion;
        public final SpecificationVersion specVersion;
        public ParsedDependency(String cnb, String releaseVersion, SpecificationVersion specVersion) {
            this.cnb = cnb;
            this.releaseVersion = releaseVersion;
            this.specVersion = specVersion;
        }
    }
}
