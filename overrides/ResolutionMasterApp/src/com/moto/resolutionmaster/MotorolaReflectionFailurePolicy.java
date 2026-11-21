package com.moto.resolutionmaster;

/**
 * Reflection errors on Motorola's Android platform.
 *
 * java.lang.reflect.InaccessibleObjectException is a Java 9+ class and can
 * be absent from Android bootclasspath even when the build JDK has it.
 * Never reference the type directly in Android bytecode.
 */
public final class MotorolaReflectionFailurePolicy {
    private MotorolaReflectionFailurePolicy() {}

    public static boolean isRestrictedAccess(Throwable cause) {
        if (cause == null) return false;
        if (cause instanceof IllegalAccessException) return true;
        return "java.lang.reflect.InaccessibleObjectException".equals(
            cause.getClass().getName());
    }
}
