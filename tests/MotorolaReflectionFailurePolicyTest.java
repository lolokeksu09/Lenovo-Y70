package com.moto.resolutionmaster;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;

public final class MotorolaReflectionFailurePolicyTest {
    private static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ok(MotorolaReflectionFailurePolicy.isRestrictedAccess(
            new IllegalAccessException("denied")), "classic reflection failure");
        ok(MotorolaReflectionFailurePolicy.isRestrictedAccess(
            new InaccessibleObjectException("access denied")), "Java 9 exception by name");
        ok(!MotorolaReflectionFailurePolicy.isRestrictedAccess(
            new SecurityException("permission")), "permission denial is distinct");
        ok(!MotorolaReflectionFailurePolicy.isRestrictedAccess(
            new InvocationTargetException(new SecurityException())),
            "wrapped unrelated failure should remain distinct");
        ok(!MotorolaReflectionFailurePolicy.isRestrictedAccess(null),
            "null should not crash");
        System.out.println("PASS: legacy Android reflection classification");
    }
}
