package com.moto.resolutionmaster;

import java.util.Locale;

/**
 * Convert shell/PackageManager exceptions into short, reliable user feedback.
 * Never show a full system-server stack trace in an AlertDialog.
 */
public final class BulkEnableFailure {
    private BulkEnableFailure() {}

    public static boolean isSystemRestricted(int code, String output) {
        if (code == 0 || output == null) return false;
        String text = output.toLowerCase(Locale.US);
        return text.contains("shell cannot change component state") ||
               text.contains("java.lang.securityexception") ||
               text.contains("securityexception") ||
               text.contains("permission denial") ||
               text.contains("not allowed to change") ||
               text.contains("not allowed to enable");
    }

    public static String explain(int code, String output) {
        if (isSystemRestricted(code, output)) {
            return "Ограничение Android: оболочке Shizuku запрещено изменять этот пакет";
        }
        String diagnostic = output == null ? "" : output.toLowerCase(Locale.US);
        if (diagnostic.contains("unknown package") ||
                diagnostic.contains("no such package") ||
                diagnostic.contains("package not found")) {
            return "Пакет не найден для текущего пользователя";
        }
        if (diagnostic.contains("permission denied")) {
            return "Недостаточно системных прав для изменения пакета";
        }
        if (diagnostic.contains("not installed")) {
            return "Приложение не установлено для текущего пользователя";
        }
        if (code == 0) {
            return "Команда выполнена, но изменение состояния не подтверждено";
        }
        return "Команда не выполнена (код " + code + "); проверьте Shizuku/Root";
    }
}
