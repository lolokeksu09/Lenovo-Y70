package com.moto.resolutionmaster;

import java.lang.reflect.InvocationTargetException;

/** Classifies binder transport death only; permission failures must never trigger retries. */
public final class MotorolaBinderFailurePolicy {
    private MotorolaBinderFailurePolicy() {}

    public static boolean isDeadBinderFailure(Throwable error) {
        Throwable cause = error;
        for (int i = 0; cause != null && i < 8; i++) {
            if ("android.os.DeadObjectException".equals(cause.getClass().getName())) {
                return true;
            }
            Throwable next = cause instanceof InvocationTargetException
                ? ((InvocationTargetException) cause).getTargetException()
                : cause.getCause();
            if (next == cause) break;
            cause = next;
        }
        return false;
    }
}
