package com.moto.resolutionmaster;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private TextView tvCurrentRes;
    private TextView tvCurrentDpi;
    private TextView tvCurrentFps;
    private TextView tvDaemonStatus;
    private TextView tvShizukuTitle;
    private TextView tvShizukuDesc;
    private Button btnShizukuAction;
    private CountDownTimer rollbackTimer;
    private AlertDialog confirmDialog;

    private final ContentObserver mSettingsObserver =
        new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateStatusUI();
                updateAnimSpeedDisplay();
            }
        };

    // DPI Sliders & State
    private static final String PREFS_NAME = "resolution_master_dpi";
    private static final String KEY_FHD_DPI = "fhd_dpi";
    private static final String KEY_QHD_DPI = "qhd_dpi";
    private static final String KEY_HD_DPI = "hd_dpi";

    private int selectedFhdDpi = 390;
    private int selectedQhdDpi = 520;
    private int selectedHdDpi = 260;

    private TextView tvFhdDpiVal;
    private SeekBar sbFhdDpi;
    private Button btnFhd;

    private TextView tvQhdDpiVal;
    private SeekBar sbQhdDpi;
    private Button btnQhd;

    private TextView tvHdDpiVal;
    private SeekBar sbHdDpi;
    private Button btnHd;

    // Display refresh-rate and Android animation controls
    private Button btnMainApplyTurbo;
    private Button btnMainResetStock;
    private TextView tvMainAnimSpeedVal;
    private Button btnMainAnimBoost;
    private Button btnMainAnimStock;

    private final Shizuku.OnRequestPermissionResultListener mPermissionResultListener =
        new Shizuku.OnRequestPermissionResultListener() {
            @Override
            public void onRequestPermissionResult(int requestCode, int grantResult) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    ShellHelper.grantWriteSecureSettings(MainActivity.this);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateShizukuUI();
                        updateStatusUI();
                        if (ShellHelper.isShizukuPermissionGranted()) {
                            Toast.makeText(MainActivity.this, "Доступ Shizuku получен! Тайлы шторки активированы.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Доступ Shizuku отклонен", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        };

    private final Shizuku.OnBinderReceivedListener mBinderReceivedListener =
        new Shizuku.OnBinderReceivedListener() {
            @Override
            public void onBinderReceived() {
                if (ShellHelper.isShizukuPermissionGranted()) {
                    ShellHelper.grantWriteSecureSettings(MainActivity.this);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateShizukuUI();
                        updateStatusUI();
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
        setContentView(R.layout.activity_main);

        tvCurrentRes = findViewById(R.id.tv_current_res);
        tvCurrentDpi = findViewById(R.id.tv_current_dpi);
        tvCurrentFps = findViewById(R.id.tv_current_fps);
        tvDaemonStatus = findViewById(R.id.tv_daemon_status);

        tvShizukuTitle = findViewById(R.id.tv_shizuku_title);
        tvShizukuDesc = findViewById(R.id.tv_shizuku_desc);
        btnShizukuAction = findViewById(R.id.btn_shizuku_action);

        Button btnBack = findViewById(R.id.btn_back_to_menu);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        btnShizukuAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ShellHelper.isShizukuRunning()) {
                    if (!ShellHelper.isShizukuPermissionGranted()) {
                        ShellHelper.requestShizukuPermission();
                    } else {
                        ShellHelper.openShizuku(MainActivity.this);
                    }
                } else if (ShellHelper.isShizukuInstalled(MainActivity.this)) {
                    ShellHelper.openShizuku(MainActivity.this);
                } else {
                    showDownloadChooserDialog();
                }
            }
        });

        // Setup Resolution and DPI controls with SeekBars & chips
        setupDpiControls();

        // Refresh Rate buttons
        Button btnFps144 = findViewById(R.id.btn_fps_144);
        Button btnFps120 = findViewById(R.id.btn_fps_120);
        Button btnFps90 = findViewById(R.id.btn_fps_90);
        Button btnFps60 = findViewById(R.id.btn_fps_60);
        Button btnFpsAuto = findViewById(R.id.btn_fps_auto);

        btnFps144.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRefreshRate(DisplayManagerHelper.HZ_144, "144 Гц (Экстрим)");
            }
        });

        btnFps120.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRefreshRate(DisplayManagerHelper.HZ_120, "120 Гц (Баланс)");
            }
        });

        btnFps90.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRefreshRate(DisplayManagerHelper.HZ_90, "90 Гц (Эко-плавно)");
            }
        });

        btnFps60.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRefreshRate(DisplayManagerHelper.HZ_60, "60 Гц (Энергосбережение)");
            }
        });

        btnFpsAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRefreshRate(DisplayManagerHelper.HZ_AUTO, "Авто (Динамический)");
            }
        });

        try {
            getContentResolver().registerContentObserver(
                Settings.System.getUriFor("peak_refresh_rate"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.System.getUriFor("min_refresh_rate"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("display_size_forced"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("display_density_forced"), false, mSettingsObserver);
            getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE), false, mSettingsObserver);
        } catch (Throwable ignored) {}

        try {
            Shizuku.addRequestPermissionResultListener(mPermissionResultListener);
            Shizuku.addBinderReceivedListenerSticky(mBinderReceivedListener);
        } catch (Throwable ignored) {}

        setupTouchControls();
        checkPermission();
        updateStatusUI();
        updateShizukuUI();
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
        updateStatusUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusUI();
        updateShizukuUI();
        updateAnimSpeedDisplay();
    }

    private void updateShizukuUI() {
        if (tvShizukuTitle == null || btnShizukuAction == null) return;

        boolean running = ShellHelper.isShizukuRunning();
        boolean granted = ShellHelper.isShizukuPermissionGranted();
        boolean installed = ShellHelper.isShizukuInstalled(this);

        if (running && granted) {
            tvShizukuTitle.setText(getString(R.string.shizuku_title_granted));
            tvShizukuTitle.setTextColor(0xFF10B981); // accent_green
            tvShizukuDesc.setText(getString(R.string.shizuku_desc_granted));
            btnShizukuAction.setText(getString(R.string.btn_shizuku_open));
        } else if (running) {
            tvShizukuTitle.setText(getString(R.string.shizuku_title_running_no_perm));
            tvShizukuTitle.setTextColor(0xFFF59E0B); // accent_yellow
            tvShizukuDesc.setText(getString(R.string.shizuku_desc_running_no_perm));
            btnShizukuAction.setText(getString(R.string.btn_shizuku_grant));
        } else if (installed) {
            tvShizukuTitle.setText(getString(R.string.shizuku_title_installed));
            tvShizukuTitle.setTextColor(0xFFF59E0B); // accent_yellow
            tvShizukuDesc.setText(getString(R.string.shizuku_desc_installed));
            btnShizukuAction.setText(getString(R.string.btn_shizuku_start));
        } else {
            tvShizukuTitle.setText(getString(R.string.shizuku_title_not_installed));
            tvShizukuTitle.setTextColor(0xFF3D82F6); // accent_blue
            tvShizukuDesc.setText(getString(R.string.shizuku_desc_not_installed));
            btnShizukuAction.setText(getString(R.string.btn_shizuku_download));
        }
    }

    private void setupDpiControls() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedFhdDpi = prefs.getInt(KEY_FHD_DPI, 390);
        selectedQhdDpi = prefs.getInt(KEY_QHD_DPI, 520);
        selectedHdDpi = prefs.getInt(KEY_HD_DPI, 260);

        // --- FHD+ Setup ---
        tvFhdDpiVal = findViewById(R.id.tv_fhd_dpi_val);
        sbFhdDpi = findViewById(R.id.sb_fhd_dpi);
        btnFhd = findViewById(R.id.btn_apply_fhd);
        Button btnFhd360 = findViewById(R.id.btn_fhd_dpi_360);
        Button btnFhd390 = findViewById(R.id.btn_fhd_dpi_390);
        Button btnFhd420 = findViewById(R.id.btn_fhd_dpi_420);

        if (sbFhdDpi != null) {
            sbFhdDpi.setMax(160); // 320 to 480
            sbFhdDpi.setProgress(Math.max(0, Math.min(160, selectedFhdDpi - 320)));
            updateFhdDpiDisplay();

            sbFhdDpi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    selectedFhdDpi = 320 + progress;
                    updateFhdDpiDisplay();
                    prefs.edit().putInt(KEY_FHD_DPI, selectedFhdDpi).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        View.OnClickListener fhdChipClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btn_fhd_dpi_360) selectedFhdDpi = 360;
                else if (id == R.id.btn_fhd_dpi_390) selectedFhdDpi = 390;
                else if (id == R.id.btn_fhd_dpi_420) selectedFhdDpi = 420;
                if (sbFhdDpi != null) sbFhdDpi.setProgress(selectedFhdDpi - 320);
                updateFhdDpiDisplay();
                prefs.edit().putInt(KEY_FHD_DPI, selectedFhdDpi).apply();
            }
        };
        if (btnFhd360 != null) btnFhd360.setOnClickListener(fhdChipClick);
        if (btnFhd390 != null) btnFhd390.setOnClickListener(fhdChipClick);
        if (btnFhd420 != null) btnFhd420.setOnClickListener(fhdChipClick);

        if (btnFhd != null) {
            btnFhd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyWithConfirmation(
                        DisplayManagerHelper.FHD_WIDTH,
                        DisplayManagerHelper.FHD_HEIGHT,
                        selectedFhdDpi,
                        "FHD+ (" + selectedFhdDpi + " DPI)"
                    );
                }
            });
        }

        // --- QHD+ Setup ---
        tvQhdDpiVal = findViewById(R.id.tv_qhd_dpi_val);
        sbQhdDpi = findViewById(R.id.sb_qhd_dpi);
        btnQhd = findViewById(R.id.btn_apply_qhd);
        Button btnQhd480 = findViewById(R.id.btn_qhd_dpi_480);
        Button btnQhd520 = findViewById(R.id.btn_qhd_dpi_520);
        Button btnQhd560 = findViewById(R.id.btn_qhd_dpi_560);

        if (sbQhdDpi != null) {
            sbQhdDpi.setMax(200); // 420 to 620
            sbQhdDpi.setProgress(Math.max(0, Math.min(200, selectedQhdDpi - 420)));
            updateQhdDpiDisplay();

            sbQhdDpi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    selectedQhdDpi = 420 + progress;
                    updateQhdDpiDisplay();
                    prefs.edit().putInt(KEY_QHD_DPI, selectedQhdDpi).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        View.OnClickListener qhdChipClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btn_qhd_dpi_480) selectedQhdDpi = 480;
                else if (id == R.id.btn_qhd_dpi_520) selectedQhdDpi = 520;
                else if (id == R.id.btn_qhd_dpi_560) selectedQhdDpi = 560;
                if (sbQhdDpi != null) sbQhdDpi.setProgress(selectedQhdDpi - 420);
                updateQhdDpiDisplay();
                prefs.edit().putInt(KEY_QHD_DPI, selectedQhdDpi).apply();
            }
        };
        if (btnQhd480 != null) btnQhd480.setOnClickListener(qhdChipClick);
        if (btnQhd520 != null) btnQhd520.setOnClickListener(qhdChipClick);
        if (btnQhd560 != null) btnQhd560.setOnClickListener(qhdChipClick);

        if (btnQhd != null) {
            btnQhd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedQhdDpi == DisplayManagerHelper.QHD_DENSITY) {
                        DisplayManagerHelper.resetResolution(MainActivity.this);
                        Toast.makeText(MainActivity.this, getString(R.string.toast_restored), Toast.LENGTH_SHORT).show();
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                updateStatusUI();
                            }
                        }, 500);
                    } else {
                        applyWithConfirmation(
                            DisplayManagerHelper.QHD_WIDTH,
                            DisplayManagerHelper.QHD_HEIGHT,
                            selectedQhdDpi,
                            "QHD+ (" + selectedQhdDpi + " DPI)"
                        );
                    }
                }
            });
        }

        // --- HD+ Setup ---
        tvHdDpiVal = findViewById(R.id.tv_hd_dpi_val);
        sbHdDpi = findViewById(R.id.sb_hd_dpi);
        btnHd = findViewById(R.id.btn_apply_hd);
        Button btnHd240 = findViewById(R.id.btn_hd_dpi_240);
        Button btnHd260 = findViewById(R.id.btn_hd_dpi_260);
        Button btnHd280 = findViewById(R.id.btn_hd_dpi_280);

        if (sbHdDpi != null) {
            sbHdDpi.setMax(140); // 200 to 340
            sbHdDpi.setProgress(Math.max(0, Math.min(140, selectedHdDpi - 200)));
            updateHdDpiDisplay();

            sbHdDpi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    selectedHdDpi = 200 + progress;
                    updateHdDpiDisplay();
                    prefs.edit().putInt(KEY_HD_DPI, selectedHdDpi).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        View.OnClickListener hdChipClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btn_hd_dpi_240) selectedHdDpi = 240;
                else if (id == R.id.btn_hd_dpi_260) selectedHdDpi = 260;
                else if (id == R.id.btn_hd_dpi_280) selectedHdDpi = 280;
                if (sbHdDpi != null) sbHdDpi.setProgress(selectedHdDpi - 200);
                updateHdDpiDisplay();
                prefs.edit().putInt(KEY_HD_DPI, selectedHdDpi).apply();
            }
        };
        if (btnHd240 != null) btnHd240.setOnClickListener(hdChipClick);
        if (btnHd260 != null) btnHd260.setOnClickListener(hdChipClick);
        if (btnHd280 != null) btnHd280.setOnClickListener(hdChipClick);

        if (btnHd != null) {
            btnHd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyWithConfirmation(
                        DisplayManagerHelper.HD_WIDTH,
                        DisplayManagerHelper.HD_HEIGHT,
                        selectedHdDpi,
                        "HD+ (" + selectedHdDpi + " DPI)"
                    );
                }
            });
        }
    }

    private void updateFhdDpiDisplay() {
        if (tvFhdDpiVal != null) {
            String tag = (selectedFhdDpi == 390) ? " (Рекомендуемый)" : (selectedFhdDpi < 390 ? " (Компактный)" : " (Крупный)");
            tvFhdDpiVal.setText(selectedFhdDpi + " DPI" + tag);
        }
        if (btnFhd != null) {
            btnFhd.setText("Применить FHD+ (" + selectedFhdDpi + " DPI)");
        }
    }

    private void updateQhdDpiDisplay() {
        if (tvQhdDpiVal != null) {
            String tag = (selectedQhdDpi == 520) ? " (Заводской)" : (selectedQhdDpi < 520 ? " (Компактный)" : " (Крупный)");
            tvQhdDpiVal.setText(selectedQhdDpi + " DPI" + tag);
        }
        if (btnQhd != null) {
            btnQhd.setText("Применить QHD+ (" + selectedQhdDpi + " DPI)");
        }
    }

    private void updateHdDpiDisplay() {
        if (tvHdDpiVal != null) {
            String tag = (selectedHdDpi == 260) ? " (Баланс)" : (selectedHdDpi < 260 ? " (Компактный)" : " (Крупный)");
            tvHdDpiVal.setText(selectedHdDpi + " DPI" + tag);
        }
        if (btnHd != null) {
            btnHd.setText("Применить HD+ (" + selectedHdDpi + " DPI)");
        }
    }

    private void showDownloadChooserDialog() {
        final String[] sources = new String[]{
            getString(R.string.source_google_play),
            getString(R.string.source_github),
            getString(R.string.source_4pda)
        };

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_download_title))
            .setItems(sources, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ShellHelper.SHIZUKU_PACKAGE));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } catch (Throwable t) {
                            ShellHelper.openUrl(MainActivity.this, ShellHelper.SHIZUKU_PLAY_STORE_URL);
                        }
                    } else if (which == 1) {
                        ShellHelper.openUrl(MainActivity.this, ShellHelper.SHIZUKU_GITHUB_URL);
                    } else if (which == 2) {
                        ShellHelper.openUrl(MainActivity.this, ShellHelper.SHIZUKU_4PDA_URL);
                    }
                }
            })
            .setNegativeButton(getString(R.string.dialog_fps_close), null)
            .show();
    }

    private void applyRefreshRate(final float rate, final String label) {
        if (ShellHelper.isShizukuRunning() && !ShellHelper.isShizukuPermissionGranted()) {
            ShellHelper.requestShizukuPermission();
            Toast.makeText(this, "Предоставьте доступ в появившемся окне Shizuku", Toast.LENGTH_LONG).show();
            return;
        }

        boolean ok = DisplayManagerHelper.setRefreshRate(this, rate);
        if (ok) {
            Toast.makeText(this, getString(R.string.toast_fps_applied, label), Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateStatusUI();
                }
            }, 300);
        } else {
            showRefreshRateHelpDialog(rate, label);
        }
    }

    private void showRefreshRateHelpDialog(final float rate, final String modeName) {
        final String adbCmd;
        if (rate <= 0.0f) {
            adbCmd = "adb shell \"settings delete system min_refresh_rate; settings delete system peak_refresh_rate\"";
        } else {
            adbCmd = String.format(java.util.Locale.US, "adb shell \"settings put system min_refresh_rate %.1f; settings put system peak_refresh_rate %.1f\"", rate, rate);
        }

        boolean installed = ShellHelper.isShizukuInstalled(this);
        String shizukuActionText = installed ? getString(R.string.btn_shizuku_open) : getString(R.string.btn_shizuku_download);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_fps_title, modeName))
            .setMessage(getString(R.string.dialog_fps_msg, adbCmd))
            .setPositiveButton(shizukuActionText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (ShellHelper.isShizukuInstalled(MainActivity.this)) {
                        ShellHelper.openShizuku(MainActivity.this);
                    } else {
                        showDownloadChooserDialog();
                    }
                }
            })
            .setNeutralButton(getString(R.string.dialog_fps_copy), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    ShellHelper.copyToClipboard(MainActivity.this, adbCmd, "ADB Command");
                    Toast.makeText(MainActivity.this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.dialog_fps_close), null)
            .show();
    }

    private void updateStatusUI() {
        Point p = DisplayManagerHelper.getCurrentResolution(this);
        int dpi = DisplayManagerHelper.getCurrentDensity(this);
        float activeFps = DisplayManagerHelper.getCurrentRefreshRate(this);
        float configuredFps = DisplayManagerHelper.getConfiguredRefreshRate(this);

        tvCurrentRes.setText(String.format("%d × %d", p.x, p.y));
        tvCurrentDpi.setText(getString(R.string.status_dpi_format, dpi));

        if (configuredFps == DisplayManagerHelper.HZ_AUTO) {
            tvCurrentFps.setText(getString(R.string.status_fps_format, Math.round(activeFps)) + " (Авто)");
        } else {
            tvCurrentFps.setText(getString(R.string.status_fps_format, Math.round(configuredFps)) + " (Фикс.)");
        }

        boolean hasSecure = checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
        if (tvDaemonStatus != null) {
            if (hasSecure) {
                tvDaemonStatus.setText(getString(R.string.status_daemon_active));
                tvDaemonStatus.setTextColor(0xFF10B981);
            } else {
                tvDaemonStatus.setText(getString(R.string.status_daemon_inactive));
                tvDaemonStatus.setTextColor(0xFFEF4444);
            }
        }
    }

    private void applyWithConfirmation(final int width, final int height, final int density, final String profileName) {
        DisplayManagerHelper.setResolution(this, width, height, density);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                updateStatusUI();
                showRollbackDialog(profileName);
            }
        }, 300);
    }

    private void showRollbackDialog(final String profileName) {
        if (confirmDialog != null && confirmDialog.isShowing()) {
            confirmDialog.dismiss();
        }
        if (rollbackTimer != null) {
            rollbackTimer.cancel();
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_confirm_title));
        builder.setMessage(getString(R.string.dialog_confirm_msg, profileName, 15));
        builder.setPositiveButton(getString(R.string.dialog_keep), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (rollbackTimer != null) rollbackTimer.cancel();
                Toast.makeText(MainActivity.this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show();
                updateStatusUI();
            }
        });
        builder.setNegativeButton(getString(R.string.dialog_rollback), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (rollbackTimer != null) rollbackTimer.cancel();
                DisplayManagerHelper.resetResolution(MainActivity.this);
                Toast.makeText(MainActivity.this, getString(R.string.toast_restored), Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateStatusUI();
                    }
                }, 500);
            }
        });
        builder.setCancelable(false);
        confirmDialog = builder.create();
        confirmDialog.show();

        rollbackTimer = new CountDownTimer(15000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (confirmDialog != null && confirmDialog.isShowing()) {
                    int remaining = (int) (millisUntilFinished / 1000) + 1;
                    confirmDialog.setMessage(getString(R.string.dialog_confirm_msg, profileName, remaining));
                }
            }

            @Override
            public void onFinish() {
                if (confirmDialog != null && confirmDialog.isShowing()) {
                    confirmDialog.dismiss();
                    DisplayManagerHelper.resetResolution(MainActivity.this);
                    Toast.makeText(MainActivity.this, getString(R.string.toast_timeout), Toast.LENGTH_LONG).show();
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateStatusUI();
                        }
                    }, 500);
                }
            }
        }.start();
    }

    private void checkPermission() {
        if (!ShellHelper.hasWriteSecureSettings(this)) {
            if (ShellHelper.isShizukuPermissionGranted() || ShellHelper.isRootAvailable()) {
                boolean ok = ShellHelper.grantWriteSecureSettings(this);
                if (ok) {
                    Toast.makeText(this, "Разрешение WRITE_SECURE_SETTINGS выдано автоматически!", Toast.LENGTH_SHORT).show();
                    updateStatusUI();
                    return;
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.perm_title));
            builder.setMessage(getString(R.string.perm_msg));

            if (ShellHelper.isShizukuRunning()) {
                builder.setPositiveButton("Активировать (Shizuku)", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!ShellHelper.isShizukuPermissionGranted()) {
                            ShellHelper.requestShizukuPermission();
                        } else {
                            boolean ok = ShellHelper.grantWriteSecureSettings(MainActivity.this);
                            if (ok) {
                                Toast.makeText(MainActivity.this, "Разрешение успешно выдано! Тайлы шторки активны.", Toast.LENGTH_SHORT).show();
                                updateStatusUI();
                            }
                        }
                    }
                });
            }

            builder.setNeutralButton("Скопировать ADB команду", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ShellHelper.copyToClipboard(MainActivity.this, ShellHelper.ADB_GRANT_COMMAND, "ADB Command");
                    Toast.makeText(MainActivity.this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton(getString(R.string.perm_ok), null);
            builder.show();
        }
    }

    private void setupTouchControls() {
        btnMainApplyTurbo = findViewById(R.id.btn_main_apply_turbo);
        tvMainAnimSpeedVal = findViewById(R.id.tv_main_anim_speed_val);
        btnMainAnimBoost = findViewById(R.id.btn_main_anim_boost);
        btnMainAnimStock = findViewById(R.id.btn_main_anim_stock);

        btnMainResetStock = findViewById(R.id.btn_main_reset_stock);

        if (btnMainApplyTurbo != null) {
            btnMainApplyTurbo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean ok = TouchSpeedHelper.enableFullTurboMode(MainActivity.this);
                    updateStatusUI();
                    if (ok) {
                        Toast.makeText(MainActivity.this, "Запрошены 144 Гц и анимации 0.5x. Проверьте фактический режим экрана.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Ошибка применения Турбо-режима (проверьте Shizuku/Root)", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnMainResetStock != null) {
            btnMainResetStock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean ok = TouchSpeedHelper.resetToStock(MainActivity.this);
                    updateStatusUI();
                    if (ok) {
                        Toast.makeText(MainActivity.this, "🔄 Настройки сброшены к стандартным заводским!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Ошибка сброса настроек", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnMainAnimBoost != null) {
            btnMainAnimBoost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyAnimScale(0.5f);
                }
            });
        }

        if (btnMainAnimStock != null) {
            btnMainAnimStock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyAnimScale(1.0f);
                }
            });
        }

        updateAnimSpeedDisplay();
    }

    private void updateAnimSpeedDisplay() {
        float scale = TouchSpeedHelper.getAnimationScale(this);
        if (tvMainAnimSpeedVal != null) {
            tvMainAnimSpeedVal.setText(TouchSpeedHelper.getAnimationScaleLabel(scale));
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

    @Override
    protected void onDestroy() {
        if (rollbackTimer != null) {
            rollbackTimer.cancel();
        }
        if (confirmDialog != null && confirmDialog.isShowing()) {
            confirmDialog.dismiss();
        }
        try {
            getContentResolver().unregisterContentObserver(mSettingsObserver);
        } catch (Throwable ignored) {}
        try {
            Shizuku.removeRequestPermissionResultListener(mPermissionResultListener);
            Shizuku.removeBinderReceivedListener(mBinderReceivedListener);
        } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
