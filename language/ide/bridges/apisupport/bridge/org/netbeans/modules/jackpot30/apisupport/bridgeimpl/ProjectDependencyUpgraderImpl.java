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
package org.netbeans.modules.jackpot30.apisupport.bridgeimpl;

import java.io.IOException;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.apisupport.project.ModuleDependency;
import org.netbeans.modules.apisupport.project.NbModuleProject;
import org.netbeans.modules.apisupport.project.ProjectXMLManager;
import org.netbeans.modules.apisupport.project.ProjectXMLManager.CyclicDependencyException;
import org.netbeans.modules.apisupport.project.spi.NbModuleProvider;
import org.netbeans.modules.apisupport.project.universe.ModuleEntry;
import org.netbeans.modules.jackpot30.apisupport.Utilities;
import org.netbeans.modules.jackpot30.apisupport.Utilities.ParsedDependency;
import org.netbeans.modules.java.hints.spiimpl.ipi.upgrade.ProjectDependencyUpgrader;
import org.openide.filesystems.FileObject;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class ProjectDependencyUpgraderImpl extends ProjectDependencyUpgrader {

    public boolean ensureDependency(Project currentProject, FileObject depFO, SpecificationVersion spec, final boolean canShowUI) {
        NbModuleProvider currentNbModule = currentProject.getLookup().lookup(NbModuleProvider.class);

        if (currentNbModule == null) {
            return false;
        }

        Project referedProject = FileOwnerQuery.getOwner(depFO);

        if (referedProject == null) {
            return false;
        }

        NbModuleProject referedNbProject = referedProject.getLookup().lookup(NbModuleProject.class);

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
            NbModuleProject currentNbModuleProject = currentProject.getLookup().lookup(NbModuleProject.class);

            if (currentNbModuleProject == null) {
                return false;
            }

            ProjectXMLManager m = new ProjectXMLManager(currentNbModuleProject);
            ModuleDependency dep = m.getModuleDependency(cnb);

            if (dep == null) {
                if (showDependencyUpgradeDialog(currentProject, displayName, null, spec, false, canShowUI)) {
                    ModuleEntry me = currentNbModuleProject.getModuleList().getEntry(cnb);
                    ModuleDependency nue = new ModuleDependency(me,
                                                                releaseVersion != null ? releaseVersion : me.getReleaseVersion(),
                                                                spec != null ? spec.toString() : me.getSpecificationVersion(),
                                                                true,
                                                                false);

                    m.addDependency(nue);
                    ProjectManager.getDefault().saveProject(currentProject);
                }

                return true;
            }

            if (dep.getSpecificationVersion() == null) {
                //impl.dep?
                return false;
            }

            SpecificationVersion currentDep = new SpecificationVersion(dep.getSpecificationVersion());

            if (spec != null && (currentDep == null || currentDep.compareTo(spec) < 0)) {
                if (showDependencyUpgradeDialog(currentProject, displayName, new SpecificationVersion(dep.getSpecificationVersion()), spec, false, canShowUI)) {
                    ModuleDependency nue = new ModuleDependency(dep.getModuleEntry(),
                                                                releaseVersion != null ? releaseVersion : dep.getReleaseVersion(),
                                                                spec.toString(),
                                                                dep.hasCompileDependency(),
                                                                dep.hasImplementationDependency());

                    m.editDependency(dep, nue);
                    ProjectManager.getDefault().saveProject(currentProject);
                }

                return true;
            }
        } catch (CyclicDependencyException ex) {
            Exceptions.printStackTrace(ex);
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
