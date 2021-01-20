/*
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
package org.netbeans.modules.java.debugjavac;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.platform.JavaPlatformManager;
import org.netbeans.api.java.platform.Specification;
import org.netbeans.modules.java.debugjavac.Decompiler.Input;
import org.netbeans.modules.java.debugjavac.Decompiler.Result;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public interface CompilerDescription {
    public String getName();
    public boolean isValid();
    public Result decompile(DecompilerDescription decompiler, Input input);

    public static class Factory {
        private static Collection<? extends CompilerDescription> descriptions;
        public static synchronized Collection<? extends CompilerDescription> descriptions() {
            if (descriptions != null) return descriptions;

            File moduleJar = InstalledFileLocator.getDefault().locate("modules/org-netbeans-modules-java-debugjavac.jar", null, false);

            List<CompilerDescription> result = new ArrayList<>();

            for (JavaPlatform platform : JavaPlatformManager.getDefault().getInstalledPlatforms()) {
                if (!"j2se".equals(platform.getSpecification().getName())) continue;

                for (FileObject installDir : platform.getInstallFolders()) {
                    FileObject toolsJar = installDir.getFileObject("lib/tools.jar");
                    FileObject jrtfs = installDir.getFileObject("lib/jrt-fs.jar");

                    if (toolsJar != null || jrtfs != null) {
                        result.add(new ExecCompilerDescription(platform, moduleJar, toolsJar != null ? FileUtil.toFile(toolsJar) : null));
                    }
                }
            }

            return descriptions = Collections.unmodifiableList(result);
        }

        static {
            JavaPlatformManager.getDefault().addPropertyChangeListener(new PropertyChangeListener() {
                @Override public void propertyChange(PropertyChangeEvent evt) {
                    synchronized (CompilerDescription.class) {
                        descriptions = null;
                        //TODO: should refresh the existing combos
                    }
                }
            });
        }
        
        private static class LoaderBased implements CompilerDescription {
            public final String displayName;
            public final URL[] jars;

            private LoaderBased(String displayName, URL[] jars) {
                this.displayName = displayName;
                this.jars = jars;
            }

            @Override
            public String getName() {
                return displayName;
            }

            private final AtomicReference<Boolean> valid = new AtomicReference<>();

            public boolean isValid() {
                Boolean val = valid.get();

                if (val == null) {
                    valid.set(val = isValidImpl());
                }

                return val;
            }

            private boolean isValidImpl() {
                ClassLoader loader = getClassLoader();

                if (loader == null) return false;

                try {
                    Class.forName("javax.tools.ToolProvider", true, loader);
                    return true;
                } catch (Throwable ex) {
                    return false;
                }
            }

            private ClassLoader classLoader;

            private synchronized  ClassLoader getClassLoader() {
                if (classLoader == null) {
                    try {
                        List<URL> urls = new ArrayList<>();

                        urls.addAll(Arrays.asList(jars));
                        urls.add(InstalledFileLocator.getDefault().locate("modules/ext/decompile.jar", null, false).toURI().toURL());

                        classLoader = new URLClassLoader(urls.toArray(new URL[0]), DecompiledTab.class.getClassLoader());
                    } catch (MalformedURLException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }

                return classLoader;
            }

            @Override
            public Result decompile(DecompilerDescription decompiler, Input input) {
                ClassLoader loader = getClassLoader();

                if (loader == null) return new Result("Internal error - cannot find ClassLoader.", null, null);

                return decompiler.createDecompiler(loader).decompile(input);
            }

        }

        private static final class ExecCompilerDescription implements CompilerDescription {

            private static final SpecificationVersion HAS_ADD_EXPORTS = new SpecificationVersion("9");

            private final JavaPlatform platform;
            private final File moduleJar;
            private final File toolsJar;

            public ExecCompilerDescription(JavaPlatform platform, File moduleJar, File toolsJar) {
                this.platform = platform;
                this.moduleJar = moduleJar;
                this.toolsJar = toolsJar;
            }

            @Override
            public String getName() {
                return platform.getDisplayName();
            }

            @Override
            public boolean isValid() {
                return true; //TODO
            }

            @Override
            public Result decompile(DecompilerDescription decompiler, Input input) {
                try {
                    List<String> args = new ArrayList<>();
                    args.add(FileUtil.toFile(platform.findTool("java")).getAbsolutePath());
                    if (toolsJar != null) {
                        args.add("-Xbootclasspath/p:" + toolsJar.getAbsolutePath());
                    }
                    if (platform.getSpecification().getVersion().compareTo(HAS_ADD_EXPORTS) >= 0) {
                        args.add("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
                        args.add("--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");
                        args.add("--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED");
                        args.add("--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED");
                        args.add("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
                        args.add("--add-exports=jdk.jdeps/com.sun.tools.classfile=ALL-UNNAMED");
                        args.add("--add-exports=jdk.jdeps/com.sun.tools.javap=ALL-UNNAMED");
                    }
                    args.add("-classpath");
                    args.add(moduleJar.getAbsolutePath());
                    args.add("org.netbeans.modules.java.debugjavac.impl.Main");
                    args.add(decompiler.id);
                    Process process = Runtime.getRuntime().exec(args.toArray(new String[0]));
                    try (XMLEncoder enc = new XMLEncoder(process.getOutputStream())) {
                        enc.writeObject(input);
                    }
                    try (XMLDecoder decl = new XMLDecoder(process.getInputStream())) {
                        return (Result) decl.readObject();
                    }
                } catch (IOException ex) {
                    StringWriter exception = new StringWriter();
                    try (PrintWriter exceptionPW = new PrintWriter(exception)) {
                        ex.printStackTrace(exceptionPW);
                    }
                    return new Result(exception.toString(), null, null);
                }
                
            }

        }

    }

}
