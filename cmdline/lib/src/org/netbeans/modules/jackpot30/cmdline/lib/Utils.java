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
package org.netbeans.modules.jackpot30.cmdline.lib;

import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.ToolProvider;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;
import org.netbeans.spi.java.classpath.PathResourceImplementation;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.BaseUtilities;

/**
 *
 * @author lahvac
 */
public class Utils {

    public static Map<String, String> computeId2DisplayName(Iterable<? extends HintDescription> descs) {
        final Map<String, String> id2DisplayName = new HashMap<>();

        for (HintDescription hd : descs) {
            if (hd.getMetadata() != null) {
                id2DisplayName.put(hd.getMetadata().id, hd.getMetadata().displayName);
            }
        }

        return id2DisplayName;
    }

    public static String categoryName(String id, Map<String, String> id2DisplayName) {
        if (id != null && id.startsWith("text/x-java:")) {
            id = id.substring("text/x-java:".length());
        }

        String idDisplayName = id2DisplayName.get(id);

        if (idDisplayName == null) {
            idDisplayName = "unknown";
        }

        for (Entry<String, String> remap : toIdRemap.entrySet()) {
            idDisplayName = idDisplayName.replace(remap.getKey(), remap.getValue());
        }

        idDisplayName = idDisplayName.replaceAll("[^A-Za-z0-9]", "_").replaceAll("_+", "_");

        idDisplayName = "[" + idDisplayName + "] ";

        return idDisplayName;
    }

    private static final Map<String, String> toIdRemap = new HashMap<String, String>() {{
        put("==", "equals");
        put("!=", "not_equals");
    }};

    //copied from BootClassPathUtil:
    public static ClassPath createDefaultBootClassPath() {
        String cp = System.getProperty("sun.boot.class.path");
        if (cp != null) {
            List<URL> urls = new ArrayList<>();
            String[] paths = cp.split(Pattern.quote(System.getProperty("path.separator")));
            for (String path : paths) {
                File f = new File(path);

                if (!f.canRead())
                    continue;

                FileObject fo = FileUtil.toFileObject(f);
                if (FileUtil.isArchiveFile(fo)) {
                    fo = FileUtil.getArchiveRoot(fo);
                }
                if (fo != null) {
                    urls.add(fo.toURL());
                }
            }
            return ClassPathSupport.createClassPath((URL[])urls.toArray(new URL[0]));
        } else {
            try {
                Class.forName("org.netbeans.ProxyURLStreamHandlerFactory").getMethod("register")
                                                                          .invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException |
                     SecurityException | IllegalAccessException |
                    IllegalArgumentException | InvocationTargetException ex) {
                throw new IllegalStateException(ex);
            }
            final List<PathResourceImplementation> modules = new ArrayList<>();
            final File installDir = new File(System.getProperty("java.home"));
            final URI imageURI = getImageURI(installDir);
            try {
                final FileObject jrtRoot = URLMapper.findFileObject(imageURI.toURL());
                final FileObject root = getModulesRoot(jrtRoot);
                for (FileObject module : root.getChildren()) {
                    modules.add(ClassPathSupport.createResource(module.toURL()));
                }
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
            if (modules.isEmpty()) {
                throw new IllegalStateException("No modules!");
            }
            return ClassPathSupport.createClassPath(modules);
        }
    }

    private static final String PROTOCOL = "nbjrt"; //NOI18N

    private static URI getImageURI(@NonNull final File jdkHome) {
        try {
            return new URI(String.format(
                "%s:%s!/%s",  //NOI18N
                PROTOCOL,
                BaseUtilities.toURI(jdkHome).toString(),
                ""));
        } catch (URISyntaxException e) {
            throw new IllegalStateException();
        }
    }

    @NonNull
    private static FileObject getModulesRoot(@NonNull final FileObject jrtRoot) {
        final FileObject modules = jrtRoot.getFileObject("modules");    //NOI18N
        //jimage v1 - modules are located in the root
        //jimage v2 - modules are located in "modules" folder
        return modules == null ?
            jrtRoot :
            modules;
    }

    public static void addExports() {
        class CurrentClassLoaderFM extends ForwardingJavaFileManager<JavaFileManager> {
            public CurrentClassLoaderFM(JavaFileManager delegate) {
                super(delegate);
            }
            @Override
            public ClassLoader getClassLoader(Location location) {
                return Utils.class.getClassLoader();
            }
        }
        JavaCompiler compilerTool = ToolProvider.getSystemJavaCompiler();
        try (CurrentClassLoaderFM fm = new CurrentClassLoaderFM(compilerTool.getStandardFileManager(d -> {}, null, null))) {
            PrintWriter nullWriter = new PrintWriter(new StringWriter());
            //using JavacTask.analyze instead of CompilationTask.call to avoid closing the current ClassLoader:
            ((JavacTask) compilerTool.getTask(nullWriter, fm, d -> {}, Arrays.asList("-proc:none", "-XDaccessInternalAPI"), Arrays.asList("java.lang.Object"), null)).analyze();
        } catch (IOException ex) {
            //ignore...
            ex.printStackTrace();
        }
    }
}
