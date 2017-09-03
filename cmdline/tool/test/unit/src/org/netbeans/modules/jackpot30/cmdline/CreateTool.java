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
package org.netbeans.modules.jackpot30.cmdline;

import java.util.regex.Pattern;
import org.netbeans.modules.jackpot30.cmdline.lib.CreateStandaloneJar;
import org.netbeans.modules.jackpot30.cmdline.lib.CreateStandaloneJar.Info;
import org.netbeans.modules.java.hints.declarative.PatternConvertorImpl;
import org.netbeans.modules.java.hints.declarative.test.api.DeclarativeHintsTestBase;
import org.netbeans.modules.java.hints.jackpot.spi.PatternConvertor;
import org.netbeans.modules.java.j2seproject.J2SEProject;
import org.netbeans.modules.java.platform.DefaultJavaPlatformProvider;
import org.netbeans.modules.project.ui.OpenProjectsTrampolineImpl;

/**
 *
 * @author lahvac
 */
public class CreateTool extends CreateStandaloneJar {

    public CreateTool(String name) {
        super(name, "jackpot");
    }

    @Override
    protected Info computeInfo() {
        return new Info().addAdditionalRoots(Main.class.getName(), DeclarativeHintsTestBase.class.getName(), OpenProjectsTrampolineImpl.class.getName(), J2SEProject.class.getName(), DefaultJavaPlatformProvider.class.getName(), PatternConvertorImpl.class.getName())
                         .addAdditionalResources("org/netbeans/modules/java/hints/resources/Bundle.properties", "org/netbeans/modules/java/hints/declarative/resources/Bundle.properties")
                         .addAdditionalLayers("org/netbeans/modules/java/hints/resources/layer.xml", "org/netbeans/modules/java/hints/declarative/resources/layer.xml")
                         .addMetaInfRegistrations(new MetaInfRegistration(org.netbeans.modules.project.uiapi.OpenProjectsTrampoline.class, OpenProjectsTrampolineImpl.class))
                         .addMetaInfRegistrationToCopy(PatternConvertor.class.getName())
                         .addExcludePattern(Pattern.compile("junit\\.framework\\..*"))
                         .setEscapeJavaxLang();
    }

}
