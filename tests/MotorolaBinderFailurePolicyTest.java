package com.moto.resolutionmaster;

import java.lang.reflect.InvocationTargetException;

public final class MotorolaBinderFailurePolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Throwable dead = new android.os.DeadObjectException("service restarted");
        check(MotorolaBinderFailurePolicy.isDeadBinderFailure(dead), "direct Binder death");
        check(MotorolaBinderFailurePolicy.isDeadBinderFailure(
            new InvocationTargetException(dead)), "wrapped reflective transport error");
        check(MotorolaBinderFailurePolicy.isDeadBinderFailure(
            new RuntimeException("outer", new InvocationTargetException(dead))),
            "nested transport error");
        check(!MotorolaBinderFailurePolicy.isDeadBinderFailure(
            new SecurityException("UID denied")), "security failure is not death");
        check(!MotorolaBinderFailurePolicy.isDeadBinderFailure(
            new InvocationTargetException(new IllegalStateException("no permission"))),
            "unrelated reflective failure");
        check(!MotorolaBinderFailurePolicy.isDeadBinderFailure(null),
            "null cannot indicate death");
        System.out.println("PASS: Binder transport death classification and negative cases");
    }
}
