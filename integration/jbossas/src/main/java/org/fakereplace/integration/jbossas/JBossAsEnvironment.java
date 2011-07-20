/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.fakereplace.integration.jbossas;

import org.fakereplace.boot.Environment;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class JBossAsEnvironment implements Environment {

    @Override
    public boolean isClassReplaceable(final String className, final ClassLoader loader) {
        if (loader instanceof ModuleClassLoader) {
            if (((ModuleClassLoader) loader).getModule().getIdentifier().toString().startsWith("deployment.")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDumpDirectory() {
        return null;
    }

    private final Map<ModuleIdentifier, ModuleClassLoader> loadersByModuleIdentifier = new HashMap<ModuleIdentifier, ModuleClassLoader>();


    private final Map<ModuleClassLoader, Map<String, Long>> timestamps = new ConcurrentHashMap<ModuleClassLoader, Map<String, Long>>();

    public void recordTimestamp(String className, ClassLoader loader) {
        if (!(loader instanceof ModuleClassLoader)) {
            return;
        }
        Map<String, Long> stamps = null;
        final ModuleClassLoader moduleClassLoader = (ModuleClassLoader) loader;
        final ModuleIdentifier moduleIdentifier = moduleClassLoader.getModule().getIdentifier();
        if (loadersByModuleIdentifier.containsKey(moduleIdentifier)) {
            final ModuleClassLoader oldLoader = loadersByModuleIdentifier.get(moduleIdentifier);
            if (oldLoader != moduleClassLoader) {
                loadersByModuleIdentifier.put(moduleIdentifier, moduleClassLoader);
                timestamps.put(moduleClassLoader,  stamps = new ConcurrentHashMap<String, Long>());
            } else {
                stamps = timestamps.get(moduleClassLoader);
            }
        } else {
            loadersByModuleIdentifier.put(moduleIdentifier, moduleClassLoader);
            timestamps.put(moduleClassLoader, stamps = new ConcurrentHashMap<String, Long>());
        }

        final URL file = loader.getResource(className.replace(".", "/") + ".class");
        className = className.replace("/", ".");
        if (file != null) {
            URLConnection connection = null;
            try {
                connection = file.openConnection();
                stamps.put(className, connection.getLastModified());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public Set<Class> getUpdatedClasses(final String deploymentName, Map<String, Long> updatedClasses) {

        final ModuleIdentifier moduleId = getModuleIdentifier(deploymentName);
        final ModuleClassLoader loader = loadersByModuleIdentifier.get(moduleId);
         if (loader == null) {
            return Collections.emptySet();
        }
        final Map<String, Long> timestamps = this.timestamps.get(loader);

        final Set<Class> ret = new HashSet<Class>();
        for (Map.Entry<String, Long> entry : updatedClasses.entrySet()) {
            if (timestamps.containsKey(entry.getKey()) && timestamps.get(entry.getKey()) < entry.getValue()) {
                try {
                    ret.add(loader.loadClass(entry.getKey()));
                    timestamps.put(entry.getKey(), entry.getValue());
                } catch (ClassNotFoundException e) {
                    System.err.println("Could not load class " + entry);
                }
            }
        }
        return ret;
    }

    private ModuleIdentifier getModuleIdentifier(final String deploymentArchive) {
        return ModuleIdentifier.create("deployment." + deploymentArchive);
    }

}
