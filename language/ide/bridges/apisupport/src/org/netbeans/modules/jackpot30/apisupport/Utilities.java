/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2010 Sun Microsystems, Inc.
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
