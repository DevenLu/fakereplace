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

package org.fakereplace.runtime;

import java.util.Map;

import org.fakereplace.com.google.common.base.Function;
import org.fakereplace.com.google.common.collect.MapMaker;
import org.fakereplace.util.NullSafeConcurrentHashMap;

/**
 * This class holds field data for added fields. It maintains a weakly
 * referenced computing map of instance -> field value.
 *
 * @author Stuart Douglas
 */
public class FieldDataStore {
    private static final Map<Object, Map<Integer, Object>> fieldData = new MapMaker().weakKeys().makeComputingMap(new Function<Object, Map<Integer, Object>>() {
        public Map<Integer, Object> apply(Object from) {
            return new NullSafeConcurrentHashMap<Integer, Object>();
        }
    });

    public static Object getValue(Object instance, int field) {
        Object ret = fieldData.get(instance).get(field);
        if(ret != null) {
            return ret;
        } else {
            String type = FieldReferenceDataStore.instance().getFieldDescriptor(field);
            if(type.length() == 1) {
                return 0;
            } else {
                return null;
            }
        }
    }

    public static void setValue(Object instance, Object value, int field) {
        fieldData.get(instance).put(field, value);
    }
}
