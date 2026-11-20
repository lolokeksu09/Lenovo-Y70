package com.moto.resolutionmaster;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;

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
    private static final String CORE = "com.motorola.misc.MotoExtendManagerCore";
    private static final String OEM_SERVICE = "com.motorola.misc.IMotoExtendService";
    private static final String OEM_STUB = "com.motorola.misc.IMotoExtendService$Stub";
    private static final String SERVICE_NAME = "moto_extend_service";
    private static volatile Object cachedCore;
    // Binder token must be stable across toggles: the Motorola service tracks
    // Direct Power Supply requests by client token, not just a boolean.
    private static final IBinder DPS_TOKEN = new Binder();
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
            Object core = core(context);
            Method supported = core.getClass().getMethod("isDirectPowerSupplySupported");
            if (!Boolean.TRUE.equals(supported.invoke(core))) {
                return Result.refused("Контроллер Motorola не поддерживает Direct Power Supply");
            }
            core.getClass().getMethod("setDirectPowerSupplyStatus", int.class, IBinder.class)
                .invoke(core, enable ? 1 : 0, DPS_TOKEN);
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
            Object core = core(context);
            Method supported = core.getClass().getMethod("isTouchEnhanceSupported");
            if (!Boolean.TRUE.equals(supported.invoke(core))) {
                return Result.refused("Motorola не разрешает Touch Boost для этого приложения");
            }
            Bundle options = new Bundle();
            options.putInt("touch_enhance_enable", enable ? 1 : 0);
            options.putString("game_package_name", gamePackage);
            options.putInt("display", 0);
            // Motorola core fills game_mode_op_package_name from real Context.
            // No forged identity and no raw Binder transaction numbers.
            core.getClass().getMethod("setGameModeState", boolean.class, Bundle.class)
                .invoke(core, true, options);
            return Result.accepted();
        } catch (Throwable t) {
            return Result.refused(explain(t));
        }
    }

    /**
     * The normal MotoExtendManager.getInstance(Context) obtains its core via
     * Context.getSystemService("moto_extend_service"). On this phone, that
     * Context lookup returns null to the third-party APK, even though the
     * Binder service exists (shown by "service list" and "dumpsys").
     *
     * The firmware's actual MotoExtendManagerCore has a PUBLIC constructor
     * (IMotoExtendService, Context), while IMotoExtendService.Stub has
     * asInterface(IBinder). Bind directly with the caller's real Context
     * rather than dereferencing the null OEM wrapper field.
     *
     * Android may still deny ServiceManager lookup or Motorola's permission
     * checks. We surface that denial; we never claim success without dumpsys.
     */
    private static Object core(Context context) throws Exception {
        Object ready = cachedCore;
        if (ready != null) return ready;
        synchronized (MotoGameController.class) {
            if (cachedCore != null) return cachedCore;
            Context app = context.getApplicationContext();
            Class<?> coreClass = Class.forName(CORE);

            Object registered = app.getSystemService(SERVICE_NAME);
            if (coreClass.isInstance(registered)) {
                cachedCore = registered;
                return registered;
            }

            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            Object raw = getService.invoke(null, SERVICE_NAME);
            if (!(raw instanceof IBinder)) {
                throw new IllegalStateException("Сервис moto_extend_service не найден");
            }
            IBinder binder = (IBinder) raw;
            Class<?> interfaceClass = Class.forName(OEM_SERVICE);
            Class<?> stubClass = Class.forName(OEM_STUB);
            Object service = stubClass.getMethod("asInterface", IBinder.class)
                .invoke(null, binder);
            if (service == null || !interfaceClass.isInstance(service)) {
                throw new IllegalStateException("Интерфейс MotoExtendService недоступен");
            }
            Object actual = coreClass.getConstructor(interfaceClass, Context.class)
                .newInstance(service, app);
            cachedCore = actual;
            return actual;
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
            return "Не найдены системные классы Motorola на этой прошивке";
        }
        if (cause instanceof IllegalAccessException || cause instanceof NoSuchMethodException) {
            return "Android ограничил доступ к Binder/скрытому API Motorola: " +
                cause.getClass().getSimpleName();
        }
        if (cause instanceof IllegalStateException) {
            return cause.getMessage() == null ? "Сервис Motorola недоступен" : cause.getMessage();
        }
        if (cause instanceof NullPointerException) {
            return "Не удалось инициализировать клиент Motorola (null core). " +
                "Проверь доступ к moto_extend_service";
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
