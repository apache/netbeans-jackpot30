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

import java.io.IOException;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.apisupport.project.spi.NbModuleProvider;
import org.netbeans.modules.apisupport.project.spi.NbModuleProvider.ModuleDependency;
import org.netbeans.modules.jackpot30.apisupport.Utilities.ParsedDependency;
import org.netbeans.modules.java.hints.spiimpl.ipi.upgrade.ProjectDependencyUpgrader;
import org.openide.filesystems.FileObject;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class APIProjectDependencyUpgraderImpl extends ProjectDependencyUpgrader {

    public boolean ensureDependency(Project currentProject, FileObject depFO, SpecificationVersion spec, final boolean canShowUI) {
        NbModuleProvider currentNbModule = currentProject.getLookup().lookup(NbModuleProvider.class);

        if (currentNbModule == null) {
            return false;
        }

        Project referedProject = FileOwnerQuery.getOwner(depFO);

        if (referedProject == null) {
            return false;
        }

        NbModuleProvider referedNbProject = referedProject.getLookup().lookup(NbModuleProvider.class);

        if (referedNbProject == null) {
            return false;
        }

        return ensureDependency(currentProject, referedNbProject.getCodeNameBase(), ProjectUtils.getInformation(referedProject).getDisplayName(), null, spec, canShowUI);
    }

    private boolean ensureDependency(Project currentProject, String cnb, String displayName, String releaseVersion, SpecificationVersion spec, final boolean canShowUI) {
        NbModuleProvider currentNbModule = currentProject.getLookup().lookup(NbModuleProvider.class);

        if (currentNbModule == null) {
            return false;
        }

        try {
            if (!currentNbModule.hasDependency(cnb)) {
                if (showDependencyUpgradeDialog(currentProject, displayName, null, spec, false, canShowUI)) {
                    currentNbModule.addDependencies(new ModuleDependency[] {new ModuleDependency(cnb, releaseVersion, spec, true)});
                    ProjectManager.getDefault().saveProject(currentProject);
                }

                return true;
            }

            SpecificationVersion currentDep = currentNbModule.getDependencyVersion(cnb);

            if (currentDep == null) {
                //impl.dep?
                return false;
            }

            if (spec != null && (currentDep == null || currentDep.compareTo(spec) < 0)) {
                if (showDependencyUpgradeDialog(currentProject, displayName, currentDep, spec, false, canShowUI)) {
                    currentNbModule.addDependencies(new ModuleDependency[] {new ModuleDependency(cnb, releaseVersion, spec, true)});
                    ProjectManager.getDefault().saveProject(currentProject);
                }

                return true;
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        return false;
    }

    @Override
    public boolean ensureDependency(Project p, String specification, boolean canShowUI) {
        ParsedDependency dep = Utilities.parseDependency(specification);

        return ensureDependency(p, dep.cnb, /*TODO: may null and that could put display name their*/specification, dep.releaseVersion, dep.specVersion, canShowUI);
    }

}
