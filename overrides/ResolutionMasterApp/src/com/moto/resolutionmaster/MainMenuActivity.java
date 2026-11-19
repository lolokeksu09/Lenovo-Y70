package com.moto.resolutionmaster;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainMenuActivity extends Activity {

    private TextView tvMenuRes;
    private TextView tvMenuDpi;
    private TextView tvMenuFps;
    private TextView tvMenuShizuku;
    private TextView tvMenuSecure;
    private TextView tvMenuTouchStatus;

    private TextView tvMenuTouchSpeedVal;
    private SeekBar sbMenuTouchSpeed;
    private Button btnMenuTouchStock;
    private Button btnMenuTouchGame;
    private Button btnMenuTouchTurbo;
    private Button btnMenuApplyTurbo;
    private Button btnMenuResetStock;
    private TextView tvMenuAnimSpeedVal;
    private Button btnMenuAnimBoost;
    private Button btnMenuAnimStock;

    private TextView tvNfcStatus;
    private Button btnInstallNfc;
    private Button btnExportNfc;
    private Button btnUninstallNfc;

    // Eye Care Controls
    private Button btnToggleDcDimming;
    private Button btnToggleNightLight;
    private Button btnToggleExtraDim;

    // Anti-Black Smearing Submenu Controls
    private Button btnToggleSmearingMenu;
    private View layoutSmearingSubmenu;
    private boolean isSmearingMenuExpanded = false;
    private TextView tvBlackFloorVal;
    private SeekBar sbBlackFloorLevel;
    private Button btnBfLow;
    private Button btnBfMed;
    private Button btnBfHigh;
    private Button btnBlackFloorToggle;
    private Button btnSmearingCombo;
    private TextView tvExtraDimLevelVal;
    private SeekBar sbExtraDimLevel;
    private Button btnBright25;
    private Button btnBright30;
    private Button btnBright35;
    private Button btnSmearing120Hz;
    private Button btnSmearing144Hz;
    private Button btnSmearingDc;

    // Audio Controls
    private Button btnAudioBtAbsVol;
    private Button btnAudioSafeVol;
    private TextView tvMediaVolumeVal;
    private SeekBar sbMediaVolume;
    private Button btnOpenMultiVolume;
    private Button btnOpenDolby;

    // Power & Bypass Controls
    private TextView tvBatteryStatus;
    private Button btnToggleBypassCharging;
    private Button btnTogglePowerSaver;
    private Button btnOpenBatteryCare;

    // Network Speed Status Bar Controls
    private static final int REQ_POST_NOTIFICATIONS = 1028;
    private Button btnToggleNetSpeed;
    private Button btnNetSpeedHideIdle;
    private Button btnNetSpeedAutostart;
    private Button btnNetSpeedIconMode;
    private View layoutNetSpeedOffset;
    private TextView tvNetSpeedOffsetVal;
    private SeekBar sbNetSpeedOffset;
    private Button btnNetSpeedUnits;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final ContentObserver mSettingsObserver =
        new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                TouchSpeedHelper.invalidateCache();
                updateStatus();
            }
        };

    private final Shizuku.OnBinderReceivedListener mBinderReceivedListener =
        new Shizuku.OnBinderReceivedListener() {
            @Override
            public void onBinderReceived() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        autoGrantIfPossible();
                        updateStatus();
                    }
                });
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            mLastDensityDpi = getResources().getConfiguration().densityDpi;
        } catch (Throwable ignored) {}
        DisplayManagerHelper.bypassHiddenApi(this);
        setContentView(R.layout.activity_main_menu);

        tvMenuRes = findViewById(R.id.tv_menu_res);
        tvMenuDpi = findViewById(R.id.tv_menu_dpi);
        tvMenuFps = findViewById(R.id.tv_menu_fps);
        tvMenuShizuku = findViewById(R.id.tv_menu_shizuku);
        tvMenuSecure = findViewById(R.id.tv_menu_secure);

        if (tvMenuSecure != null) {
            tvMenuSecure.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!ShellHelper.hasWriteSecureSettings(MainMenuActivity.this)) {
                        showSecureSettingsDialog();
                    } else {
                        Toast.makeText(MainMenuActivity.this, "Разрешение WRITE_SECURE_SETTINGS уже активно! Быстрые тайлы работают.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        View cardDisplay = findViewById(R.id.card_nav_display);
        Button btnDisplay = findViewById(R.id.btn_open_display);

        View.OnClickListener openDisplayListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainMenuActivity.this, MainActivity.class);
                startActivity(intent);
            }
        };
        if (cardDisplay != null) cardDisplay.setOnClickListener(openDisplayListener);
        if (btnDisplay != null) btnDisplay.setOnClickListener(openDisplayListener);

        View cardDebloat = findViewById(R.id.card_nav_debloat);
        Button btnDebloat = findViewById(R.id.btn_open_debloat);

        View.OnClickListener openDebloatListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainMenuActivity.this, DebloatActivity.class);
                startActivity(intent);
            }
        };
        if (cardDebloat != null) cardDebloat.setOnClickListener(openDebloatListener);
        if (btnDebloat != null) btnDebloat.setOnClickListener(openDebloatListener);

        // NFC Fix Card Init
        tvNfcStatus = findViewById(R.id.tv_nfc_status);
        btnInstallNfc = findViewById(R.id.btn_install_nfc_module);
        btnExportNfc = findViewById(R.id.btn_export_nfc_zip);
        btnUninstallNfc = findViewById(R.id.btn_uninstall_nfc_module);

        if (tvNfcStatus != null) {
            tvNfcStatus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvNfcStatus.setText("Маршруты NFC: Проверка статуса...");
                    tvNfcStatus.setTextColor(0xFFF59E0B);
                    updateNfcStatusUI();
                }
            });
        }

        if (btnInstallNfc != null) {
            btnInstallNfc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!ShellHelper.isShizukuPermissionGranted() && !ShellHelper.isRootAvailable()) {
                        new AlertDialog.Builder(MainMenuActivity.this)
                            .setTitle("Требуется Shizuku или Root")
                            .setMessage("Для активации фикса NFC выберите удобный вариант:\n\n" +
                                "1. Shizuku (без ПК) — активируйте Shizuku через «Беспроводную отладку», и фикс применится в 1 клик прямо на телефоне.\n\n" +
                                "2. Root — предоставьте Root-доступ приложению.\n\n" +
                                "3. Либо нажмите «📦 Экспорт ZIP» и прошейте архив через Magisk / KernelSU.")
                            .setPositiveButton("Открыть Shizuku", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ShellHelper.openShizuku(MainMenuActivity.this);
                                }
                            })
                            .setNegativeButton("Закрыть", null)
                            .show();
                        return;
                    }

                    if (tvNfcStatus != null) {
                        tvNfcStatus.setText("Маршруты NFC: Применение фикса...");
                        tvNfcStatus.setTextColor(0xFFF59E0B);
                    }
                    if (btnInstallNfc != null) {
                        btnInstallNfc.setEnabled(false);
                        btnInstallNfc.setText("⏳ Применение...");
                    }
                    if (btnUninstallNfc != null) btnUninstallNfc.setEnabled(false);

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final boolean ok = NfcFixHelper.applyMasterFix(MainMenuActivity.this);
                            final NfcFixHelper.RoutingInfo updatedInfo = NfcFixHelper.getLiveRoutingInfo(MainMenuActivity.this);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    applyNfcInfoToViews(updatedInfo);
                                    if (ok || updatedInfo.status == NfcFixHelper.NfcStatus.FIXED_100_PERCENT) {
                                        new AlertDialog.Builder(MainMenuActivity.this)
                                            .setTitle("Фикс NFC успешно применён! ⚡")
                                            .setMessage("Таблица маршрутизации контроллера перенаправлена на Host Android (DH, DH, DH):\n\n" +
                                                "• Empty AID ➔ 0x00 (DH)\n" +
                                                "• PROTOCOL ISO-DEP ➔ 0x00 (DH)\n" +
                                                "• TECHNOLOGY A & B ➔ 0x00 (DH)\n\n" +
                                                "Применение NFC-маршрутов не гарантирует оплату: проверьте на реальном терминале.\n\n" +
                                                "Проверьте состояние NFC и совместимость с вашим приложением оплаты.")
                                            .setPositiveButton("Отлично", null)
                                            .show();
                                    } else {
                                        Toast.makeText(MainMenuActivity.this, "Ошибка применения фикса (проверьте права Shizuku/Root)", Toast.LENGTH_LONG).show();
                                    }
                                }
                            });

                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    updateNfcStatusUI();
                                }
                            }, 1000);
                        }
                    }).start();
                }
            });
        }

        if (btnExportNfc != null) {
            btnExportNfc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    java.io.File zip = NfcFixHelper.exportModuleZip(MainMenuActivity.this);
                    if (zip != null && zip.exists()) {
                        new AlertDialog.Builder(MainMenuActivity.this)
                            .setTitle("ZIP-модуль создан! 📦")
                            .setMessage("Файл успешно сохранен в папку Загрузки:\n\n" + zip.getAbsolutePath() + "\n\nКак прошить:\n1. Откройте приложение Magisk (или KernelSU / APatch)\n2. Перейдите во вкладку «Модули»\n3. Нажмите «Установить из хранилища» и выберите этот ZIP\n4. Перезагрузите устройство.")
                            .setPositiveButton("Отлично", null)
                            .show();
                    } else {
                        Toast.makeText(MainMenuActivity.this, "Не удалось сохранить ZIP-архив", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnUninstallNfc != null) {
            btnUninstallNfc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showNfcRollbackDialog();
                }
            });
        }

        TextView tvNfcAuthor = findViewById(R.id.tv_nfc_author);
        if (tvNfcAuthor != null) {
            tvNfcAuthor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ShellHelper.openUrl(MainMenuActivity.this, "https://4pda.to/forum/index.php?showtopic=1121132&view=findpost&p=146040708");
                }
            });
        }

        TextView tvAppAuthor = findViewById(R.id.tv_app_author);
        View layoutFooterAuthor = findViewById(R.id.layout_footer_author);
        View.OnClickListener authorClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ShellHelper.openUrl(MainMenuActivity.this, "https://4pda.to/forum/index.php?showtopic=1121132&view=findpost&p=145909028");
            }
        };
        if (tvAppAuthor != null) tvAppAuthor.setOnClickListener(authorClickListener);
        if (layoutFooterAuthor != null) layoutFooterAuthor.setOnClickListener(authorClickListener);

        setupTouchControls();
        setupEyeCareControls();
        setupAudioControls();
        setupPowerControls();
        setupNetworkSpeedControls();

        try {
            getContentResolver().registerContentObserver(
                Settings.System.getUriFor("peak_refresh_rate"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.System.getUriFor("min_refresh_rate"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.System.getUriFor("pointer_speed"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("display_size_forced"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("display_density_forced"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("night_display_activated"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("reduce_bright_colors_activated"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("reduce_bright_colors_level"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("low_power"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(AudioHelper.SETTING_DISABLE_ABSOLUTE_VOLUME), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(AudioHelper.SETTING_SAFE_VOLUME_STATE), false, mSettingsObserver);
        } catch (Throwable ignored) {}

        try {
            Shizuku.addBinderReceivedListenerSticky(mBinderReceivedListener);
        } catch (Throwable ignored) {}

        updateStatus();
    }

    private int mLastDensityDpi = -1;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig != null) {
            int currentDpi = newConfig.densityDpi;
            if (mLastDensityDpi != -1 && currentDpi != mLastDensityDpi) {
                mLastDensityDpi = currentDpi;
                recreate();
                return;
            }
            mLastDensityDpi = currentDpi;
        }
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoGrantIfPossible();
        TouchSpeedHelper.invalidateCache();
        updateStatus();
    }

    private void autoGrantIfPossible() {
        if (!ShellHelper.hasWriteSecureSettings(this)) {
            if (ShellHelper.isShizukuPermissionGranted() || ShellHelper.isRootAvailable()) {
                boolean ok = ShellHelper.grantWriteSecureSettings(this);
                if (ok) {
                    Toast.makeText(this, "Разрешение WRITE_SECURE_SETTINGS выдано автоматически!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void updateStatus() {
        Point p = DisplayManagerHelper.getCurrentResolution(this);
        int dpi = DisplayManagerHelper.getCurrentDensity(this);
        float configuredFps = DisplayManagerHelper.getConfiguredRefreshRate(this);
        float activeFps = DisplayManagerHelper.getCurrentRefreshRate(this);

        if (tvMenuRes != null) {
            tvMenuRes.setText(String.format("%d × %d", p.x, p.y));
        }
        if (tvMenuDpi != null) {
            tvMenuDpi.setText(dpi + " DPI");
        }
        if (tvMenuFps != null) {
            if (configuredFps == DisplayManagerHelper.HZ_AUTO) {
                tvMenuFps.setText(Math.round(activeFps) + " Гц (Авто)");
            } else {
                tvMenuFps.setText(Math.round(configuredFps) + " Гц (Фикс.)");
            }
        }

        if (tvMenuShizuku != null) {
            if (ShellHelper.isShizukuPermissionGranted()) {
                tvMenuShizuku.setText("Shizuku: Подключен ✓");
                tvMenuShizuku.setTextColor(0xFF10B981); // accent_green
            } else if (ShellHelper.isShizukuRunning()) {
                tvMenuShizuku.setText("Shizuku: Запущен (нужен доступ)");
                tvMenuShizuku.setTextColor(0xFFF59E0B); // accent_yellow
            } else if (ShellHelper.isRootAvailable()) {
                tvMenuShizuku.setText("Root: Доступен ✓");
                tvMenuShizuku.setTextColor(0xFF10B981);
            } else if (ShellHelper.isShizukuInstalled(this)) {
                tvMenuShizuku.setText("Shizuku: Не запущен");
                tvMenuShizuku.setTextColor(0xFF9E9E9E);
            } else {
                tvMenuShizuku.setText("Shizuku: Не установлен");
                tvMenuShizuku.setTextColor(0xFF9E9E9E);
            }
        }

        if (tvMenuSecure != null) {
            boolean hasSecure = ShellHelper.hasWriteSecureSettings(this);
            if (hasSecure) {
                tvMenuSecure.setText("Тайлы шторки: Активны ✓");
                tvMenuSecure.setTextColor(0xFF10B981); // accent_green
            } else {
                tvMenuSecure.setText("⚡ Включить тайлы шторки (Нажмите)");
                tvMenuSecure.setTextColor(0xFFF59E0B); // accent_yellow
            }
        }

        updateNfcStatusUI();

        int curTouch = TouchSpeedHelper.getPointerSpeed(this);
        updateTouchSpeedDisplay(curTouch);
        updateAnimSpeedDisplay();

        updateEyeCareUI();
        updateAudioUI();
        updatePowerUI();
        updateNetworkSpeedUI();
    }

    private void setupTouchControls() {
        tvMenuTouchStatus = findViewById(R.id.tv_menu_touch_status);
        tvMenuTouchSpeedVal = findViewById(R.id.tv_menu_touch_speed_val);
        sbMenuTouchSpeed = findViewById(R.id.sb_menu_touch_speed);
        btnMenuTouchStock = findViewById(R.id.btn_menu_touch_stock);
        btnMenuTouchGame = findViewById(R.id.btn_menu_touch_game);
        btnMenuTouchTurbo = findViewById(R.id.btn_menu_touch_turbo);
        btnMenuApplyTurbo = findViewById(R.id.btn_menu_apply_turbo);
        tvMenuAnimSpeedVal = findViewById(R.id.tv_menu_anim_speed_val);
        btnMenuAnimBoost = findViewById(R.id.btn_menu_anim_boost);
        btnMenuAnimStock = findViewById(R.id.btn_menu_anim_stock);

        if (sbMenuTouchSpeed != null) {
            sbMenuTouchSpeed.setMax(14);
            int curSpeed = TouchSpeedHelper.getPointerSpeed(this);
            sbMenuTouchSpeed.setProgress(TouchSpeedHelper.speedToProgress(curSpeed));
            updateTouchSpeedDisplay(curSpeed);

            sbMenuTouchSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int speed = TouchSpeedHelper.progressToSpeed(progress);
                        updateTouchSpeedDisplay(speed);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int speed = TouchSpeedHelper.progressToSpeed(seekBar.getProgress());
                    applyTouchSpeed(speed, false);
                }
            });
        }

        btnMenuResetStock = findViewById(R.id.btn_menu_reset_stock);

        if (btnMenuTouchStock != null) {
            btnMenuTouchStock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyTouchSpeed(0, true);
                }
            });
        }

        if (btnMenuTouchGame != null) {
            btnMenuTouchGame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyTouchSpeed(5, true);
                }
            });
        }

        if (btnMenuTouchTurbo != null) {
            btnMenuTouchTurbo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyTouchSpeed(7, true);
                }
            });
        }

        if (btnMenuApplyTurbo != null) {
            btnMenuApplyTurbo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean ok = TouchSpeedHelper.enableFullTurboMode(MainMenuActivity.this);
                    if (sbMenuTouchSpeed != null) {
                        sbMenuTouchSpeed.setProgress(TouchSpeedHelper.speedToProgress(TouchSpeedHelper.getPointerSpeed(MainMenuActivity.this)));
                    }
                    updateStatus();
                    if (ok) {
                        Toast.makeText(MainMenuActivity.this, "Запрошены 144 Гц и анимации 0.5x. Проверьте фактический режим экрана.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainMenuActivity.this, "Ошибка применения Турбо-режима (проверьте Shizuku/Root)", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnMenuResetStock != null) {
            btnMenuResetStock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean ok = TouchSpeedHelper.resetToStock(MainMenuActivity.this);
                    if (sbMenuTouchSpeed != null) {
                        sbMenuTouchSpeed.setProgress(TouchSpeedHelper.speedToProgress(0));
                    }
                    updateStatus();
                    if (ok) {
                        Toast.makeText(MainMenuActivity.this, "🔄 Настройки сброшены к стандартным заводским!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainMenuActivity.this, "Ошибка сброса настроек", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnMenuAnimBoost != null) {
            btnMenuAnimBoost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyAnimScale(0.5f);
                }
            });
        }

        if (btnMenuAnimStock != null) {
            btnMenuAnimStock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyAnimScale(1.0f);
                }
            });
        }

        updateAnimSpeedDisplay();
    }

    private void updateTouchSpeedDisplay(int speed) {
        String label = TouchSpeedHelper.getSpeedLabel(speed);
        if (tvMenuTouchSpeedVal != null) {
            tvMenuTouchSpeedVal.setText(label);
        }
        if (tvMenuTouchStatus != null) {
            tvMenuTouchStatus.setText("Скорость указателя: " + label);
        }
    }

    private void applyTouchSpeed(int speed, boolean updateSeekBar) {
        boolean ok = TouchSpeedHelper.setPointerSpeed(this, speed);
        if (updateSeekBar && sbMenuTouchSpeed != null) {
            sbMenuTouchSpeed.setProgress(TouchSpeedHelper.speedToProgress(speed));
        }
        updateTouchSpeedDisplay(TouchSpeedHelper.getPointerSpeed(this));
        if (ok) {
            Toast.makeText(this, "Скорость указателя: " + TouchSpeedHelper.getSpeedLabel(speed), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Не удалось изменить скорость указателя (проверьте Shizuku/Root)", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAnimSpeedDisplay() {
        float scale = TouchSpeedHelper.getAnimationScale(this);
        if (tvMenuAnimSpeedVal != null) {
            tvMenuAnimSpeedVal.setText(TouchSpeedHelper.getAnimationScaleLabel(scale));
        }
    }

    private void applyAnimScale(float scale) {
        boolean ok = TouchSpeedHelper.setAnimationScales(this, scale);
        updateAnimSpeedDisplay();
        if (ok) {
            Toast.makeText(this, "Масштаб анимаций: " + TouchSpeedHelper.getAnimationScaleLabel(scale), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Не удалось изменить масштаб (проверьте Shizuku/Root)", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Eye Care Section ====================
    private void setupEyeCareControls() {
        btnToggleDcDimming = findViewById(R.id.btn_toggle_dc_dimming);
        btnToggleNightLight = findViewById(R.id.btn_toggle_night_light);
        btnToggleExtraDim = findViewById(R.id.btn_toggle_extra_dim);

        if (btnToggleDcDimming != null) {
            btnToggleDcDimming.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            EyeCareHelper.toggleDcDimming(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateEyeCareUI();
                                    boolean active = EyeCareHelper.isDcDimmingActive(MainMenuActivity.this);
                                    Toast.makeText(MainMenuActivity.this, "DC Dimming (Анти-ШИМ): " + (active ? "Включен 👁️" : "Выключен"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (btnToggleNightLight != null) {
            btnToggleNightLight.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            EyeCareHelper.toggleNightLight(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateEyeCareUI();
                                    boolean active = EyeCareHelper.isNightLightActive(MainMenuActivity.this);
                                    Toast.makeText(MainMenuActivity.this, "Ночной режим: " + (active ? "Включен 🌙" : "Выключен"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (btnToggleExtraDim != null) {
            btnToggleExtraDim.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            EyeCareHelper.toggleExtraDim(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateEyeCareUI();
                                    boolean active = EyeCareHelper.isExtraDimActive(MainMenuActivity.this);
                                    Toast.makeText(MainMenuActivity.this, "Экстра-затемнение: " + (active ? "Включено 🌑" : "Выключено"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        // Anti-Black Smearing Submenu Init
        btnToggleSmearingMenu = findViewById(R.id.btn_toggle_smearing_menu);
        layoutSmearingSubmenu = findViewById(R.id.layout_smearing_submenu);
        tvBlackFloorVal = findViewById(R.id.tv_black_floor_val);
        sbBlackFloorLevel = findViewById(R.id.sb_black_floor_level);
        btnBfLow = findViewById(R.id.btn_bf_low);
        btnBfMed = findViewById(R.id.btn_bf_med);
        btnBfHigh = findViewById(R.id.btn_bf_high);
        btnBlackFloorToggle = findViewById(R.id.btn_black_floor_toggle);
        btnSmearingCombo = findViewById(R.id.btn_smearing_combo);
        tvExtraDimLevelVal = findViewById(R.id.tv_extra_dim_level_val);
        sbExtraDimLevel = findViewById(R.id.sb_extra_dim_level);
        btnBright25 = findViewById(R.id.btn_bright_25);
        btnBright30 = findViewById(R.id.btn_bright_30);
        btnBright35 = findViewById(R.id.btn_bright_35);
        btnSmearing120Hz = findViewById(R.id.btn_smearing_120hz);
        btnSmearing144Hz = findViewById(R.id.btn_smearing_144hz);
        btnSmearingDc = findViewById(R.id.btn_smearing_dc);

        if (btnToggleSmearingMenu != null && layoutSmearingSubmenu != null) {
            btnToggleSmearingMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isSmearingMenuExpanded = !isSmearingMenuExpanded;
                    layoutSmearingSubmenu.setVisibility(isSmearingMenuExpanded ? View.VISIBLE : View.GONE);
                    updateEyeCareUI();
                }
            });
        }

        if (sbBlackFloorLevel != null) {
            sbBlackFloorLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && tvBlackFloorVal != null) {
                        tvBlackFloorVal.setText(EyeCareHelper.formatBlackFloorLabel(progress > 0, progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int lvl = seekBar.getProgress();
                    if (lvl <= 0) {
                        applyBlackFloorAction(false, 0);
                    } else {
                        applyBlackFloorAction(true, lvl);
                    }
                }
            });
        }

        if (btnBfLow != null) {
            btnBfLow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyBlackFloorAction(true, 3);
                }
            });
        }

        if (btnBfMed != null) {
            btnBfMed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyBlackFloorAction(true, 4);
                }
            });
        }

        if (btnBfHigh != null) {
            btnBfHigh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyBlackFloorAction(true, 6);
                }
            });
        }

        if (btnBlackFloorToggle != null) {
            btnBlackFloorToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean active = EyeCareHelper.isBlackFloorActive(MainMenuActivity.this);
                    int lvl = EyeCareHelper.getBlackFloorLevel(MainMenuActivity.this);
                    if (sbBlackFloorLevel != null && sbBlackFloorLevel.getProgress() > 0) {
                        lvl = sbBlackFloorLevel.getProgress();
                    }
                    applyBlackFloorAction(!active, lvl);
                }
            });
        }

        if (btnSmearingCombo != null) {
            btnSmearingCombo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final boolean enabled = EyeCareHelper.toggleBrightExtraDimCombo(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateEyeCareUI();
                                    Toast.makeText(MainMenuActivity.this,
                                        enabled ? "💡 Связка активна: яркость матрицы 30% + Extra Dim" : "🔄 Связка отключена, яркость восстановлена",
                                        Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (sbExtraDimLevel != null) {
            sbExtraDimLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && tvExtraDimLevelVal != null) {
                        tvExtraDimLevelVal.setText(progress + "%");
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    final int level = seekBar.getProgress();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            EyeCareHelper.setExtraDimLevel(MainMenuActivity.this, level);
                            if (!EyeCareHelper.isExtraDimActive(MainMenuActivity.this)) {
                                EyeCareHelper.setExtraDimActive(MainMenuActivity.this, true);
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateEyeCareUI();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (btnBright25 != null) {
            btnBright25.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyHardwareBrightness(25);
                }
            });
        }

        if (btnBright30 != null) {
            btnBright30.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyHardwareBrightness(30);
                }
            });
        }

        if (btnBright35 != null) {
            btnBright35.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyHardwareBrightness(35);
                }
            });
        }

        if (btnSmearing120Hz != null) {
            btnSmearing120Hz.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            DisplayManagerHelper.setRefreshRate(MainMenuActivity.this, 120.0f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateStatus();
                                    Toast.makeText(MainMenuActivity.this, "Частота зафиксирована: 120 Гц", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (btnSmearing144Hz != null) {
            btnSmearing144Hz.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            DisplayManagerHelper.setRefreshRate(MainMenuActivity.this, 144.0f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateStatus();
                                    Toast.makeText(MainMenuActivity.this, "Частота зафиксирована: 144 Гц", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (btnSmearingDc != null) {
            btnSmearingDc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            EyeCareHelper.toggleDcDimming(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateEyeCareUI();
                                    boolean active = EyeCareHelper.isDcDimmingActive(MainMenuActivity.this);
                                    Toast.makeText(MainMenuActivity.this, "DC Dimming: " + (active ? "Вкл 👁️" : "Выкл"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }
    }

    private void applyHardwareBrightness(final int percent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                EyeCareHelper.setHardwareBrightnessPercent(MainMenuActivity.this, percent);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainMenuActivity.this, "Аппаратная яркость матрицы: " + percent + "%", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void applyBlackFloorAction(final boolean enable, final int level) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int result = EyeCareHelper.applyBlackFloor(MainMenuActivity.this, enable, level);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == EyeCareHelper.RESULT_NEED_OVERLAY_PERM) {
                            new AlertDialog.Builder(MainMenuActivity.this)
                                .setTitle("Разрешение «Поверх других окон»")
                                .setMessage("Без Root-доступа подъём уровня чёрного OLED (#040404) работает через аппаратный слой SurfaceFlinger (HWC).\n\nРазрешите отображение поверх других окон (или подключите Shizuku для автоматической выдачи).")
                                .setPositiveButton("Открыть настройки", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + getPackageName()));
                                            startActivity(intent);
                                        } catch (Throwable ignored) {}
                                    }
                                })
                                .setNegativeButton("Отмена", null)
                                .show();
                        } else if (result == EyeCareHelper.RESULT_OK_ROOT) {
                            Toast.makeText(MainMenuActivity.this, "✨ Подъём чёрного активен (SurfaceFlinger ColorMatrix Root): " + EyeCareHelper.formatBlackFloorLabel(true, level), Toast.LENGTH_SHORT).show();
                        } else if (result == EyeCareHelper.RESULT_OK_OVERLAY) {
                            Toast.makeText(MainMenuActivity.this, "✨ Подъём чёрного активен (HWC Layer): " + EyeCareHelper.formatBlackFloorLabel(true, level), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainMenuActivity.this, "Подъём уровня чёрного отключён (#000000)", Toast.LENGTH_SHORT).show();
                        }
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                updateEyeCareUI();
                            }
                        }, 150);
                    }
                });
            }
        }).start();
    }

    private void updateEyeCareUI() {
        boolean dcActive = EyeCareHelper.isDcDimmingActive(this);
        if (btnToggleDcDimming != null) {
            if (dcActive) {
                btnToggleDcDimming.setText("👁️ DC Dimming (Анти-ШИМ): Включен ✓");
                btnToggleDcDimming.setBackgroundResource(R.drawable.btn_green);
            } else {
                btnToggleDcDimming.setText("👁️ DC Dimming (Анти-ШИМ): Выключен");
                btnToggleDcDimming.setBackgroundResource(R.drawable.btn_primary);
            }
        }

        boolean nightActive = EyeCareHelper.isNightLightActive(this);
        if (btnToggleNightLight != null) {
            if (nightActive) {
                btnToggleNightLight.setText("🌙 Ночной режим: Вкл ✓");
                btnToggleNightLight.setTextColor(0xFF10B981);
            } else {
                btnToggleNightLight.setText("🌙 Ночной режим: Выкл");
                btnToggleNightLight.setTextColor(0xFFF3F4F6);
            }
        }

        boolean extraDimActive = EyeCareHelper.isExtraDimActive(this);
        if (btnToggleExtraDim != null) {
            if (extraDimActive) {
                btnToggleExtraDim.setText("🌑 Экстра-затемнение: Вкл ✓");
                btnToggleExtraDim.setTextColor(0xFF10B981);
            } else {
                btnToggleExtraDim.setText("🌑 Экстра-затемнение: Выкл");
                btnToggleExtraDim.setTextColor(0xFFF3F4F6);
            }
        }

        // Update Anti-Black Smearing Submenu state
        boolean bfActive = EyeCareHelper.isBlackFloorActive(this);
        int bfLevel = EyeCareHelper.getBlackFloorLevel(this);
        boolean comboActive = EyeCareHelper.isBrightExtraDimComboActive(this);

        if (btnToggleSmearingMenu != null) {
            String arrow = isSmearingMenuExpanded ? "▲" : "▼";
            String badge = (bfActive || comboActive) ? " • Активен ✓ " : "  ";
            btnToggleSmearingMenu.setText("🌑 Анти-шлейф чёрного (Anti-Black Smearing)" + badge + arrow);
            btnToggleSmearingMenu.setTextColor((bfActive || comboActive) ? 0xFF10B981 : 0xFF8B5CF6);
        }

        if (tvBlackFloorVal != null) {
            tvBlackFloorVal.setText(EyeCareHelper.formatBlackFloorLabel(bfActive, bfLevel));
            tvBlackFloorVal.setTextColor(bfActive ? 0xFF10B981 : 0xFF8B5CF6);
        }

        if (sbBlackFloorLevel != null) {
            sbBlackFloorLevel.setProgress(bfActive ? bfLevel : 0);
        }

        if (btnBlackFloorToggle != null) {
            String hex = String.format("#%02X%02X%02X", bfLevel, bfLevel, bfLevel);
            if (bfActive) {
                btnBlackFloorToggle.setText("✨ Подъём чёрного ВКЛ (" + hex + ") • Выключить");
                btnBlackFloorToggle.setBackgroundResource(R.drawable.btn_green);
            } else {
                btnBlackFloorToggle.setText("✨ Включить подъём чёрного (" + hex + ")");
                btnBlackFloorToggle.setBackgroundResource(R.drawable.btn_purple);
            }
        }

        if (btnSmearingCombo != null) {
            if (comboActive) {
                btnSmearingCombo.setText("🔄 Выключить связку (Вернуть обычную яркость)");
                btnSmearingCombo.setBackgroundResource(R.drawable.btn_green);
            } else {
                btnSmearingCombo.setText("💡 Включить связку (Яркость 30% + Extra Dim)");
                btnSmearingCombo.setBackgroundResource(R.drawable.btn_primary);
            }
        }

        int extraDimLevel = EyeCareHelper.getExtraDimLevel(this);
        if (tvExtraDimLevelVal != null) {
            tvExtraDimLevelVal.setText(extraDimLevel + "%");
        }
        if (sbExtraDimLevel != null) {
            sbExtraDimLevel.setProgress(extraDimLevel);
        }

        float cfgHz = DisplayManagerHelper.getConfiguredRefreshRate(this);
        if (btnSmearing120Hz != null) {
            boolean is120 = Math.round(cfgHz) == 120;
            btnSmearing120Hz.setText(is120 ? "120 Гц ✓" : "120 Гц");
            btnSmearing120Hz.setTextColor(is120 ? 0xFF10B981 : 0xFFF3F4F6);
        }
        if (btnSmearing144Hz != null) {
            boolean is144 = Math.round(cfgHz) == 144;
            btnSmearing144Hz.setText(is144 ? "144 Гц ✓" : "144 Гц");
            btnSmearing144Hz.setTextColor(is144 ? 0xFF10B981 : 0xFFF3F4F6);
        }
        if (btnSmearingDc != null) {
            btnSmearingDc.setText(dcActive ? "DC Dim: Вкл ✓" : "DC Dim: Выкл");
            btnSmearingDc.setTextColor(dcActive ? 0xFF10B981 : 0xFFF3F4F6);
        }
    }

    // ==================== Audio & Bluetooth Section ====================
    private void setupAudioControls() {
        btnAudioBtAbsVol = findViewById(R.id.btn_audio_bt_abs_vol);
        btnAudioSafeVol = findViewById(R.id.btn_audio_safe_vol);
        tvMediaVolumeVal = findViewById(R.id.tv_media_volume_val);
        sbMediaVolume = findViewById(R.id.sb_media_volume);
        btnOpenMultiVolume = findViewById(R.id.btn_open_multivolume);
        btnOpenDolby = findViewById(R.id.btn_open_dolby);

        if (btnAudioBtAbsVol != null) {
            btnAudioBtAbsVol.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            AudioHelper.toggleBluetoothAbsoluteVolume(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateAudioUI();
                                    boolean disabled = AudioHelper.isBluetoothAbsoluteVolumeDisabled(MainMenuActivity.this);
                                    Toast.makeText(MainMenuActivity.this, "Раздельная громкость BT: " + (disabled ? "Включена 🎧" : "Выключена (Сток)"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (btnAudioSafeVol != null) {
            btnAudioSafeVol.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            AudioHelper.toggleSafeVolumeBypass(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateAudioUI();
                                    boolean bypassed = AudioHelper.isSafeVolumeBypassed(MainMenuActivity.this);
                                    Toast.makeText(MainMenuActivity.this, "Обход лимита громкости: " + (bypassed ? "Активен 🛡️" : "Выключен (Сток)"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (sbMediaVolume != null) {
            int curVol = AudioHelper.getMediaVolumePercent(MainMenuActivity.this);
            sbMediaVolume.setProgress(curVol);
            if (tvMediaVolumeVal != null) tvMediaVolumeVal.setText(curVol + "%");

            sbMediaVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && tvMediaVolumeVal != null) {
                        tvMediaVolumeVal.setText(progress + "%");
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    final int progress = seekBar.getProgress();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            AudioHelper.setMediaVolumePercent(MainMenuActivity.this, progress);
                        }
                    }).start();
                    if (tvMediaVolumeVal != null) tvMediaVolumeVal.setText(progress + "%");
                }
            });
        }

        if (btnOpenMultiVolume != null) {
            btnOpenMultiVolume.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AudioHelper.launchMultiVolume(MainMenuActivity.this);
                }
            });
        }

        if (btnOpenDolby != null) {
            btnOpenDolby.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AudioHelper.launchDolbyAtmos(MainMenuActivity.this);
                }
            });
        }
    }

    private void updateAudioUI() {
        boolean btDisabled = AudioHelper.isBluetoothAbsoluteVolumeDisabled(this);
        if (btnAudioBtAbsVol != null) {
            if (btDisabled) {
                btnAudioBtAbsVol.setText("🎧 Раздельная BT: Вкл ✓");
                btnAudioBtAbsVol.setTextColor(0xFF10B981);
            } else {
                btnAudioBtAbsVol.setText("🎧 Раздельная BT: Выкл");
                btnAudioBtAbsVol.setTextColor(0xFFF3F4F6);
            }
        }

        boolean safeBypassed = AudioHelper.isSafeVolumeBypassed(this);
        if (btnAudioSafeVol != null) {
            if (safeBypassed) {
                btnAudioSafeVol.setText("🛡️ Обход лимита: Вкл ✓");
                btnAudioSafeVol.setTextColor(0xFF10B981);
            } else {
                btnAudioSafeVol.setText("🛡️ Обход лимита: Выкл");
                btnAudioSafeVol.setTextColor(0xFFF3F4F6);
            }
        }

        int curVol = AudioHelper.getMediaVolumePercent(this);
        if (sbMediaVolume != null) {
            sbMediaVolume.setProgress(curVol);
        }
        if (tvMediaVolumeVal != null) {
            tvMediaVolumeVal.setText(curVol + "%");
        }
    }

    // ==================== Power & Bypass Section ====================
    private void setupPowerControls() {
        tvBatteryStatus = findViewById(R.id.tv_battery_status);
        btnToggleBypassCharging = findViewById(R.id.btn_toggle_bypass_charging);
        btnTogglePowerSaver = findViewById(R.id.btn_toggle_power_saver);
        btnOpenBatteryCare = findViewById(R.id.btn_open_battery_care);

        if (btnToggleBypassCharging != null) {
            btnToggleBypassCharging.setEnabled(false);
            btnToggleBypassCharging.setText("Bypass: аппаратная поддержка не подтверждена");
        }

        if (btnTogglePowerSaver != null) {
            btnTogglePowerSaver.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            PowerHelper.togglePowerSaveMode(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updatePowerUI();
                                    boolean cur = PowerHelper.isPowerSaveMode(MainMenuActivity.this);
                                    Toast.makeText(MainMenuActivity.this, "Энергосбережение: " + (cur ? "Включено 🔋" : "Выключено"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (btnOpenBatteryCare != null) {
            btnOpenBatteryCare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PowerHelper.launchBatteryCare(MainMenuActivity.this);
                }
            });
        }
    }

    private void updatePowerUI() {
        PowerHelper.BatteryInfo info = PowerHelper.getBatteryInfo(this);
        if (tvBatteryStatus != null) {
            String text = String.format("🔋 Батарея: %d%% • %.1f°C • %s", info.level, info.temperature, info.statusText);
            tvBatteryStatus.setText(text);
            if (info.isBypassActive) {
                tvBatteryStatus.setTextColor(0xFF8B5CF6);
            } else if (info.isCharging) {
                tvBatteryStatus.setTextColor(0xFF10B981);
            } else {
                tvBatteryStatus.setTextColor(0xFFF3F4F6);
            }
        }

        if (btnToggleBypassCharging != null) {
            btnToggleBypassCharging.setEnabled(false);
            btnToggleBypassCharging.setText("Bypass недоступен — нет проверки PMIC");
            btnToggleBypassCharging.setBackgroundResource(R.drawable.btn_chip);
        }

        boolean powerSave = PowerHelper.isPowerSaveMode(this);
        if (btnTogglePowerSaver != null) {
            if (powerSave) {
                btnTogglePowerSaver.setText("🔋 Энергосбережение: Вкл ✓");
                btnTogglePowerSaver.setTextColor(0xFF10B981);
            } else {
                btnTogglePowerSaver.setText("🔋 Энергосбережение: Выкл");
                btnTogglePowerSaver.setTextColor(0xFFF3F4F6);
            }
        }
    }

    // ==================== Network Speed Status Bar Section ====================
    private void setupNetworkSpeedControls() {
        btnToggleNetSpeed = findViewById(R.id.btn_toggle_net_speed);
        btnNetSpeedHideIdle = findViewById(R.id.btn_net_speed_hide_idle);
        btnNetSpeedAutostart = findViewById(R.id.btn_net_speed_autostart);
        btnNetSpeedIconMode = findViewById(R.id.btn_net_speed_icon_mode);
        layoutNetSpeedOffset = findViewById(R.id.layout_net_speed_offset);
        tvNetSpeedOffsetVal = findViewById(R.id.tv_net_speed_offset_val);
        sbNetSpeedOffset = findViewById(R.id.sb_net_speed_offset);
        btnNetSpeedUnits = findViewById(R.id.btn_net_speed_units);

        if (btnToggleNetSpeed != null) {
            btnToggleNetSpeed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean currentlyActive = NetworkSpeedHelper.isActiveNow(MainMenuActivity.this);
                    applyNetSpeedToggle(!currentlyActive);
                }
            });
        }

        if (btnNetSpeedHideIdle != null) {
            btnNetSpeedHideIdle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean cur = NetworkSpeedHelper.isHideOnIdleEnabled(MainMenuActivity.this);
                    NetworkSpeedHelper.setHideOnIdleEnabled(MainMenuActivity.this, !cur);
                    updateNetworkSpeedUI();
                    Toast.makeText(MainMenuActivity.this,
                        !cur ? "💤 Индикатор будет скрываться при 0 КБ/с" : "📶 Индикатор виден всегда (даже при 0 КБ/с)",
                        Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnNetSpeedAutostart != null) {
            btnNetSpeedAutostart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean cur = NetworkSpeedHelper.isAutoStartEnabled(MainMenuActivity.this);
                    NetworkSpeedHelper.setAutoStartEnabled(MainMenuActivity.this, !cur);
                    updateNetworkSpeedUI();
                    Toast.makeText(MainMenuActivity.this,
                        !cur ? "🔄 Автозапуск после перезагрузки: Включён" : "🔄 Автозапуск после перезагрузки: Выключен",
                        Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnNetSpeedIconMode != null) {
            btnNetSpeedIconMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final int mode = NetworkSpeedHelper.cycleIconMode(MainMenuActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateNetworkSpeedUI();
                                    Toast.makeText(MainMenuActivity.this,
                                        "👁️ Вид индикатора: " + NetworkSpeedHelper.getIconModeLabel(mode),
                                        Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        if (sbNetSpeedOffset != null) {
            sbNetSpeedOffset.setMax(165); // 15..180 dp
            sbNetSpeedOffset.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int dp = 15 + progress;
                    if (tvNetSpeedOffsetVal != null) {
                        tvNetSpeedOffsetVal.setText(dp + " dp");
                    }
                    if (fromUser) {
                        NetworkSpeedHelper.setOverlayOffsetDp(MainMenuActivity.this, dp);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (btnNetSpeedUnits != null) {
            btnNetSpeedUnits.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean curBits = NetworkSpeedHelper.isBitsMode(MainMenuActivity.this);
                    NetworkSpeedHelper.setBitsMode(MainMenuActivity.this, !curBits);
                    updateNetworkSpeedUI();
                    Toast.makeText(MainMenuActivity.this,
                        !curBits ? "📊 Единицы: Кбит/с • Мбит/с (Биты)" : "📊 Единицы: КБ/с • МБ/с (Байты)",
                        Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void applyNetSpeedToggle(final boolean enable) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int result = NetworkSpeedHelper.setEnabled(MainMenuActivity.this, enable);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == NetworkSpeedHelper.RESULT_NEED_NOTIF_PERM) {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_POST_NOTIFICATIONS);
                            } else {
                                Toast.makeText(MainMenuActivity.this, "Разрешите уведомления для отображения скорости в статус-баре", Toast.LENGTH_LONG).show();
                            }
                        } else if (result == NetworkSpeedHelper.RESULT_NEED_OVERLAY_PERM) {
                            new AlertDialog.Builder(MainMenuActivity.this)
                                .setTitle("Разрешение «Поверх других окон»")
                                .setMessage("Чтобы закрепить скорость интернета справа у батареи (чтобы она не смещалась от уведомлений), разрешите отображение поверх других окон (или подключите Shizuku для автоматической выдачи).")
                                .setPositiveButton("Открыть настройки", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + getPackageName()));
                                            startActivity(intent);
                                        } catch (Throwable ignored) {}
                                    }
                                })
                                .setNegativeButton("Отмена", null)
                                .show();
                        } else if (result == NetworkSpeedHelper.RESULT_OK_NATIVE_ROOT) {
                            Toast.makeText(MainMenuActivity.this, "📶 Нативный индикатор скорости Motorola активирован (Root)!", Toast.LENGTH_SHORT).show();
                        } else if (result == NetworkSpeedHelper.RESULT_OK_SERVICE) {
                            Toast.makeText(MainMenuActivity.this, "📶 Скорость интернета в статус-баре включена!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainMenuActivity.this, "Индикатор скорости отключён", Toast.LENGTH_SHORT).show();
                        }
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                updateNetworkSpeedUI();
                            }
                        }, 150);
                    }
                });
            }
        }).start();
    }

    private void updateNetworkSpeedUI() {
        boolean active = NetworkSpeedHelper.isActiveNow(this);
        boolean nativeRoot = NetworkSpeedHelper.isNativeRootActive(this);

        if (btnToggleNetSpeed != null) {
            if (active) {
                btnToggleNetSpeed.setText(nativeRoot
                    ? "📶 Скорость в статус-баре (Native Root): ВКЛ ✓"
                    : "📶 Скорость в статус-баре: ВКЛЮЧЕНА ✓");
                btnToggleNetSpeed.setBackgroundResource(R.drawable.btn_green);
            } else {
                btnToggleNetSpeed.setText("📶 Показать скорость в статус-баре");
                btnToggleNetSpeed.setBackgroundResource(R.drawable.btn_primary);
            }
        }

        boolean hideIdle = NetworkSpeedHelper.isHideOnIdleEnabled(this);
        if (btnNetSpeedHideIdle != null) {
            if (hideIdle) {
                btnNetSpeedHideIdle.setText("💤 Скрывать при 0: Вкл ✓");
                btnNetSpeedHideIdle.setTextColor(0xFF10B981);
            } else {
                btnNetSpeedHideIdle.setText("💤 Скрывать при 0: Выкл");
                btnNetSpeedHideIdle.setTextColor(0xFFF3F4F6);
            }
        }

        boolean autoStart = NetworkSpeedHelper.isAutoStartEnabled(this);
        if (btnNetSpeedAutostart != null) {
            if (autoStart) {
                btnNetSpeedAutostart.setText("🔄 Автозапуск: Вкл ✓");
                btnNetSpeedAutostart.setTextColor(0xFF10B981);
            } else {
                btnNetSpeedAutostart.setText("🔄 Автозапуск: Выкл");
                btnNetSpeedAutostart.setTextColor(0xFFF3F4F6);
            }
        }

        int iconMode = NetworkSpeedHelper.getIconMode(this);
        if (btnNetSpeedIconMode != null) {
            btnNetSpeedIconMode.setText("👁️ Вид: " + NetworkSpeedHelper.getIconModeLabel(iconMode));
        }

        if (layoutNetSpeedOffset != null) {
            layoutNetSpeedOffset.setVisibility(iconMode == NetworkSpeedHelper.ICON_MODE_RIGHT_OVERLAY ? View.VISIBLE : View.GONE);
        }
        int offsetDp = NetworkSpeedHelper.getOverlayOffsetDp(this);
        if (tvNetSpeedOffsetVal != null) {
            tvNetSpeedOffsetVal.setText(offsetDp + " dp");
        }
        if (sbNetSpeedOffset != null) {
            sbNetSpeedOffset.setMax(165); // 15..180 dp
            sbNetSpeedOffset.setProgress(Math.max(0, offsetDp - 15));
        }

        boolean bitsMode = NetworkSpeedHelper.isBitsMode(this);
        if (btnNetSpeedUnits != null) {
            if (bitsMode) {
                btnNetSpeedUnits.setText("📊 Единицы измерения: Кбит/с • Мбит/с (Биты)");
                btnNetSpeedUnits.setTextColor(0xFF06B6D4);
            } else {
                btnNetSpeedUnits.setText("📊 Единицы измерения: КБ/с • МБ/с (Байты)");
                btnNetSpeedUnits.setTextColor(0xFF9CA3AF);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (grantResults != null && grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                applyNetSpeedToggle(true);
            } else {
                Toast.makeText(this, "Без разрешения на уведомления статус-бар не сможет показать скорость", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showSecureSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Разрешение для быстрых тайлов");
        builder.setMessage(
            "Для мгновенного переключения герцовки и разрешения прямо из шторки уведомлений (без открытия приложения и без ПК) требуется разрешение WRITE_SECURE_SETTINGS.\n\n" +
            "Выберите способ активации:\n\n" +
            "• Через Shizuku: активация в 1 клик на самом телефоне\n" +
            "• Через ADB: выполните команду с компьютера:\n" +
            ShellHelper.ADB_GRANT_COMMAND
        );

        if (ShellHelper.isShizukuRunning()) {
            builder.setPositiveButton("Активировать (Shizuku)", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (!ShellHelper.isShizukuPermissionGranted()) {
                        ShellHelper.requestShizukuPermission();
                    } else {
                        boolean ok = ShellHelper.grantWriteSecureSettings(MainMenuActivity.this);
                        if (ok) {
                            Toast.makeText(MainMenuActivity.this, "Разрешение успешно выдано! Тайлы шторки активны.", Toast.LENGTH_SHORT).show();
                            updateStatus();
                        } else {
                            Toast.makeText(MainMenuActivity.this, "Не удалось выдать разрешение через Shizuku.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        } else if (ShellHelper.isRootAvailable()) {
            builder.setPositiveButton("Активировать (Root)", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    boolean ok = ShellHelper.grantWriteSecureSettings(MainMenuActivity.this);
                    if (ok) {
                        Toast.makeText(MainMenuActivity.this, "Разрешение успешно выдано через Root!", Toast.LENGTH_SHORT).show();
                        updateStatus();
                    } else {
                        Toast.makeText(MainMenuActivity.this, "Ошибка выполнения Root команды.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        builder.setNeutralButton("Скопировать команду ADB", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ShellHelper.copyToClipboard(MainMenuActivity.this, ShellHelper.ADB_GRANT_COMMAND, "ADB Command");
                Toast.makeText(MainMenuActivity.this, "Команда ADB скопирована в буфер!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private void showNfcRollbackDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Откат фикса NFC к заводскому стоку");
        builder.setMessage(
            "Откат вернет маршруты NFC к исходному заводскому состоянию (eSE1):\n\n" +
            "• Прямой откат (Shizuku / Root): мгновенный возврат маршрутов в сток прямо на смартфоне без перезагрузки.\n" +
            "• Экспорт ZIP отката: сохранение архива «moto_y70_nfc_revert_stock.zip» в Загрузки для прошивки через Magisk / KernelSU.\n\n" +
            "Выберите способ отката:"
        );

        if (ShellHelper.isShizukuPermissionGranted() || ShellHelper.isRootAvailable()) {
            builder.setPositiveButton("Откатить сейчас", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (tvNfcStatus != null) {
                        tvNfcStatus.setText("Маршруты NFC: Откат к стоку...");
                        tvNfcStatus.setTextColor(0xFFF59E0B);
                    }
                    if (btnInstallNfc != null) btnInstallNfc.setEnabled(false);
                    if (btnUninstallNfc != null) {
                        btnUninstallNfc.setEnabled(false);
                        btnUninstallNfc.setText("⏳ Откат...");
                    }

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final boolean ok = NfcFixHelper.revertToStock(MainMenuActivity.this);
                            final NfcFixHelper.RoutingInfo updatedInfo = NfcFixHelper.getLiveRoutingInfo(MainMenuActivity.this);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    applyNfcInfoToViews(updatedInfo);
                                    if (ok || updatedInfo.status == NfcFixHelper.NfcStatus.FACTORY_STOCK) {
                                        new AlertDialog.Builder(MainMenuActivity.this)
                                            .setTitle("NFC возвращен к стоку! 🔄")
                                            .setMessage("Маршруты контроллера успешно переведены в заводской режим (eSE1, eSE1, eSE1). Перезагрузка не требуется.")
                                            .setPositiveButton("Понятно", null)
                                            .show();
                                    } else {
                                        Toast.makeText(MainMenuActivity.this, "Сброс выполнен", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });

                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    updateNfcStatusUI();
                                }
                            }, 1000);
                        }
                    }).start();
                }
            });
        }

        builder.setNeutralButton("📦 Экспорт ZIP отката", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                java.io.File zip = NfcFixHelper.exportRollbackZip(MainMenuActivity.this);
                if (zip != null && zip.exists()) {
                    new AlertDialog.Builder(MainMenuActivity.this)
                        .setTitle("ZIP отката создан! 📦")
                        .setMessage("Файл успешно сохранен в Загрузки:\n\n" + zip.getAbsolutePath() + "\n\nПрошейте этот архив в Magisk / KernelSU (Модули -> Установить из хранилища) для возврата к стоку.")
                        .setPositiveButton("Понятно", null)
                        .show();
                } else {
                    Toast.makeText(MainMenuActivity.this, "Не удалось создать ZIP отката", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void updateNfcStatusUI() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final NfcFixHelper.RoutingInfo nfcInfo = NfcFixHelper.getLiveRoutingInfo(MainMenuActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyNfcInfoToViews(nfcInfo);
                    }
                });
            }
        }).start();
    }

    private void applyNfcInfoToViews(NfcFixHelper.RoutingInfo nfcInfo) {
        if (tvNfcStatus != null) {
            tvNfcStatus.setText("Маршруты NFC: " + nfcInfo.getDisplayStatus());
            tvNfcStatus.setTextColor(nfcInfo.getStatusColor());
        }
        if (nfcInfo.status == NfcFixHelper.NfcStatus.FIXED_100_PERCENT) {
            if (btnInstallNfc != null) {
                btnInstallNfc.setEnabled(true);
                btnInstallNfc.setText("⚡ Переприменить фикс (DH, DH, DH)");
            }
        } else {
            if (btnInstallNfc != null) {
                btnInstallNfc.setEnabled(true);
                btnInstallNfc.setText("⚡ Активировать фикс (в 1 клик)");
            }
        }
        if (btnUninstallNfc != null) {
            btnUninstallNfc.setEnabled(true);
            btnUninstallNfc.setVisibility(View.VISIBLE);
            btnUninstallNfc.setText("🔄 Откат к стоку (eSE1)");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            getContentResolver().unregisterContentObserver(mSettingsObserver);
        } catch (Throwable ignored) {}
        try {
            Shizuku.removeBinderReceivedListener(mBinderReceivedListener);
        } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
