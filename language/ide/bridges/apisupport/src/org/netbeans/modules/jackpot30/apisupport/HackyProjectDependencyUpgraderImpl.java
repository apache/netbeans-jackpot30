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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.netbeans.api.project.Project;
//import org.netbeans.modules.apisupport.project.api.DependencyUpdater;
import org.netbeans.modules.java.hints.spiimpl.ipi.upgrade.ProjectDependencyUpgrader;
import org.openide.filesystems.FileObject;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public class HackyProjectDependencyUpgraderImpl extends ProjectDependencyUpgrader {

    public boolean ensureDependency(Project currentProject, FileObject depFO, SpecificationVersion spec, final boolean canShowUI) {
        File lib = InstalledFileLocator.getDefault().locate("modules/ext/org-netbeans-modules-jackpot30-apisupport.jar", null, false);

        ClassLoader scl = Lookup.getDefault().lookup(ClassLoader.class);
        try {
            URLClassLoader l = new URLClassLoader(new URL[]{lib.toURI().toURL()}, scl);
            Class<?> u = Class.forName("org.netbeans.modules.jackpot30.apisupport.bridgeimpl.ProjectDependencyUpgraderImpl", false, l);

            return ((ProjectDependencyUpgrader) u.newInstance()).ensureDependency(currentProject, depFO, spec, canShowUI);
        } catch (InstantiationException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
        } catch (ClassNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        }

        return false;
    }

    @Override
    public boolean ensureDependency(Project p, String specification, boolean b) {
        File lib = InstalledFileLocator.getDefault().locate("modules/ext/org-netbeans-modules-jackpot30-apisupport.jar", null, false);

        ClassLoader scl = Lookup.getDefault().lookup(ClassLoader.class);
        try {
            URLClassLoader l = new URLClassLoader(new URL[]{lib.toURI().toURL()}, scl);
            Class<?> u = Class.forName("org.netbeans.modules.jackpot30.apisupport.bridgeimpl.ProjectDependencyUpgraderImpl", false, l);

            return ((ProjectDependencyUpgrader) u.newInstance()).ensureDependency(p, specification, b);
        } catch (InstantiationException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
        } catch (ClassNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        }

        return false;
    }

}
