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
package org.netbeans.modules.jackpot30.cmdline.processor;

import java.util.regex.Pattern;
import javax.annotation.processing.Processor;
//import org.netbeans.modules.jackpot30.cmdline.Main.BCPFallBack;
//import org.netbeans.modules.jackpot30.cmdline.Main.SourceLevelQueryImpl;
import org.netbeans.modules.jackpot30.cmdline.lib.CreateStandaloneJar;
import org.netbeans.modules.jackpot30.cmdline.lib.CreateStandaloneJar.Info;
import org.netbeans.modules.java.hints.declarative.PatternConvertorImpl;
import org.netbeans.modules.java.hints.declarative.test.api.DeclarativeHintsTestBase;
import org.netbeans.modules.java.hints.jackpot.spi.PatternConvertor;
import org.netbeans.modules.java.j2seproject.J2SEProject;
import org.netbeans.modules.java.platform.DefaultJavaPlatformProvider;
import org.netbeans.modules.project.ui.OpenProjectsTrampolineImpl;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation2;

/**
 *
 * @author lahvac
 */
public class CreateToolProcessor extends CreateStandaloneJar {

    public CreateToolProcessor(String name) {
        super(name, "jackpot");
    }

    @Override
    protected Info computeInfo() {
        return new Info().addAdditionalRoots(ProcessorImpl.class.getName(), DeclarativeHintsTestBase.class.getName(), OpenProjectsTrampolineImpl.class.getName(), J2SEProject.class.getName(), DefaultJavaPlatformProvider.class.getName(), PatternConvertorImpl.class.getName()/*, BCPFallBack.class.getName()*/, "org.slf4j.impl.StaticLoggerBinder")
                         .addAdditionalResources("org/netbeans/modules/java/hints/resources/Bundle.properties", "org/netbeans/modules/java/hints/declarative/resources/Bundle.properties", "org/netbeans/modules/jackpot30/cmdline/processor/cfg_hints.xml")
                         .addAdditionalLayers("org/netbeans/modules/java/hints/resources/layer.xml", "org/netbeans/modules/java/hints/declarative/resources/layer.xml")
                         .addMetaInfRegistrations(new MetaInfRegistration(org.netbeans.modules.project.uiapi.OpenProjectsTrampoline.class, OpenProjectsTrampolineImpl.class))
                         .addMetaInfRegistrations(new MetaInfRegistration(Processor.class, ProcessorImpl.class))
//                         .addMetaInfRegistrations(new MetaInfRegistration(ClassPathProvider.class.getName(), BCPFallBack.class.getName(), 9999))
//                         .addMetaInfRegistrations(new MetaInfRegistration(ClassPathProvider.class.getName(), Main.ClassPathProviderImpl.class.getName(), 100))
//                         .addMetaInfRegistrations(new MetaInfRegistration(SourceLevelQueryImplementation2.class.getName(), SourceLevelQueryImpl.class.getName(), 100))
                         .addMetaInfRegistrationToCopy(PatternConvertor.class.getName())
                         .addExcludePattern(Pattern.compile("junit\\.framework\\..*"))
                         //TODO: add javac excludes
                         ;
    }

}
