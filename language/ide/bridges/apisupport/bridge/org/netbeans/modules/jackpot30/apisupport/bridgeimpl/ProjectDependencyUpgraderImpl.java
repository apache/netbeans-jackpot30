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
