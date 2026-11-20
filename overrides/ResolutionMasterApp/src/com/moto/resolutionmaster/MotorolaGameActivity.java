package com.moto.resolutionmaster;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/**
 * Read-only GameTime hardware diagnostics for the Lenovo Y70 Motorola ROM.
 * Commands run under the user's existing Shizuku authorization, on a worker.
 * No raw Binder writes, no sysfs edits, no signature permission impersonation.
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
            "\nИгра: " + (s.gamePackage == null ? "не определена" : s.gamePackage));
        tvCapabilities.setText(
            "Интерфейс Goodix/Motorola: " +
                (s.touchServiceConnected ? "подключён" : "не подтверждён") +
            "\nЧастота отчётов (AIDL): " + truth(s.reportRateSupported) +
            "\nЧастота опроса (AIDL): " + truth(s.sampleRateSupported) +
            "\nЧувствительность нажатий: " + truth(s.tapSensitivitySupported) +
            "\nОтзывчивость свайпов: " + truth(s.swipeResponsivenessSupported) +
            "\nПоддержка API не означает измеренную частоту или задержку.");
    }

    private static String truth(Boolean state) {
        return state == null ? "неизвестно" : state ? "поддерживается / да" : "нет";
    }

    private static String shortText(String input, int limit) {
        String compact = input.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > limit ? compact.substring(0, limit) + "…" : compact;
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
