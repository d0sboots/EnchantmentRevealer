/* Copyright 2016 David Walker

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package io.github.d0sboots.enchantmentrevealer;

import java.lang.reflect.Field;

/**
 * Generic wrapper for Field that is based on types instead of names, and also makes the Field
 * accessible.
 *
 * @param <T> Type of the field itself
 * @param <C> Type of the class the field is a member of
 */
public class FieldHelper<T, C> {
    private final Field field;

    /**
     * Creates a new FieldHelper for the given field and class.
     * The field's type must be unique within the class (and all superclasses).
     */
    public static <T, C> FieldHelper<T, C> from(Class<T> fieldType, Class<C> target) {
        Field[] fields = target.getDeclaredFields();
        int index = getFieldOffset(fields, fieldType, target);
        Field found = fields[index];
        found.setAccessible(true);
        return new FieldHelper<T, C>(found);
    }

    /**
     * Creates a FieldHelper where the target field is specified by an offset from another field.
     * Use this if the target has a primitive type, or a type that happens multiple times.
     *
     * @param fieldType Class type of the field itself
     * @param target Class type of the class the field is a member of
     * @param referenceField Class type of a field to use as a reference point
     * @param offset Where the target is relative to the reference (positive is declared later)
     */
    public static <T, C> FieldHelper<T, C> offsetFrom(
            Class<T> fieldType, Class<C> target, Class<?> referenceField, int offset) {
        Field[] fields = target.getDeclaredFields();
        int index = getFieldOffset(fields, referenceField, target);
        Field found = fields[index + offset];
        // We don't bother checking for primitive types, because we'd have to map it to the
        // reference type and it's too annoying.
        if (!found.getType().equals(fieldType) && !found.getType().isPrimitive()) {
            throw new RuntimeException(
                    "Bad offset of " + offset + " specified: Expected to find a field with type " +
                    fieldType.getCanonicalName() + ", but instead found " +
                    found.getType().getCanonicalName());
        }
        found.setAccessible(true);
        return new FieldHelper<T, C>(found);
    }

    private static int getFieldOffset(Field[] fields, Class<?> fieldType, Class<?> target) {
        int index = -1;
        for (int i = 0; i < fields.length; ++i) {
            Field f = fields[i];
            if (f.getType().equals(fieldType)) {
                if (index != -1) {
                    throw new RuntimeException(
                            "There are multiple fields of type " + fieldType.getName()
                            + " in class " + target.getName() + ": " + fields[index].getName()
                            + " at index " + index + " and " + fields[i].getName()
                            + " at index " + i);
                }
                index = i;
            }
        }
        if (index != -1) {
            return index;
        }
        throw new RuntimeException("Couldn't find field with type " + fieldType.getName()
                + " in class " + target.getName());
    }

    private FieldHelper(Field field) {
        this.field = field;
    }

    @SuppressWarnings("unchecked")
    public T get(C target) {
        try {
            return (T) field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void set(C target, T value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
