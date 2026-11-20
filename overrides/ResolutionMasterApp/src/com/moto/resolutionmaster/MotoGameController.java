package com.moto.resolutionmaster;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * First-party Motorola API calls, with no raw binder transaction codes,
 * package/UID spoofing, root filesystem edits or fabricated success.
 *
 * Reflection only avoids linking OEM classes on other Android builds.
 * Android's genuine caller UID and service-side permission checks still apply.
 *
 * A successful void invocation means "request sent"; the Activity MUST
 * verify its observable effect using a fresh moto_extend_service readback.
 */
public final class MotoGameController {
    private static final String MANAGER =
        "motorola.core_services.misc.MotoExtendManager";
    public static final String DPS_PERMISSION =
        "com.motorola.permission.SET_DIRECT_POWER_SUPPLY_STATUS";

    private MotoGameController() {}

    public static final class Result {
        public final boolean sent;
        public final String reason;
        private Result(boolean sent, String reason) {
            this.sent = sent;
            this.reason = reason;
        }
        public static Result accepted() {
            return new Result(true, "Запрос принят API; проверяем системный статус...");
        }
        public static Result refused(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * Hardware direct supply. On stock Motorola this permission is
     * signature|privileged and is NOT obtainable using ordinary Shizuku/ADB.
     */
    public static Result requestBypass(Context context, boolean enable) {
        if (context == null) return Result.refused("Нет Context");
        if (context.checkSelfPermission(DPS_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return Result.refused(
                "Android отклонил управление Bypass: требуется Motorola " +
                "signature|privileged. Shizuku не выдаёт это разрешение стороннему APK.");
        }
        try {
            Object manager = manager(context);
            Method supported = manager.getClass().getMethod("isDirectPowerSupplySupported");
            if (!Boolean.TRUE.equals(supported.invoke(manager))) {
                return Result.refused("Контроллер Motorola не поддерживает Direct Power Supply");
            }
            manager.getClass().getMethod("setDirectPowerSupplyStatus", int.class)
                .invoke(manager, enable ? 1 : 0);
            return Result.accepted();
        } catch (Throwable t) {
            return Result.refused(explain(t));
        }
    }

    /**
     * Request one GameMode touch-enhance setting for this caller's own token.
     * The service automatically records the actual opPackageName of our app.
     * GameTime may take precedence, and the driver may reject this request:
     * only a matching mGameModeStates entry can verify that the request stuck.
     */
    public static Result requestTouch(Context context, boolean enable, String gamePackage) {
        if (context == null) return Result.refused("Нет Context");
        if (gamePackage == null ||
                !gamePackage.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")) {
            return Result.refused("Не определена установленная игра для Touch Boost");
        }
        try {
            context.getPackageManager().getPackageInfo(gamePackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return Result.refused("Выбранная игра не установлена: " + gamePackage);
        }
        try {
            Object manager = manager(context);
            Method supported = manager.getClass().getMethod("isTouchEnhanceSupported");
            if (!Boolean.TRUE.equals(supported.invoke(manager))) {
                return Result.refused("Motorola не разрешает Touch Boost для этого приложения");
            }
            Bundle options = new Bundle();
            options.putInt("touch_enhance_enable", enable ? 1 : 0);
            options.putString("game_package_name", gamePackage);
            options.putInt("display", 0);
            // Motorola core fills game_mode_op_package_name itself from Context.
            manager.getClass().getMethod("setGameModeState", boolean.class, Bundle.class)
                .invoke(manager, true, options);
            return Result.accepted();
        } catch (Throwable t) {
            return Result.refused(explain(t));
        }
    }

    private static Object manager(Context context) throws Exception {
        Class<?> klass = Class.forName(MANAGER);
        Object instance = klass.getMethod("getInstance", Context.class)
            .invoke(null, context.getApplicationContext());
        if (instance == null) {
            throw new IllegalStateException("MotoExtendManager вернул null");
        }
        return instance;
    }

    private static String explain(Throwable t) {
        Throwable cause = t;
        for (int i = 0; i < 5 && cause instanceof InvocationTargetException; i++) {
            Throwable next = ((InvocationTargetException) cause).getTargetException();
            if (next == null || next == cause) break;
            cause = next;
        }
        if (cause instanceof SecurityException) {
            return "Motorola отказала в доступе (SecurityException): " +
                safe(cause.getMessage());
        }
        if (cause instanceof ClassNotFoundException || cause instanceof NoClassDefFoundError) {
            return "Системная библиотека moto-core_services недоступна этому APK";
        }
        if (cause instanceof NoSuchMethodException) {
            return "Версия MotoExtendManager не содержит нужного метода";
        }
        return "Вызов Motorola API не выполнен: " +
            cause.getClass().getSimpleName() + " " + safe(cause.getMessage());
    }

    private static String safe(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String text = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= 120 ? text : text.substring(0, 120);
    }
}
