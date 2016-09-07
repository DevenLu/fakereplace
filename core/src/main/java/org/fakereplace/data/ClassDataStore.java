/*
 * Copyright 2012, Stuart Douglas, and individual contributors as indicated
 * by the @authors tag.
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

package org.fakereplace.data;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.fakereplace.classloading.ClassIdentifier;
import org.fakereplace.core.BuiltinClassData;
import org.fakereplace.com.google.common.collect.MapMaker;
import org.fakereplace.manip.util.MapFunction;
import org.fakereplace.reflection.FieldAccessor;

public class ClassDataStore {

    private static final ClassDataStore INSTANCE = new ClassDataStore();

    private final Map<String, Class<?>> proxyNameToReplacedClass = new ConcurrentHashMap<String, Class<?>>();
    private final Map<String, FieldAccessor> proxyNameToFieldAccessor = new ConcurrentHashMap<String, FieldAccessor>();
    private final Map<ClassLoader, ConcurrentMap<String, ClassData>> classData = new MapMaker().weakKeys().makeComputingMap(new MapFunction<ClassLoader, String, ClassData>(false));
    private final Map<ClassLoader, ConcurrentMap<String, BaseClassData>> baseClassData = new MapMaker().weakKeys().makeComputingMap(new MapFunction<ClassLoader, String, BaseClassData>(false));
    private final Map<String, MethodData> proxyNameToMethodData = new ConcurrentHashMap<String, MethodData>();
    private final Set<ClassIdentifier> replacedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * takes the place of the null key on ConcurrentHashMap
     */
    private static final ClassLoader NULL_LOADER = new ClassLoader() {
    };

    private ClassDataStore() {

    }

    public void markClassReplaced(Class<?> clazz) {
        replacedClasses.add(new ClassIdentifier(clazz.getName(), clazz.getClassLoader()));
    }

    public boolean isClassReplaced(Class<?> clazz) {
        return replacedClasses.contains(new ClassIdentifier(clazz.getName(), clazz.getClassLoader()));
    }

    public boolean isClassReplaced(String name, ClassLoader loader) {
        return replacedClasses.contains(new ClassIdentifier(name, loader));
    }

    public void saveClassData(ClassLoader loader, String className, ClassDataBuilder data) {
        className = className.replace('/', '.');
        if (loader == null) {
            loader = NULL_LOADER;
        }
        Map<String, ClassData> map = classData.get(loader);
        map.put(className, data.buildClassData());
    }

    public void saveClassData(ClassLoader loader, String className, BaseClassData data) {
        className = className.replace('/', '.');
        if (loader == null) {
            loader = NULL_LOADER;
        }
        Map<String, BaseClassData> map = baseClassData.get(loader);
        map.put(className, data);
    }

    public ClassData getModifiedClassData(ClassLoader loader, String className) {
        className = className.replace('/', '.');
        if (loader == null) {
            loader = NULL_LOADER;
        }
        Map<String, ClassData> map = classData.get(loader);
        ClassData cd = map.get(className);
        if (cd == null) {
            BaseClassData dd = getBaseClassData(loader, className);
            if (dd == null) {
                return null;
            }
            ClassDataBuilder builder = new ClassDataBuilder(dd);
            ClassData d = builder.buildClassData();
            map.put(className, d);
            return d;
        }

        return cd;
    }

    public BaseClassData getBaseClassData(ClassLoader loader, String className) {
        className = className.replace('/', '.');
        if (loader == null) {
            loader = NULL_LOADER;
        }
        Map<String, BaseClassData> map = baseClassData.get(loader);
        if (!map.containsKey(className)) {
            // if this is a class that is not being instrumented it is safe to
            // load the class and get the data
            if (BuiltinClassData.skipInstrumentation(className)) {
                try {
                    if (loader != NULL_LOADER) {
                        Class<?> cls = loader.loadClass(className);
                        saveClassData(loader, className, new BaseClassData(cls));
                    } else {
                        Class<?> cls = Class.forName(className);
                        saveClassData(loader, className, new BaseClassData(cls));
                    }
                } catch (ClassNotFoundException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        BaseClassData cd = map.get(className);
        return cd;
    }

    public Class<?> getRealClassFromProxyName(String proxyName) {
        return proxyNameToReplacedClass.get(proxyName);
    }

    public void registerProxyName(Class<?> c, String proxyName) {
        proxyNameToReplacedClass.put(proxyName, c);
    }

    public void registerFieldAccessor(String proxyName, FieldAccessor accessor) {
        proxyNameToFieldAccessor.put(proxyName, accessor);
    }

    public void registerReplacedMethod(String proxyName, MethodData methodData) {
        proxyNameToMethodData.put(proxyName, methodData);
    }

    public MethodData getMethodInformation(String proxyName) {
        return proxyNameToMethodData.get(proxyName);
    }

    public FieldAccessor getFieldAccessor(String proxyName) {
        return proxyNameToFieldAccessor.get(proxyName);
    }

    public static ClassDataStore instance() {
        return INSTANCE;
    }

    /**
     * THIS IS A TEMPORARY METHOD
     *
     * It should only exist during the transition phase, while all rewriting is being moved into the transformers.
     *
     * Once all processing is in the transformer chain then it should be removed, and a class data builder passed through
     * all the transformers instead
     *
     * TODO: remove this method
     * @param loader
     * @param name
     */
    public void modifyCurrentData(ClassLoader loader, String name, Consumer<ClassDataBuilder> consumer) {
        ClassData current = getModifiedClassData(loader, name);
        ClassDataBuilder builder = new ClassDataBuilder(current, getBaseClassData(loader, name));
        consumer.accept(builder);
        ClassDataStore.instance().saveClassData(loader, name, builder);
    }
}
