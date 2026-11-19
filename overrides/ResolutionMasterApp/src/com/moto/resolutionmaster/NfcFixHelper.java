package com.moto.resolutionmaster;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class NfcFixHelper {

    private static final String TAG = "NfcFixHelper";
    public static final String MODULE_ID = "moto_y70_nfc_fix";
    public static final String MODULE_PATH = "/data/adb/modules/" + MODULE_ID;
    public static final String DEX_TARGET = "/data/local/tmp/nfc-fix.dex";

    public enum NfcStatus {
        FIXED_100_PERCENT, // Legacy internal enum name: DH routing does not guarantee payment compatibility
        LEGACY_FIX,        // DH, DH, eSE1 — mixed routing
        FACTORY_STOCK,     // eSE1, eSE1, eSE1 — factory-style routing
        NFC_OFF,           // NFC is disabled in settings
        UNKNOWN            // Shell / dumpsys unavailable
    }

    public static class RoutingInfo {
        public NfcStatus status = NfcStatus.UNKNOWN;
        public String emptyAid = "";
        public String isoDep = "";
        public String techA = "";
        public String techB = "";
        public String rawDetails = "";

        public String getDisplayStatus() {
            switch (status) {
                case FIXED_100_PERCENT:
                    return "Маршрутизация DH обнаружена (оплата не проверена)";
                case LEGACY_FIX:
                    return "Обнаружена смешанная маршрутизация DH/eSE1";
                case FACTORY_STOCK:
                    return "Обнаружена маршрутизация eSE1";
                case NFC_OFF:
                    return "NFC выключен в настройках смартфона";
                default:
                    return "Статус: нажмите для проверки / активации";
            }
        }

        public int getStatusColor() {
            switch (status) {
                case FIXED_100_PERCENT:
                    return 0xFF10B981; // accent_green
                case LEGACY_FIX:
                    return 0xFFF59E0B; // accent_yellow
                case FACTORY_STOCK:
                    return 0xFFEF4444; // accent_red
                default:
                    return 0xFF9E9E9E; // text_muted
            }
        }
    }

    public static RoutingInfo getLiveRoutingInfo(Context context) {
        RoutingInfo info = new RoutingInfo();

        // 1. Authoritative check via helper status (direct IPC to INfcCardEmulation)
        extractDexHelper(context);
        String helperStatus = ShellHelper.runCommandWithOutput(context, "CLASSPATH=" + DEX_TARGET + " app_process /system/bin NfcFix status");
        if (helperStatus != null && !helperStatus.isEmpty()) {
            if (helperStatus.contains("nfc_state=off")) {
                info.status = NfcStatus.NFC_OFF;
                return info;
            }
            // Prefer post-operation values; a stale before= field is not success.
            Matcher after = Pattern.compile("after=(DH|eSE1),(DH|eSE1),(DH|eSE1)").matcher(helperStatus);
            Matcher before = Pattern.compile("before=(DH|eSE1),(DH|eSE1),(DH|eSE1)").matcher(helperStatus);
            String observed = null;
            if (after.find()) observed = after.group(1) + "," + after.group(2) + "," + after.group(3);
            else if (before.find()) observed = before.group(1) + "," + before.group(2) + "," + before.group(3);
            if ("DH,DH,DH".equals(observed)) {
                info.status = NfcStatus.FIXED_100_PERCENT;
                return info;
            } else if ("DH,DH,eSE1".equals(observed)) {
                info.status = NfcStatus.LEGACY_FIX;
                return info;
            } else if ("eSE1,eSE1,eSE1".equals(observed)) {
                info.status = NfcStatus.FACTORY_STOCK;
                return info;
            }
        }

        // 2. Secondary check via dumpsys nfc
        String dump = ShellHelper.runCommandWithOutput(context, "dumpsys nfc");
        if (dump == null || dump.isEmpty()) {
            return info;
        }

        if (dump.contains("mState=off") || dump.contains("mState=1")) {
            info.status = NfcStatus.NFC_OFF;
            return info;
        }

        Pattern tablePattern = Pattern.compile("(?s)--- dumpRoutingTable: start ---\\s*(.*?)--- dumpRoutingTable:\\s+end\\s+---");
        Matcher tableMatcher = tablePattern.matcher(dump);
        if (tableMatcher.find()) {
            String table = tableMatcher.group(1);
            info.rawDetails = table;

            Pattern rowPattern = Pattern.compile("^\\s*(Empty_AID|PROTOCOL_ISO_DEP|TECHNOLOGY_A|TECHNOLOGY_B)\\s+(0x[0-9a-fA-F]{2})", Pattern.MULTILINE);
            Matcher rowMatcher = rowPattern.matcher(table);
            while (rowMatcher.find()) {
                String name = rowMatcher.group(1);
                String target = rowMatcher.group(2).toLowerCase();
                if ("Empty_AID".equals(name)) info.emptyAid = target;
                else if ("PROTOCOL_ISO_DEP".equals(name)) info.isoDep = target;
                else if ("TECHNOLOGY_A".equals(name)) info.techA = target;
                else if ("TECHNOLOGY_B".equals(name)) info.techB = target;
            }

            boolean emptyIsDh = "0x00".equalsIgnoreCase(info.emptyAid);
            boolean emptyIsEse = "0xc0".equalsIgnoreCase(info.emptyAid);
            boolean isoIsDh = "0x00".equalsIgnoreCase(info.isoDep);
            boolean techIsDh = "0x00".equalsIgnoreCase(info.techA);

            if (emptyIsDh && (isoIsDh || techIsDh)) {
                info.status = NfcStatus.FIXED_100_PERCENT;
            } else if (isoIsDh && emptyIsEse) {
                info.status = NfcStatus.LEGACY_FIX;
            } else if (emptyIsEse || "0xc0".equalsIgnoreCase(info.techA)) {
                info.status = NfcStatus.FACTORY_STOCK;
            } else {
                info.status = NfcStatus.UNKNOWN;
            }
        }

        return info;
    }

    public static boolean isMasterFixActive(Context context) {
        RoutingInfo info = getLiveRoutingInfo(context);
        return info.status == NfcStatus.FIXED_100_PERCENT;
    }

    public static boolean extractDexHelper(Context context) {
        try {
            // First check if already on device with valid size
            String checkCmd = "stat -c %s " + DEX_TARGET + " 2>/dev/null || wc -c < " + DEX_TARGET + " 2>/dev/null";
            String sizeOut = ShellHelper.runCommandWithOutput(context, checkCmd);
            if (sizeOut != null && !sizeOut.trim().isEmpty()) {
                try {
                    long size = Long.parseLong(sizeOut.trim());
                    if (size > 10000) {
                        return true; // Already extracted and valid
                    }
                } catch (Throwable ignored) {}
            }

            InputStream is = context.getAssets().open("nfc-fix.dex");
            byte[] buf = new byte[is.available()];
            int read = 0;
            while (read < buf.length) {
                int r = is.read(buf, read, buf.length - read);
                if (r == -1) break;
                read += r;
            }
            is.close();

            boolean ok = ShellHelper.writeShellFile(context, buf, DEX_TARGET);
            Log.i(TAG, "extractDexHelper write result: " + ok);
            return ok;
        } catch (Throwable t) {
            Log.e(TAG, "extractDexHelper failed", t);
            return false;
        }
    }

    /**
     * Apply only when routes can be read. Do not retry aggressive low-level
     * operations or modify other installed Magisk modules.
     */
    public static boolean applyMasterFix(Context context) {
        if (!extractDexHelper(context)) return false;
        RoutingInfo before = getLiveRoutingInfo(context);
        if (before.status == NfcStatus.FIXED_100_PERCENT) return true;
        if (before.status != NfcStatus.LEGACY_FIX &&
                before.status != NfcStatus.FACTORY_STOCK) {
            Log.w(TAG, "NFC routing not readable; aborting apply");
            return false;
        }
        String cmd = "CLASSPATH=" + DEX_TARGET + " app_process /system/bin NfcFix apply";
        String output = ShellHelper.runCommandWithOutput(context, cmd);
        if (output == null || !output.contains("result=ok")) {
            Log.w(TAG, "NFC apply failed; no automatic retry");
            return false;
        }
        RoutingInfo after = getLiveRoutingInfo(context);
        return after.status == NfcStatus.FIXED_100_PERCENT;
    }

    /**
     * Explicit user-requested rollback with readback and no implicit deletion.
     */
    public static boolean revertToStock(Context context) {
        if (!extractDexHelper(context)) return false;
        RoutingInfo before = getLiveRoutingInfo(context);
        if (before.status == NfcStatus.FACTORY_STOCK) return true;
        if (before.status != NfcStatus.FIXED_100_PERCENT &&
                before.status != NfcStatus.LEGACY_FIX) {
            Log.w(TAG, "NFC routing not readable; aborting rollback");
            return false;
        }
        String cmd = "CLASSPATH=" + DEX_TARGET + " app_process /system/bin NfcFix undo";
        String output = ShellHelper.runCommandWithOutput(context, cmd);
        if (output == null || !output.contains("result=ok")) {
            Log.w(TAG, "NFC undo failed; no automatic retry");
            return false;
        }
        RoutingInfo after = getLiveRoutingInfo(context);
        return after.status == NfcStatus.FACTORY_STOCK;
    }

    public static File exportModuleZip(Context context) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File zipFile = new File(downloadsDir, "moto_y70_nfc_master_fix_v2.5.zip");
            File tempFile = null;
            boolean useTemp = false;

            try {
                FileOutputStream testFos = new FileOutputStream(zipFile);
                testFos.close();
            } catch (Throwable t) {
                useTemp = true;
                File cacheDir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
                tempFile = new File(cacheDir, "moto_y70_nfc_master_fix_v2.5.zip");
            }

            File targetZip = useTemp ? tempFile : zipFile;

            // Extract dex helper bytes
            byte[] dexBytes;
            try (InputStream is = context.getAssets().open("nfc-fix.dex")) {
                dexBytes = new byte[is.available()];
                is.read(dexBytes);
            }

            String moduleProp = 
                "id=" + MODULE_ID + "\n" +
                "name=Lenovo Y70 / Moto Edge 70 Max NFC Master Fix (DH, DH, DH)\n" +
                "version=v2.5\n" +
                "versionCode=250\n" +
                "author=LenovoMotorolovich & Community\n" +
                "description=Эталонный фикс NFC для Lenovo Legion Y70: перенаправляет Empty AID, ISO-DEP и Tech A/B на Host Android (DH 0x00). Совместимость оплаты необходимо проверить на устройстве.\n";

            String serviceSh = 
                "#!/system/bin/sh\n" +
                "MODDIR=${0%/*}\n" +
                "sleep 10\n" +
                "CLASSPATH=$MODDIR/nfc-fix.dex app_process /system/bin NfcFix apply\n";

            String updateBinary = 
                "#!/sbin/sh\n" +
                "OUTFD=$2\n" +
                "ZIPFILE=$3\n" +
                "ui_print() { echo \"$1\"; }\n" +
                "ui_print \"************************************************\"\n" +
                "ui_print \"* Lenovo Y70 NFC Master Fix v2.5 (DH, DH, DH)  *\"\n" +
                "ui_print \"* Payment compatibility is not guaranteed.        *\"\n" +
                "ui_print \"************************************************\"\n" +
                "MODDIR=/data/adb/modules/moto_y70_nfc_fix\n" +
                "mkdir -p $MODDIR\n" +
                "unzip -o \"$ZIPFILE\" \"module.prop\" -d \"$MODDIR\" >&2\n" +
                "unzip -o \"$ZIPFILE\" \"nfc-fix.dex\" -d \"$MODDIR\" >&2\n" +
                "unzip -o \"$ZIPFILE\" \"service.sh\" -d \"$MODDIR\" >&2\n" +
                "chmod -R 755 $MODDIR\n" +
                "chmod 644 $MODDIR/module.prop\n" +
                "chmod 644 $MODDIR/nfc-fix.dex\n" +
                "chmod 755 $MODDIR/service.sh\n" +
                "touch $MODDIR/auto_mount\n" +
                "ui_print \"- Applying live NFC routes right now...\"\n" +
                "CLASSPATH=$MODDIR/nfc-fix.dex app_process /system/bin NfcFix apply\n" +
                "ui_print \"- NFC Master Fix successfully applied!\"\n" +
                "exit 0\n";

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip))) {
                zos.putNextEntry(new ZipEntry("module.prop"));
                zos.write(moduleProp.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("service.sh"));
                zos.write(serviceSh.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("nfc-fix.dex"));
                zos.write(dexBytes);
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("META-INF/com/google/android/updater-script"));
                zos.write("#MAGISK\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("META-INF/com/google/android/update-binary"));
                zos.write(updateBinary.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            if (useTemp && tempFile != null && tempFile.exists()) {
                String cpCmd = "cp " + tempFile.getAbsolutePath() + " " + zipFile.getAbsolutePath() + " && chmod 664 " + zipFile.getAbsolutePath();
                ShellHelper.runCommand(context, cpCmd);
            }

            Log.i(TAG, "Exported Magisk module to: " + zipFile.getAbsolutePath());
            return zipFile;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to export Magisk module zip", t);
            return null;
        }
    }

    public static File exportRollbackZip(Context context) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File zipFile = new File(downloadsDir, "moto_y70_nfc_revert_stock.zip");
            File tempFile = null;
            boolean useTemp = false;

            try {
                FileOutputStream testFos = new FileOutputStream(zipFile);
                testFos.close();
            } catch (Throwable t) {
                useTemp = true;
                File cacheDir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
                tempFile = new File(cacheDir, "moto_y70_nfc_revert_stock.zip");
            }

            File targetZip = useTemp ? tempFile : zipFile;

            byte[] dexBytes;
            try (InputStream is = context.getAssets().open("nfc-fix.dex")) {
                dexBytes = new byte[is.available()];
                is.read(dexBytes);
            }

            String rollbackScript = 
                "#!/sbin/sh\n" +
                "OUTFD=$2\n" +
                "ZIPFILE=$3\n" +
                "ui_print() { echo \"$1\"; }\n" +
                "ui_print \"************************************************\"\n" +
                "ui_print \"* Lenovo Y70 NFC Stock Revert / Rollback (eSE) *\"\n" +
                "ui_print \"************************************************\"\n" +
                "MODDIR=/data/adb/modules/moto_y70_nfc_fix\n" +
                "unzip -o \"$ZIPFILE\" \"nfc-fix.dex\" -d \"/data/local/tmp\" >&2\n" +
                "CLASSPATH=/data/local/tmp/nfc-fix.dex app_process /system/bin NfcFix undo\n" +
                "rm -rf /data/adb/modules/moto_y70_nfc_fix\n" +
                "ui_print \"- Reverted NFC routes to factory eSE1!\"\n" +
                "exit 0\n";

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip))) {
                zos.putNextEntry(new ZipEntry("nfc-fix.dex"));
                zos.write(dexBytes);
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("META-INF/com/google/android/updater-script"));
                zos.write("#MAGISK\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("META-INF/com/google/android/update-binary"));
                zos.write(rollbackScript.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            if (useTemp && tempFile != null && tempFile.exists()) {
                String cpCmd = "cp " + tempFile.getAbsolutePath() + " " + zipFile.getAbsolutePath() + " && chmod 664 " + zipFile.getAbsolutePath();
                ShellHelper.runCommand(context, cpCmd);
            }

            Log.i(TAG, "Exported Magisk rollback module to: " + zipFile.getAbsolutePath());
            return zipFile;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to export Magisk rollback zip", t);
            return null;
        }
    }
}
