package com.moto.resolutionmaster;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/**
 * Direct Motorola GameTime API controls with independently verified readback.
 * Cannot silently obtain Motorola signature|privileged DPS permission.
 * No sysfs edits, raw Binder codes or fake success on void API calls.
 */
public final class MotorolaGameActivity extends Activity {
    private TextView tvAccess;
    private TextView tvDps;
    private TextView tvDpsRequests;
    private TextView tvTouch;
    private TextView tvMode;
    private TextView tvCapabilities;
    private TextView tvObserved;
    private Button btnRefresh;
    private Button btnGameTime;
    private Button btnShizuku;
    private Button btnCopyReport;
    private Button btnDpsSet;
    private Button btnTouchSet;
    private TextView tvControlFeedback;
    private MotorolaGameStateParser.Snapshot lastSnapshot;
    private volatile boolean controlBusy = false;
    private volatile boolean destroyed = false;
    private int requestSerial = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_motorola_game);

        tvAccess = findViewById(R.id.tv_game_access);
        tvDps = findViewById(R.id.tv_game_dps);
        tvDpsRequests = findViewById(R.id.tv_game_dps_request);
        tvTouch = findViewById(R.id.tv_game_touch);
        tvMode = findViewById(R.id.tv_game_mode);
        tvCapabilities = findViewById(R.id.tv_game_support);
        tvObserved = findViewById(R.id.tv_game_observed);
        btnRefresh = findViewById(R.id.btn_game_refresh);
        btnGameTime = findViewById(R.id.btn_game_gametime);
        btnShizuku = findViewById(R.id.btn_game_shizuku);
        btnCopyReport = findViewById(R.id.btn_game_copy_report);
        btnCopyReport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { copyDiagnosticReport(); }
        });
        btnDpsSet = findViewById(R.id.btn_game_dps_set);
        btnTouchSet = findViewById(R.id.btn_game_touch_set);
        tvControlFeedback = findViewById(R.id.tv_game_control_feedback);
        // These are actions, not status lamps: even a denied operation must
        // give a visible reason when the user taps it.
        btnDpsSet.setEnabled(true);
        btnTouchSet.setEnabled(true);
        btnDpsSet.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestBypassFromUi(); }
        });
        btnTouchSet.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestTouchFromUi(); }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { refresh(); }
        });
        btnGameTime.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openGameTime(); }
        });
        btnShizuku.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (ShellHelper.isShizukuRunning()) {
                    ShellHelper.requestShizukuPermission();
                    Toast.makeText(MotorolaGameActivity.this,
                        "Подтверди доступ Shizuku, затем нажми «Обновить»",
                        Toast.LENGTH_LONG).show();
                } else {
                    ShellHelper.openShizuku(MotorolaGameActivity.this);
                }
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        requestSerial++;
        super.onDestroy();
    }

    private void refresh() {
        if (controlBusy) return;
        final int request = ++requestSerial;
        btnRefresh.setEnabled(false);
        btnRefresh.setText("Считывание...");
        tvAccess.setText("Чтение системного сервиса Motorola...");
        // Never perform dumpsys on the Android UI thread.
        new Thread(new Runnable() {
            @Override public void run() {
                MotorolaGameStateParser.Snapshot snapshot;
                String access;
                try {
                    if (!ShellHelper.isShizukuPermissionGranted() &&
                            !ShellHelper.isRootAvailable()) {
                        snapshot = MotorolaGameStateParser.unavailable(
                            "Нет доступа Shizuku/Root");
                        access = "Нужен доступ Shizuku для чтения moto_extend_service";
                    } else {
                        ShellHelper.CommandResult result = ShellHelper.runCommandWithResult(
                            MotorolaGameActivity.this, "dumpsys moto_extend_service");
                        snapshot = result.success
                            ? MotorolaGameStateParser.parse(result.detail)
                            : MotorolaGameStateParser.unavailable(
                                result.detail.length() == 0
                                ? "dumpsys недоступен (код " + result.exitCode + ")"
                                : "Нет доступа к dumpsys: " + shortText(result.detail, 160));
                        access = snapshot.readable
                            ? "Shizuku/Root · системный сервис доступен"
                            : snapshot.error;
                    }
                } catch (Throwable e) {
                    snapshot = MotorolaGameStateParser.unavailable(
                        "Не удалось прочитать состояние Motorola");
                    access = snapshot.error;
                }
                final MotorolaGameStateParser.Snapshot state = snapshot;
                final String finalAccess = access;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || isFinishing() || request != requestSerial) return;
                        btnRefresh.setEnabled(true);
                        btnRefresh.setText("Обновить состояние");
                        showState(state, finalAccess);
                    }
                });
            }
        }, "MotorolaGameReadback").start();
    }

    private void showState(MotorolaGameStateParser.Snapshot s, String access) {
        lastSnapshot = s;
        tvAccess.setText(access);
        tvObserved.setText("Последняя проверка: " +
            DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
        btnShizuku.setVisibility(ShellHelper.isShizukuPermissionGranted()
            ? View.GONE : View.VISIBLE);

        if (!s.readable) {
            tvDps.setText("Нет данных");
            tvTouch.setText("Нет данных");
            tvDpsRequests.setText("Запросы питания: неизвестно");
            tvMode.setText("Активная игра: неизвестно");
            tvCapabilities.setText("Для диагностики необходим доступ к moto_extend_service.");
            updateControls(s);
            return;
        }
        if (Boolean.FALSE.equals(s.dpsSupported)) {
            tvDps.setText("Не поддерживается системой");
        } else if (!Boolean.TRUE.equals(s.dpsSupported)) {
            tvDps.setText("Поддержка неизвестна");
        } else if (s.hardwareDpsStatus == null) {
            tvDps.setText("Поддерживается · аппаратный статус неизвестен");
        } else if (s.hardwareDpsStatus.intValue() == 1) {
            tvDps.setText("АКТИВЕН · аппаратный статус 1");
        } else if (s.hardwareDpsStatus.intValue() == 0) {
            tvDps.setText("Выключен · аппаратный статус 0");
        } else {
            tvDps.setText("Неизвестный статус: " + s.hardwareDpsStatus);
        }
        tvDpsRequests.setText("Запросы GameTime: " +
            (s.dpsRequests == null ? "неизвестно" : shortText(s.dpsRequests, 160)));

        if (Boolean.TRUE.equals(s.touchRequested)) {
            tvTouch.setText("Touch Boost: запрос ВКЛ");
        } else if (Boolean.FALSE.equals(s.touchRequested)) {
            tvTouch.setText("Touch Boost: запрос ВЫКЛ");
        } else {
            tvTouch.setText("Touch Boost: нет активного запроса GameTime");
        }
        tvMode.setText("Игровой режим: " + truth(s.gameModeActive) +
            "\nИгра GameTime: " + (s.gamePackage == null ? "не определена" : s.gamePackage) +
            "\nЗапрос Lenovo Y70: " +
            (s.ownTouchRequested == null ? "не отправлен" :
                Boolean.TRUE.equals(s.ownTouchRequested) ? "включить" : "выключить"));
        tvCapabilities.setText(
            "Интерфейс Goodix/Motorola: " +
                (s.touchServiceConnected ? "подключён" : "не подтверждён") +
            "\nЧастота отчётов (AIDL): " + truth(s.reportRateSupported) +
            "\nЧастота опроса (AIDL): " + truth(s.sampleRateSupported) +
            "\nЧувствительность нажатий: " + truth(s.tapSensitivitySupported) +
            "\nОтзывчивость свайпов: " + truth(s.swipeResponsivenessSupported) +
            "\nПоддержка API не означает измеренную частоту или задержку.");
        updateControls(s);
    }

    private boolean hasDpsPermission() {
        return checkSelfPermission(MotoGameController.DPS_PERMISSION)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void updateControls(MotorolaGameStateParser.Snapshot s) {
        if (controlBusy) {
            btnDpsSet.setEnabled(false);
            btnTouchSet.setEnabled(false);
            return;
        }
        // Never disable due to missing permission/readback: user must be
        // able to tap and receive a specific denial or recovery instruction.
        btnDpsSet.setEnabled(true);
        btnTouchSet.setEnabled(true);
        boolean hardwareKnown = s.readable && Boolean.TRUE.equals(s.dpsSupported) &&
            (Integer.valueOf(0).equals(s.hardwareDpsStatus) ||
             Integer.valueOf(1).equals(s.hardwareDpsStatus));
        if (!hasDpsPermission()) {
            btnDpsSet.setText("Bypass: почему недоступен?");
        } else if (!hardwareKnown) {
            btnDpsSet.setText("Bypass: проверить доступ к контроллеру");
        } else {
            btnDpsSet.setText(Integer.valueOf(1).equals(s.hardwareDpsStatus)
                ? "Выключить Bypass" : "Включить Bypass");
        }
        btnTouchSet.setText(s.readable && s.touchServiceConnected
            ? (Boolean.TRUE.equals(s.ownTouchRequested)
                ? "Выключить наш Touch Boost" : "Включить Touch Boost")
            : "Touch Boost: проверить доступ");
        // Do not overwrite tvControlFeedback here: it belongs to the last
        // user action, not the async status refresh.
    }

    private void notifyAction(String message) {
        tvControlFeedback.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void requestBypassFromUi() {
        if (controlBusy) {
            notifyAction("Операция Motorola уже выполняется.");
            return;
        }
        if (!hasDpsPermission()) {
            notifyAction("Bypass заблокирован Android: этому APK не выдано " +
                "разрешение Motorola signature|privileged. Shizuku без Root " +
                "не может выдать его обычному приложению.");
            return;
        }
        final MotorolaGameStateParser.Snapshot s = lastSnapshot;
        if (s == null || !s.readable) {
            notifyAction("Bypass: системное состояние ещё не прочитано. " +
                "Проверь доступ Shizuku и нажми «Обновить состояние».");
            return;
        }
        if (!Boolean.TRUE.equals(s.dpsSupported)) {
            notifyAction("Motorola не подтверждает поддержку прямого питания.");
            return;
        }
        if (!Integer.valueOf(0).equals(s.hardwareDpsStatus) &&
                !Integer.valueOf(1).equals(s.hardwareDpsStatus)) {
            notifyAction("Неизвестный аппаратный статус Bypass. " +
                "Изменение питания отменено.");
            return;
        }
        final boolean enable = s.hardwareDpsStatus.intValue() == 0;
        if (enable && !PowerHelper.getBatteryInfo(this).isPlugged) {
            notifyAction("Для прямого питания сначала подключи зарядное устройство.");
            return;
        }
        if (enable) {
            new AlertDialog.Builder(this)
                .setTitle("Включить прямое питание?")
                .setMessage("Передадим команду штатному Motorola API. " +
                    "Успех покажем только после подтверждения аппаратного " +
                    "статуса контроллера. Отключить можно здесь или в GameTime.")
                .setPositiveButton("Включить", (d, which) -> runControl(true, true, null))
                .setNegativeButton("Отмена", null)
                .show();
        } else {
            runControl(true, false, null);
        }
    }

    private void requestTouchFromUi() {
        if (controlBusy) {
            notifyAction("Операция Motorola уже выполняется.");
            return;
        }
        final MotorolaGameStateParser.Snapshot s = lastSnapshot;
        if (s == null || !s.readable) {
            notifyAction("Touch Boost: нет данных от moto_extend_service. " +
                "Дай доступ Shizuku и нажми «Обновить состояние».");
            return;
        }
        if (!s.touchServiceConnected) {
            notifyAction("Touch Boost: сервис сенсора Motorola не подключён.");
            return;
        }
        String game = s.ownGamePackage != null ? s.ownGamePackage
            : s.gamePackage != null ? s.gamePackage
            : "com.riotgames.league.wildrift";
        boolean enable = !Boolean.TRUE.equals(s.ownTouchRequested);
        runControl(false, enable, game);
    }

    private void runControl(final boolean bypass, final boolean enable,
                            final String gamePackage) {
        if (controlBusy || destroyed) return;
        controlBusy = true;
        final int request = ++requestSerial;
        btnDpsSet.setEnabled(false);
        btnTouchSet.setEnabled(false);
        btnRefresh.setEnabled(false);
        tvControlFeedback.setText("Передача команды Motorola и проверка результата...");
        new Thread(new Runnable() {
            @Override public void run() {
                MotoGameController.Result call = bypass
                    ? MotoGameController.requestBypass(MotorolaGameActivity.this, enable)
                    : MotoGameController.requestTouch(
                        MotorolaGameActivity.this, enable, gamePackage);
                MotorolaGameStateParser.Snapshot observed = null;
                if (ShellHelper.isShizukuPermissionGranted() || ShellHelper.isRootAvailable()) {
                    // Reconcile from the system state, not a void method return.
                    for (int attempt = 0; attempt < (call.sent ? 3 : 1); attempt++) {
                        if (attempt > 0) {
                            try { Thread.sleep(450); }
                            catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        ShellHelper.CommandResult data = ShellHelper.runCommandWithResult(
                            MotorolaGameActivity.this, "dumpsys moto_extend_service");
                        observed = data.success
                            ? MotorolaGameStateParser.parse(data.detail)
                            : MotorolaGameStateParser.unavailable("Нет доступа к системной проверке");
                        if (verified(observed, bypass, enable, gamePackage)) break;
                    }
                }
                final MotorolaGameStateParser.Snapshot finalObserved = observed;
                final boolean verified = call.sent &&
                    verified(observed, bypass, enable, gamePackage);
                final String feedback = !call.sent
                    ? call.reason
                    : verified
                        ? (bypass
                            ? "Bypass: аппаратное состояние подтверждено"
                            : "Touch Boost: подтверждён запрос Lenovo Y70 в Motorola; " +
                              "частота опроса не измерялась")
                        : "Команда отправлена, но её результат не подтверждён " +
                          "системным сервисом. Состояние не изменено в интерфейсе.";
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || isFinishing() || request != requestSerial) return;
                        controlBusy = false;
                        btnRefresh.setEnabled(true);
                        if (finalObserved != null && finalObserved.readable) {
                            showState(finalObserved, "Состояние перечитано из Motorola");
                        } else if (lastSnapshot != null) {
                            updateControls(lastSnapshot);
                        }
                        // Status refresh must not replace this result with an
                        // unrelated warning about the other control.
                        notifyAction(feedback);
                    }
                });
            }
        }, "MotoGameControl").start();
    }

    private static boolean verified(MotorolaGameStateParser.Snapshot state,
                                    boolean bypass, boolean enable, String gamePackage) {
        if (state == null || !state.readable) return false;
        if (bypass) return (enable ? Integer.valueOf(1) : Integer.valueOf(0))
            .equals(state.hardwareDpsStatus);
        return (enable ? Boolean.TRUE : Boolean.FALSE).equals(state.ownTouchRequested) &&
            gamePackage != null && gamePackage.equals(state.ownGamePackage);
    }

    private static String truth(Boolean state) {
        return state == null ? "неизвестно" : state ? "поддерживается / да" : "нет";
    }

    private static String shortText(String input, int limit) {
        String compact = input.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > limit ? compact.substring(0, limit) + "…" : compact;
    }

    private void copyDiagnosticReport() {
        // Share concise, live UI readbacks; do not include raw Binder tokens
        // or the unredacted output of dumpsys.
        String report = "LenovoMotorolovichY70 · Motorola control diagnostics\n" +
            "App: " + getPackageName() + "\n" +
            "Shizuku: " + ShellHelper.isShizukuPermissionGranted() + "\n" +
            "Motorola DPS permission: " + hasDpsPermission() + "\n" +
            "Service: " + tvAccess.getText() + "\n" +
            "Last action: " + tvControlFeedback.getText() + "\n" +
            "DPS: " + tvDps.getText() + "\n" +
            "Requests: " + tvDpsRequests.getText() + "\n" +
            "Touch: " + tvTouch.getText() + "\n" +
            "Mode: " + tvMode.getText() + "\n" +
            "Capabilities: " + tvCapabilities.getText();
        ShellHelper.copyToClipboard(this, report, "Motorola diagnostics");
        Toast.makeText(this, "Диагностика скопирована. Вставь её в чат.",
            Toast.LENGTH_LONG).show();
    }

    private void openGameTime() {
        // A GameTime UI may exist only as an in-game overlay on this ROM.
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.motorola.gamemode");
        if (intent == null) {
            intent = getPackageManager().getLaunchIntentForPackage(
                "com.riotgames.league.wildrift");
            if (intent != null) {
                Toast.makeText(this, "Открываю Wild Rift. GameTime доступен внутри игры.",
                    Toast.LENGTH_LONG).show();
            }
        }
        if (intent == null) {
            Toast.makeText(this, "Открой игру и вызови штатный GameTime в ней.",
                Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (Throwable e) {
            Toast.makeText(this, "Не удалось открыть GameTime или Wild Rift",
                Toast.LENGTH_LONG).show();
        }
    }
}
