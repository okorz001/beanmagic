package org.korz.beanmagic;

import java.util.Objects;

class TypeConverterKey {
    private final Class<?> in;
    private final Class<?> out;

    public TypeConverterKey(Class<?> in, Class<?> out) {
        this.in = in;
        this.out = out;
    }

    @Override // Object
    public String toString() {
        return String.format("(%s, %s)", in.getName(), out.getName());
    }

    @Override // Object
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof TypeConverterKey) {
            TypeConverterKey other = (TypeConverterKey) o;
            return in == other.in && out == other.out;
        }
        return false;
    }

    @Override // Object
    public int hashCode() {
        return Objects.hash(in, out);
    }
}
