package com.moto.resolutionmaster;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private TextView tvThermal;
    private TextView tvCaptureStatus;
    private TextView tvObserved;
    private Button btnRefresh;
    private Button btnGameTime;
    private Button btnShizuku;
    private Button btnCopyReport;
    private Button btnDpsSet;
    private Button btnTouchSet;
    private Button btnDirectRetry;
    private Button btnCaptureStart;
    private Button btnCaptureStop;
    private Button btnCaptureCopy;
    private TextView tvControlFeedback;
    private MotorolaGameStateParser.Snapshot lastSnapshot;
    private volatile boolean controlBusy = false;
    private volatile boolean destroyed = false;
    private int requestSerial = 0;
    private final Handler captureHandler = new Handler(Looper.getMainLooper());
    private boolean captureUiVisible;
    private final Runnable captureStatusPoll = new Runnable() {
        @Override public void run() {
            if (!captureUiVisible || destroyed) return;
            updateCaptureStatus();
            captureHandler.postDelayed(this, 2000L);
        }
    };

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
        tvThermal = findViewById(R.id.tv_game_thermal);
        tvCaptureStatus = findViewById(R.id.tv_game_capture_status);
        btnCaptureStart = findViewById(R.id.btn_game_capture_start);
        btnCaptureStop = findViewById(R.id.btn_game_capture_stop);
        btnCaptureCopy = findViewById(R.id.btn_game_capture_copy);
        btnCaptureStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startGameCapture(); }
        });
        btnCaptureStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { stopGameCapture(); }
        });
        btnCaptureCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { copyGameCapture(); }
        });
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
        btnDirectRetry = findViewById(R.id.btn_game_direct_retry);
        btnDirectRetry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { resetDirectAccessGate(); }
        });
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
        MotorolaDirectAccessGate.migrateLegacyFailure(
            this, MotorolaControlTrace.lastCompleted(this));
        if (!controlBusy) {
            String pending = MotorolaControlTrace.pending(this);
            if (pending != null) {
                tvControlFeedback.setText(
                    "Предыдущая операция «" + pending + "» прервалась до ответа. " +
                    "Причина завершения процесса неизвестна; нужен системный журнал.");
            } else {
                String outcome = MotorolaControlTrace.lastCompleted(this);
                if (outcome != null) tvControlFeedback.setText(outcome);
            }
        }
        captureUiVisible = true;
        captureHandler.removeCallbacks(captureStatusPoll);
        captureHandler.post(captureStatusPoll);
        refresh();
    }

    @Override protected void onPause() {
        captureUiVisible = false;
        captureHandler.removeCallbacks(captureStatusPoll);
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        captureUiVisible = false;
        captureHandler.removeCallbacks(captureStatusPoll);
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
                String thermal;
                if (!ShellHelper.isShizukuPermissionGranted() &&
                        !ShellHelper.isRootAvailable()) {
                    thermal = MotorolaThermalState.unavailable("нужен Shizuku/Root");
                } else {
                    try {
                        ShellHelper.CommandResult result =
                            ShellHelper.runCommandWithResult(
                                MotorolaGameActivity.this,
                                MotorolaThermalState.READ_COMMAND);
                        thermal = result.success
                            ? MotorolaThermalState.parse(result.detail)
                            : MotorolaThermalState.unavailable(
                                "Shell: код " + result.exitCode);
                    } catch (Throwable e) {
                        thermal = MotorolaThermalState.unavailable(
                            "ошибка чтения " + e.getClass().getSimpleName());
                    }
                }
                final String thermalSummary = thermal;
                final MotorolaGameStateParser.Snapshot state = snapshot;
                final String finalAccess = access;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || isFinishing() || request != requestSerial) return;
                        btnRefresh.setEnabled(true);
                        btnRefresh.setText("Обновить состояние");
                        showState(state, finalAccess);
                        tvThermal.setText(thermalSummary);
                    }
                });
            }
        }, "MotorolaGameReadback").start();
    }

    private void showState(MotorolaGameStateParser.Snapshot s, String access) {
        lastSnapshot = s;
        tvAccess.setText(access + "\n" +
            MotorolaTouchRoute.accessSummary(
                MotorolaDirectAccessGate.isBlocked(this)));
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
        boolean ownBinderMissing = MotorolaDirectAccessGate.isBlocked(this);
        btnDirectRetry.setVisibility(ownBinderMissing ? View.VISIBLE : View.GONE);
        btnTouchSet.setText(ownBinderMissing
            ? MotorolaTouchRoute.actionLabel(true, s.ownTouchRequested)
            : s.readable && s.touchServiceConnected
                ? MotorolaTouchRoute.actionLabel(false, s.ownTouchRequested)
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
        if (MotorolaTouchRoute.useOfficialGameTime(
                MotorolaDirectAccessGate.isBlocked(this))) {
            notifyAction("Shizuku читает GameTime, но прямой Binder недоступен " +
                "нашему APK. Открываю штатный игровой помощник; включение " +
                "Touch Boost зависит от его настроек.");
            openGameTime();
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

    private void resetDirectAccessGate() {
        MotorolaDirectAccessGate.reset(this);
        notifyAction("Блокировка прямого API сброшена по твоему запросу. " +
            "Следующее нажатие Touch Boost снова попробует штатный Motorola API. " +
            "Это не выдаёт системные разрешения.");
        updateControls(lastSnapshot != null
            ? lastSnapshot
            : MotorolaGameStateParser.unavailable("Нет свежих данных"));
    }

    private void runControl(final boolean bypass, final boolean enable,
                            final String gamePackage) {
        if (controlBusy || destroyed) return;
        // Synchronously persist an operation marker BEFORE calling any OEM
        // code. Native signals/process kills cannot be caught by Java.
        final String operation = (bypass ? "Bypass" : "Touch Boost") +
            (enable ? " ВКЛ" : " ВЫКЛ");
        if (!MotorolaControlTrace.begin(this, operation)) {
            notifyAction("Не удалось сохранить диагностику операции. " +
                "Для безопасности запрос Motorola отменён.");
            return;
        }
        controlBusy = true;
        final int request = ++requestSerial;
        btnDpsSet.setEnabled(false);
        btnTouchSet.setEnabled(false);
        btnRefresh.setEnabled(false);
        tvControlFeedback.setText("Передача команды Motorola и проверка результата...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    MotoGameController.Result call = bypass
                        ? MotoGameController.requestBypass(MotorolaGameActivity.this, enable)
                        : MotoGameController.requestTouch(
                            MotorolaGameActivity.this, enable, gamePackage);
                    MotorolaGameStateParser.Snapshot observed = null;
                    if (ShellHelper.isShizukuPermissionGranted() ||
                            ShellHelper.isRootAvailable()) {
                        for (int attempt = 0; attempt < (call.sent ? 3 : 1); attempt++) {
                            if (attempt > 0) {
                                try { Thread.sleep(450); }
                                catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            ShellHelper.CommandResult data =
                                ShellHelper.runCommandWithResult(
                                    MotorolaGameActivity.this, "dumpsys moto_extend_service");
                            observed = data.success
                                ? MotorolaGameStateParser.parse(data.detail)
                                : MotorolaGameStateParser.unavailable(
                                    "Нет доступа к системной проверке");
                            if (verified(observed, bypass, enable, gamePackage)) break;
                        }
                    }
                    final MotorolaGameStateParser.Snapshot finalObserved = observed;
                    final boolean confirmed = call.sent &&
                        verified(observed, bypass, enable, gamePackage);
                    final String feedback = !call.sent
                        ? call.reason
                        : confirmed
                            ? (bypass
                                ? "Bypass: аппаратное состояние подтверждено"
                                : "Touch Boost: подтверждён запрос Lenovo Y70 " +
                                  "в Motorola; частота опроса не измерялась")
                            : "Команда отправлена, но результат не подтверждён " +
                              "системным сервисом. Фактическое состояние неизвестно.";
                    MotorolaControlTrace.finish(MotorolaGameActivity.this, feedback);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed || isFinishing() ||
                                    request != requestSerial) return;
                            controlBusy = false;
                            btnRefresh.setEnabled(true);
                            if (finalObserved != null && finalObserved.readable) {
                                showState(finalObserved, "Состояние перечитано из Motorola");
                            } else if (lastSnapshot != null) {
                                updateControls(lastSnapshot);
                            }
                            notifyAction(feedback);
                        }
                    });
                } catch (Throwable t) {
                    android.util.Log.e("MotoGameControl", "Motorola operation interrupted", t);
                    final String feedback =
                        "Вызов Motorola/проверка состояния прерваны: " +
                        t.getClass().getSimpleName() + ". " +
                        (t.getMessage() == null ? "" : shortText(t.getMessage(), 140)) +
                        " Фактический результат не подтверждён.";
                    MotorolaControlTrace.finish(MotorolaGameActivity.this, feedback);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed || isFinishing() ||
                                    request != requestSerial) return;
                            controlBusy = false;
                            btnRefresh.setEnabled(true);
                            if (lastSnapshot != null) updateControls(lastSnapshot);
                            notifyAction(feedback);
                        }
                    });
                }
            }
        }, "MotoGameControl").start();
    }

    private void updateCaptureStatus() {
        boolean active = MotorolaCaptureStore.isActive(this);
        btnCaptureStart.setEnabled(!active);
        btnCaptureStop.setEnabled(active);
        tvCaptureStatus.setText(MotorolaCaptureStore.status(this) +
            "\nСеанс сохраняется локально; добавляется в «Скопировать диагностику».");
    }

    private void startGameCapture() {
        if (controlBusy) {
            notifyAction("Дождись завершения текущего запроса Motorola.");
            return;
        }
        if (MotorolaCaptureStore.isActive(this)) {
            notifyAction("Запись игрового сеанса уже запущена.");
            return;
        }
        if (!ShellHelper.isShizukuPermissionGranted() &&
                !ShellHelper.isRootAvailable()) {
            notifyAction("Автодиагностика требует разрешение Shizuku. " +
                "Выдай его на этом экране и попробуй снова.");
            return;
        }
        final Intent game;
        try {
            game = getPackageManager().getLaunchIntentForPackage(
                MotorolaCaptureFormat.GAME_PACKAGE);
        } catch (RuntimeException e) {
            notifyAction("Не удалось проверить запуск Wild Rift: " +
                e.getClass().getSimpleName());
            return;
        }
        if (game == null) {
            notifyAction("Wild Rift не найден или не разрешён к запуску.");
            return;
        }
        try {
            Intent record = new Intent(this, MotorolaGameCaptureService.class)
                .setAction(MotorolaGameCaptureService.ACTION_START);
            startForegroundService(record);
            // Foreground recording starts before the game comes to the front.
            startActivity(game);
            updateCaptureStatus();
        } catch (RuntimeException e) {
            try {
                startService(new Intent(this, MotorolaGameCaptureService.class)
                    .setAction(MotorolaGameCaptureService.ACTION_STOP));
            } catch (RuntimeException ignored) { }
            notifyAction("Не удалось запустить запись/игру: " +
                e.getClass().getSimpleName());
        }
    }

    private void stopGameCapture() {
        if (!MotorolaCaptureStore.isActive(this)) {
            updateCaptureStatus();
            return;
        }
        try {
            startService(new Intent(this, MotorolaGameCaptureService.class)
                .setAction(MotorolaGameCaptureService.ACTION_STOP));
            tvCaptureStatus.setText("Остановка записи запрошена...");
        } catch (RuntimeException e) {
            tvCaptureStatus.setText("Не удалось остановить запись: " +
                e.getClass().getSimpleName());
        }
    }

    private void copyGameCapture() {
        String report = MotorolaCaptureStore.report(this);
        if (report == null || report.trim().isEmpty()) {
            notifyAction("Запись ещё не собрана: запусти тест игрового сеанса.");
            return;
        }
        ShellHelper.copyToClipboard(this, report, "Legion Y70 game capture");
        Toast.makeText(this, "Запись игрового сеанса скопирована",
            Toast.LENGTH_SHORT).show();
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
        // Never block the main thread with logcat. The user explicitly copies
        // the report, and no crash data is uploaded by the app.
        final String status =
            "LenovoMotorolovichY70 · Motorola control diagnostics\n" +
            "App: " + getPackageName() + "\n" +
            "Shizuku: " + ShellHelper.isShizukuPermissionGranted() + "\n" +
            "Motorola DPS permission: " + hasDpsPermission() + "\n" +
            "Direct Motorola Binder: " +
                MotorolaTouchRoute.accessSummary(
                    MotorolaDirectAccessGate.isBlocked(this)) + "\n" +
            "Service: " + tvAccess.getText() + "\n" +
            "Last action: " + tvControlFeedback.getText() + "\n" +
            "Pending OEM operation: " + MotorolaControlTrace.pending(this) + "\n" +
            "Last completed: " + MotorolaControlTrace.lastCompleted(this) + "\n" +
            "DPS: " + tvDps.getText() + "\n" +
            "Requests: " + tvDpsRequests.getText() + "\n" +
            "Touch: " + tvTouch.getText() + "\n" +
            "Mode: " + tvMode.getText() + "\n" +
            "Capabilities: " + tvCapabilities.getText() +
            "\nThermal: " + tvThermal.getText() +
            "\n\nGame session:\n" + MotorolaCaptureStore.report(this);
        btnCopyReport.setEnabled(false);
        btnCopyReport.setText("Сбор отчёта...");
        new Thread(new Runnable() {
            @Override public void run() {
                String excerpt = "Журнал недоступен";
                try {
                    if (ShellHelper.isShizukuPermissionGranted() ||
                            ShellHelper.isRootAvailable()) {
                        String raw = ShellHelper.runCommandWithOutput(
                            MotorolaGameActivity.this,
                            "logcat -d -b crash -v threadtime -t 600");
                        excerpt = relevantCrashExcerpt(raw);
                    }
                } catch (Throwable e) {
                    excerpt = "Не удалось прочитать журнал: " +
                        e.getClass().getSimpleName();
                }
                final String report = status + "\n\nLast relevant crash:\n" + excerpt;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || isFinishing()) return;
                        btnCopyReport.setEnabled(true);
                        btnCopyReport.setText("Скопировать диагностику");
                        ShellHelper.copyToClipboard(
                            MotorolaGameActivity.this, report, "Motorola diagnostics");
                        Toast.makeText(MotorolaGameActivity.this,
                            "Отчёт и последняя авария скопированы. Вставь текст в чат.",
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "MotoGameCrashReport").start();
    }

    private static String relevantCrashExcerpt(String raw) {
        return MotorolaCrashLogFilter.relevantCrashExcerpt(raw);
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
