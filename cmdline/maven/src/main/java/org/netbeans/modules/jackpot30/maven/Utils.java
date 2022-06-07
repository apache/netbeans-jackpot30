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
package org.netbeans.modules.jackpot30.maven;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 *
 * @author lahvac
 */
public class Utils {

    public static Xpp3Dom getPluginConfiguration(MavenProject project, String groupId, String artifactId) {
        for (Object o : project.getBuild().getPlugins()) {
            if (!(o instanceof Plugin)) continue;

            Plugin p = (Plugin) o;

            if (   groupId.equals(p.getGroupId())
                && artifactId.equals(p.getArtifactId())) {
                if (p.getConfiguration() instanceof Xpp3Dom) {
                    return (Xpp3Dom) p.getConfiguration();
                }
                break;
            }
        }

        return null;
    }

    public static String getJackpotConfigurationFile(MavenProject project) {
        Xpp3Dom configuration = getJackpotPluginConfiguration(project);
        
        if (configuration != null) {
            Xpp3Dom configurationFileElement = configuration.getChild("configurationFile");

            if (configurationFileElement != null) {
                return configurationFileElement.getValue();
            }
        }

        return null;
    }

    public static boolean getJackpotFailOnWarnings(MavenProject project) {
        Xpp3Dom configuration = getJackpotPluginConfiguration(project);

        if (configuration != null) {
            Xpp3Dom configurationFileElement = configuration.getChild("failOnWarnings");

            if (configurationFileElement != null) {
                return "true".equalsIgnoreCase(configurationFileElement.getValue());
            }
        }

        return true;
    }

    private static Xpp3Dom getJackpotPluginConfiguration(MavenProject project) {
        return getPluginConfiguration(project, "org.apache.netbeans.modules.jackpot30", "jackpot30-maven-plugin");
    }
}
