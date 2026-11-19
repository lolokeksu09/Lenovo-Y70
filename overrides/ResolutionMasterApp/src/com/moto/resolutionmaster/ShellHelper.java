package com.moto.resolutionmaster;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

public class ShellHelper {

    private static final String TAG = "ResolutionMaster";

    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    public static final String SHIZUKU_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api";
    public static final String SHIZUKU_GITHUB_URL = "https://github.com/RikkaApps/Shizuku/releases";
    public static final String SHIZUKU_4PDA_URL = "https://4pda.to/forum/index.php?showtopic=944888";

    public static final String PERM_WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";
    public static final String ADB_GRANT_COMMAND = "adb shell pm grant com.moto.resolutionmaster android.permission.WRITE_SECURE_SETTINGS";

    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    public static boolean hasWriteSecureSettings(Context context) {
        if (context == null) return false;
        return context.checkCallingOrSelfPermission(PERM_WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean grantWriteSecureSettings(Context context) {
        if (hasWriteSecureSettings(context)) {
            return true;
        }
        String pkg = context.getPackageName();
        String cmd = "pm grant " + pkg + " " + PERM_WRITE_SECURE_SETTINGS;
        CommandResult result = runCommandWithResult(context, cmd);
        boolean verified = result.success && hasWriteSecureSettings(context);
        if (!verified) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS was not confirmed: " + result.detail);
        }
        return verified;
    }

    /**
     * A single shell operation with explicit exit-code evidence.
     * This does not prove an Android setting took effect; callers must read it back.
     */
    public static final class CommandResult {
        public final boolean success;
        public final int exitCode;
        public final String detail;

        private CommandResult(boolean success, int exitCode, String detail) {
            this.success = success;
            this.exitCode = exitCode;
            this.detail = detail == null ? "" : detail;
        }
    }

    /**
     * Use this for ONE atomic shell command. The exit-code marker is emitted by
     * the shell, even when its command writes diagnostics to stderr.
     * This method does not hide permission failures as successes.
     */
    public static CommandResult runCommandWithResult(Context context, String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return new CommandResult(false, -1, "Пустая команда");
        }
        if (!isShizukuPermissionGranted() && !isRootAvailable()) {
            return new CommandResult(false, -1, "Требуется доступ Shizuku или Root");
        }
        final String marker = "__RM_EXIT_CODE__=";
        final String wrapped = "{ " + cmd + "; } 2>&1; printf '\\n" + marker + "%s\\n' "$?"";
        final String output = runCommandWithOutput(context, wrapped);
        if (output == null) {
            return new CommandResult(false, -1, "Нет ответа от Shell");
        }
        int index = output.lastIndexOf(marker);
        if (index < 0) {
            return new CommandResult(false, -1, "Shell не сообщил код завершения: " + output.trim());
        }
        String rawCode = output.substring(index + marker.length()).trim();
        int newline = rawCode.indexOf('\n');
        if (newline >= 0) rawCode = rawCode.substring(0, newline).trim();
        try {
            int exitCode = Integer.parseInt(rawCode);
            String detail = output.substring(0, index).trim();
            return new CommandResult(exitCode == 0, exitCode, detail);
        } catch (NumberFormatException e) {
            return new CommandResult(false, -1, "Некорректный код Shell: " + rawCode);
        }
    }

    public static boolean runCommand(Context context, String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return false;
        }

        // 1. Try Root (su)
        if (runRoot(cmd)) {
            Log.d(TAG, "Command executed via Root: " + cmd);
            return true;
        }

        // 2. Try Shizuku IPC
        if (runShizuku(cmd)) {
            Log.d(TAG, "Command executed via Shizuku: " + cmd);
            return true;
        }

        return false;
    }

    public static String runCommandWithOutput(Context context, String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return "";
        }
        StringBuilder output = new StringBuilder();

        // 1. Try Shizuku IPC
        if (isShizukuPermissionGranted()) {
            try {
                Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                newProcessMethod.setAccessible(true);
                java.lang.Process p = (java.lang.Process) newProcessMethod.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
                if (p != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    p.waitFor();
                    return output.toString();
                }
            } catch (Throwable t) {
                Log.e(TAG, "runCommandWithOutput Shizuku error", t);
            }
        }

        // 2. Try Root
        if (isRootAvailable()) {
            try {
                java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                p.waitFor();
                return output.toString();
            } catch (Throwable t) {
                Log.e(TAG, "runCommandWithOutput Root error", t);
            }
        }

        return "";
    }

    public static boolean isRootAvailable() {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            int exit = p.waitFor();
            return exit == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShizukuInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isShizukuPermissionGranted() {
        try {
            if (!isShizukuRunning()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean writeShellFile(Context context, byte[] data, String targetPath) {
        if (data == null || targetPath == null) return false;

        // Check if target file already exists and size matches
        try {
            String checkCmd = "stat -c %s " + targetPath + " 2>/dev/null || wc -c < " + targetPath + " 2>/dev/null";
            String sizeOut = runCommandWithOutput(context, checkCmd);
            if (sizeOut != null && sizeOut.trim().equals(String.valueOf(data.length))) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 1. Try Shizuku process stdin
        if (isShizukuPermissionGranted()) {
            try {
                Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                newProcessMethod.setAccessible(true);
                java.lang.Process p = (java.lang.Process) newProcessMethod.invoke(null, new String[]{"sh", "-c", "cat > " + targetPath + " && chmod 644 " + targetPath}, null, null);
                if (p != null) {
                    java.io.OutputStream os = p.getOutputStream();
                    os.write(data);
                    os.flush();
                    os.close();
                    int exit = p.waitFor();
                    if (exit == 0) return true;
                }
            } catch (Throwable t) {
                Log.e(TAG, "writeShellFile Shizuku stdin error", t);
            }
        }

        // 2. Try Root process stdin
        if (isRootAvailable()) {
            try {
                java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat > " + targetPath + " && chmod 644 " + targetPath});
                java.io.OutputStream os = p.getOutputStream();
                os.write(data);
                os.flush();
                os.close();
                int exit = p.waitFor();
                if (exit == 0) return true;
            } catch (Throwable t) {
                Log.e(TAG, "writeShellFile Root stdin error", t);
            }
        }

        // 3. Fallback: Base64 string stream via shell
        try {
            String b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            String b64Cmd = "echo '" + b64 + "' | base64 -d > " + targetPath + " && chmod 644 " + targetPath;
            if (runCommand(context, b64Cmd)) {
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "writeShellFile base64 error", t);
        }

        return false;
    }

    public static void requestShizukuPermission() {
        try {
            if (isShizukuRunning()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to request Shizuku permission", t);
        }
    }

    public static boolean runShizuku(String cmd) {
        try {
            if (!isShizukuPermissionGranted()) {
                Log.w(TAG, "runShizuku: permission not granted");
                return false;
            }
            Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
            java.lang.Process p = (java.lang.Process) newProcessMethod.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            if (p != null) {
                int exit = p.waitFor();
                Log.d(TAG, "runShizuku exited with code: " + exit + " for cmd: " + cmd);
                return exit == 0;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to run command via Shizuku", t);
        }
        return false;
    }

    public static void openShizuku(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to open Shizuku", t);
        }
    }

    public static void openUrl(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to open URL: " + url, t);
        }
    }

    private static boolean runRoot(String cmd) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            int exit = p.waitFor();
            return exit == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void copyToClipboard(Context context, String text, String label) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(label, text));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to copy to clipboard", t);
        }
    }
}
