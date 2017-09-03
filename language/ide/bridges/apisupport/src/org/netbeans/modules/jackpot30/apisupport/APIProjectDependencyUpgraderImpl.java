/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU General
 * Public License Version 2 only ("GPL") or the Common Development and
 * Distribution License("CDDL") (collectively, the "License"). You may not use
 * this file except in compliance with the License. You can obtain a copy of the
 * License at http://www.netbeans.org/cddl-gplv2.html or
 * nbbuild/licenses/CDDL-GPL-2-CP. See the License for the specific language
 * governing permissions and limitations under the License. When distributing
 * the software, include this License Header Notice in each file and include the
 * License file at nbbuild/licenses/CDDL-GPL-2-CP. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided by Oracle
 * in the GPL Version 2 section of the License file that accompanied this code.
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL or only
 * the GPL Version 2, indicate your decision by adding "[Contributor] elects to
 * include this software in this distribution under the [CDDL or GPL Version 2]
 * license." If you do not indicate a single choice of license, a recipient has
 * the option to distribute your version of this file under either the CDDL, the
 * GPL Version 2 or to extend the choice of license to its licensees as provided
 * above. However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is made
 * subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
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
