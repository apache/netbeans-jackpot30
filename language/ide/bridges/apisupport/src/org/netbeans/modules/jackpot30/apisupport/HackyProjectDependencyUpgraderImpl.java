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
