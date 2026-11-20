package com.moto.resolutionmaster;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

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
    private static final String CORE = "com.motorola.misc.MotoExtendManagerCore";
    private static final String SERVICE_INTERFACE = "com.motorola.misc.IMotoExtendService";
    private static final String SERVICE_NAME = "moto_extend_service";
    // Use a persistent Binder token for the lifetime of the app process.
    // This is a normal client token, NOT a privileged UID or raw transaction.
    private static final IBinder DPS_TOKEN = new Binder();
    private static final Object CORE_LOCK = new Object();
    private static volatile Object coreInstance;
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
            if (isCore(manager)) {
                // Core exposes the typed AIDL call with a client Binder token.
                manager.getClass().getMethod(
                        "setDirectPowerSupplyStatus", int.class, IBinder.class)
                    .invoke(manager, enable ? 1 : 0, DPS_TOKEN);
            } else {
                manager.getClass().getMethod("setDirectPowerSupplyStatus", int.class)
                    .invoke(manager, enable ? 1 : 0);
            }
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

    /**
     * On Lenovo Y70 with Motorola firmware, a normal application gets null
     * from Context.getSystemService("moto_extend_service") even though the
     * service is registered and accessible to dumpsys.
     *
     * MotoExtendManager.getInstance(Context) wraps that null manager, so
     * calling isTouchEnhanceSupported() used to fail with an NPE.
     *
     * Prefer the normal wrapper when correctly initialized. Otherwise obtain
     * the binder by its verified service name and construct the *official*
     * framework MotoExtendManagerCore(IMotoExtendService, Context). This does
     * not change identity or permissions; the service checks our own UID.
     */
    private static Object manager(Context context) throws Exception {
        try {
            Class<?> klass = Class.forName(MANAGER);
            Object instance = klass.getMethod("getInstance", Context.class)
                .invoke(null, context.getApplicationContext());
            if (instance != null) {
                Field coreField = klass.getDeclaredField("mMotoExtendManagerCore");
                coreField.setAccessible(true);
                if (coreField.get(instance) != null) return instance;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Missing or restricted wrapper; try official framework Core below.
        }
        return coreFromRegisteredService(context);
    }

    private static boolean isCore(Object manager) {
        return CORE.equals(manager.getClass().getName());
    }

    private static Object coreFromRegisteredService(Context context) throws Exception {
        Object cached = coreInstance;
        if (cached != null) return cached;
        synchronized (CORE_LOCK) {
            if (coreInstance != null) return coreInstance;

            // android.os.ServiceManager is a hidden framework class.
            // Reflection may itself be blocked on some Android builds; in that
            // case the caller reports a failure, never a fake "enabled" state.
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod(
                "getService", String.class);
            getService.setAccessible(true);
            Object rawBinder = getService.invoke(null, SERVICE_NAME);
            if (!(rawBinder instanceof IBinder)) {
                throw new IllegalStateException(
                    "Сервис moto_extend_service не предоставил Binder нашему APK");
            }

            Class<?> stubClass = Class.forName(SERVICE_INTERFACE + "$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object service = asInterface.invoke(null, (IBinder) rawBinder);
            if (service == null) {
                throw new IllegalStateException(
                    "Motorola IMotoExtendService.Stub.asInterface вернул null");
            }

            Class<?> interfaceClass = Class.forName(SERVICE_INTERFACE);
            Class<?> coreClass = Class.forName(CORE);
            Constructor<?> ctor = coreClass.getConstructor(
                interfaceClass, Context.class);
            Object result = ctor.newInstance(service, context.getApplicationContext());
            if (result == null) {
                throw new IllegalStateException(
                    "Конструктор Motorola Core вернул null");
            }
            coreInstance = result;
            return result;
        }
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
            return "Версия Motorola framework не содержит нужного API";
        }
        if (cause instanceof IllegalAccessException ||
                cause instanceof java.lang.reflect.InaccessibleObjectException) {
            return "Android ограничил доступ к Motorola framework: " +
                safe(cause.getMessage());
        }
        if (cause instanceof IllegalStateException) {
            return "Подключение Motorola Core: " + safe(cause.getMessage());
        }
        if (cause instanceof NullPointerException) {
            return "Motorola Core не инициализирован: " + safe(cause.getMessage());
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
