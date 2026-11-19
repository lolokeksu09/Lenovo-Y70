package com.moto.resolutionmaster;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class DebloatActivity extends Activity {

    private Button btnTabPreset;
    private Button btnTabManual;
    private ScrollView scrollPresetTab;
    private LinearLayout layoutManualTab;

    // Preset Views
    private int currentPresetType = DebloatPreset.TYPE_AUTHOR;
    private Button btnPresetAuthor;
    private Button btnPresetAosp;
    private Button btnPresetBalanced;
    private Button btnPresetMinimal;
    private TextView tvPresetInfoTitle;
    private TextView tvPresetTarget;
    private TextView tvPresetWhatInside;
    private TextView tvPresetCounter;
    private ProgressBar pbPresetBatch;
    private Button btnPresetApplyAll;
    private Button btnPresetEnableAll;
    private LinearLayout llPresetContainer;

    // Manual Views
    private EditText etSearch;
    private Button btnFilterAll;
    private Button btnFilterSystem;
    private Button btnFilterUser;
    private Button btnFilterDisabled;
    private ProgressBar pbLoadingApps;
    private ListView lvApps;
    private Button btnEnableAllDisabled;
    private Button btnRefreshApps;
    private TextView tvDisabledSummary;
    private TextView tvBulkEnableProgress;
    private ProgressBar pbBulkEnable;
    private boolean bulkBusy = false;
    private int appsLoadSerial = 0;
    // Explicit per-package Settings workflow. Nothing is changed automatically.
    private final List<String> settingsRestoreQueue = new ArrayList<>();
    private int settingsRestoreIndex = 0;
    private String pendingSettingsPackage = null;
    private boolean settingsPageLeft = false;
    private boolean waitingAccessibilityGrant = false;

    private final DebloatFilterSelection filterSelection = new DebloatFilterSelection();
    private AppListAdapter appListAdapter;
    private final List<AppInfo> installedApps = new ArrayList<>();
    private boolean isAppsLoaded = false;
    private final java.util.Set<String> pendingTogglePackages =
        java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayManagerHelper.bypassHiddenApi(this);
        setContentView(R.layout.activity_debloat);

        Button btnBack = findViewById(R.id.btn_back_menu);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        btnTabPreset = findViewById(R.id.btn_tab_preset);
        btnTabManual = findViewById(R.id.btn_tab_manual);
        scrollPresetTab = findViewById(R.id.scroll_preset_tab);
        layoutManualTab = findViewById(R.id.layout_manual_tab);

        // Tab Switching
        btnTabPreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(true);
            }
        });
        btnTabManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(false);
            }
        });

        // Preset Init
        btnPresetAuthor = findViewById(R.id.btn_preset_author);
        btnPresetAosp = findViewById(R.id.btn_preset_aosp);
        btnPresetBalanced = findViewById(R.id.btn_preset_balanced);
        btnPresetMinimal = findViewById(R.id.btn_preset_minimal);
        tvPresetInfoTitle = findViewById(R.id.tv_preset_info_title);
        tvPresetTarget = findViewById(R.id.tv_preset_target);
        tvPresetWhatInside = findViewById(R.id.tv_preset_what_inside);
        tvPresetCounter = findViewById(R.id.tv_preset_counter);
        pbPresetBatch = findViewById(R.id.pb_preset_batch);
        btnPresetApplyAll = findViewById(R.id.btn_preset_apply_all);
        btnPresetEnableAll = findViewById(R.id.btn_preset_enable_all);
        llPresetContainer = findViewById(R.id.ll_preset_container);

        Button btnShowTable = findViewById(R.id.btn_show_preset_table);
        if (btnShowTable != null) {
            btnShowTable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPresetComparisonDialog();
                }
            });
        }

        View.OnClickListener presetTabClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btn_preset_author) {
                    selectPreset(DebloatPreset.TYPE_AUTHOR);
                } else if (id == R.id.btn_preset_aosp) {
                    selectPreset(DebloatPreset.TYPE_AOSP);
                } else if (id == R.id.btn_preset_balanced) {
                    selectPreset(DebloatPreset.TYPE_BALANCED);
                } else if (id == R.id.btn_preset_minimal) {
                    selectPreset(DebloatPreset.TYPE_MINIMAL);
                }
            }
        };

        if (btnPresetAuthor != null) btnPresetAuthor.setOnClickListener(presetTabClick);
        if (btnPresetAosp != null) btnPresetAosp.setOnClickListener(presetTabClick);
        if (btnPresetBalanced != null) btnPresetBalanced.setOnClickListener(presetTabClick);
        if (btnPresetMinimal != null) btnPresetMinimal.setOnClickListener(presetTabClick);

        btnPresetApplyAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmBatchPreset(true);
            }
        });

        btnPresetEnableAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmBatchPreset(false);
            }
        });

        Button btnDebloatRollback = findViewById(R.id.btn_debloat_rollback);
        if (btnDebloatRollback != null) {
            btnDebloatRollback.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRollbackOptionsDialog();
                }
            });
        }

        Button btnFullStockRollback = findViewById(R.id.btn_full_stock_rollback);
        if (btnFullStockRollback != null) {
            btnFullStockRollback.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    executeGlobalRollback();
                }
            });
        }

        // Manual Init
        etSearch = findViewById(R.id.et_search);
        btnFilterAll = findViewById(R.id.btn_filter_all);
        btnFilterSystem = findViewById(R.id.btn_filter_system);
        btnFilterUser = findViewById(R.id.btn_filter_user);
        btnFilterDisabled = findViewById(R.id.btn_filter_disabled);
        pbLoadingApps = findViewById(R.id.pb_loading_apps);
        lvApps = findViewById(R.id.lv_apps);
        btnEnableAllDisabled = findViewById(R.id.btn_enable_all_disabled);
        btnRefreshApps = findViewById(R.id.btn_refresh_apps);
        tvDisabledSummary = findViewById(R.id.tv_disabled_summary);
        tvBulkEnableProgress = findViewById(R.id.tv_bulk_enable_progress);
        pbBulkEnable = findViewById(R.id.pb_bulk_enable);
        btnEnableAllDisabled.setEnabled(false);
        btnRefreshApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { loadInstalledApps(); }
        });
        btnEnableAllDisabled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { previewEnableAllDisabled(); }
        });

        setupFilters();
        setupSearch();

        // Populate preset list immediately
        refreshPresetList();
    }

    @Override
    protected void onPause() {
        if (pendingSettingsPackage != null) settingsPageLeft = true;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingAccessibilityGrant) {
            waitingAccessibilityGrant = false;
            if (!isFinishing()) showNextSettingsPackage();
            return;
        }
        if (pendingSettingsPackage != null && settingsPageLeft) {
            final String pkg = pendingSettingsPackage;
            pendingSettingsPackage = null;
            settingsPageLeft = false;
            verifyReturnedSettingsPackage(pkg);
            return;
        }
        if (!bulkBusy && isAppsLoaded && layoutManualTab != null &&
                layoutManualTab.getVisibility() == View.VISIBLE) {
            loadInstalledApps();
        }
    }

    private void switchTab(boolean showPreset) {
        if (showPreset) {
            btnTabPreset.setBackgroundResource(R.drawable.btn_primary);
            btnTabPreset.setTextColor(0xFFFFFFFF);
            btnTabManual.setBackgroundColor(0x00000000);
            btnTabManual.setTextColor(0xFF9CA3AF);

            scrollPresetTab.setVisibility(View.VISIBLE);
            layoutManualTab.setVisibility(View.GONE);
            refreshPresetList();
        } else {
            btnTabManual.setBackgroundResource(R.drawable.btn_primary);
            btnTabManual.setTextColor(0xFFFFFFFF);
            btnTabPreset.setBackgroundColor(0x00000000);
            btnTabPreset.setTextColor(0xFF9CA3AF);

            scrollPresetTab.setVisibility(View.GONE);
            layoutManualTab.setVisibility(View.VISIBLE);

            if (!isAppsLoaded) {
                loadInstalledApps();
            }
        }
    }

    private void selectPreset(int type) {
        currentPresetType = type;
        updatePresetTabButtons();
        refreshPresetList();
    }

    private void updatePresetTabButtons() {
        int chipBg = R.drawable.btn_chip;
        int activeBg = R.drawable.btn_primary;
        int textMuted = 0xFF9CA3AF;
        int textWhite = 0xFFFFFFFF;

        if (btnPresetAuthor != null) {
            btnPresetAuthor.setBackgroundResource(currentPresetType == DebloatPreset.TYPE_AUTHOR ? activeBg : chipBg);
            btnPresetAuthor.setTextColor(currentPresetType == DebloatPreset.TYPE_AUTHOR ? textWhite : textMuted);
        }
        if (btnPresetAosp != null) {
            btnPresetAosp.setBackgroundResource(currentPresetType == DebloatPreset.TYPE_AOSP ? activeBg : chipBg);
            btnPresetAosp.setTextColor(currentPresetType == DebloatPreset.TYPE_AOSP ? textWhite : textMuted);
        }
        if (btnPresetBalanced != null) {
            btnPresetBalanced.setBackgroundResource(currentPresetType == DebloatPreset.TYPE_BALANCED ? activeBg : chipBg);
            btnPresetBalanced.setTextColor(currentPresetType == DebloatPreset.TYPE_BALANCED ? textWhite : textMuted);
        }
        if (btnPresetMinimal != null) {
            btnPresetMinimal.setBackgroundResource(currentPresetType == DebloatPreset.TYPE_MINIMAL ? activeBg : chipBg);
            btnPresetMinimal.setTextColor(currentPresetType == DebloatPreset.TYPE_MINIMAL ? textWhite : textMuted);
        }

        if (tvPresetInfoTitle != null) {
            tvPresetInfoTitle.setText(DebloatPreset.getPresetTitle(currentPresetType));
        }
        if (tvPresetTarget != null) {
            tvPresetTarget.setText("🎯 Для кого: " + DebloatPreset.getPresetTarget(currentPresetType));
        }
        if (tvPresetWhatInside != null) {
            tvPresetWhatInside.setText("📦 Что внутри:\n" + DebloatPreset.getPresetWhatInside(currentPresetType));
        }
    }

    private void showPresetComparisonDialog() {
        String msg =
            "══════════════════════════════\n" +
            "1. МИНИМАЛЬНЫЙ (SAFE)\n" +
            "• Для кого: Для всех, 100% без рисков\n" +
            "• Что внутри: Рекламные загрузчики, кредитные хвосты (PayJoy, PAKS AppOps-freeze), иностранные операторы.\n\n" +
            "══════════════════════════════\n" +
            "2. БАЛАНС\n" +
            "• Для кого: Максимум автономности без потери фишек Moto\n" +
            "• Что внутри: Минимальный + вся телеметрия Moto/Google, предустановленный мусор, отключение Doze-трекеров, ненужные сервисы Google. Жесты и камера сохраняются!\n\n" +
            "══════════════════════════════\n" +
            "3. ЧИСТЫЙ\n" +
            "• Для кого: Для фанатов чистого Android / Google Pixel\n" +
            "• Что внутри: Рекомендуемый + отключение надстроек Moto (Smart Connect, Freeform, Moto App, Launcher-оверлеи) с переходом на чистый стиль. Лаунчер Launcher3 защищен!\n\n" +
            "══════════════════════════════\n" +
            "4. АВТОРСКИЙ (Y70 EDITION)\n" +
            "• Для кого: Проверено сообществом (эталон Y70)\n" +
            "• Что внутри: Эталонный список из 111 пакетов для сообщества 4PDA, настроенный и проверенный на Legion Y70.";

        new AlertDialog.Builder(this)
            .setTitle("Таблица сценариев очистки")
            .setMessage(msg)
            .setPositiveButton("Понятно", null)
            .show();
    }

    private void refreshPresetList() {
        if (llPresetContainer == null) return;
        llPresetContainer.removeAllViews();

        updatePresetTabButtons();

        List<DebloatPreset.PresetItem> items = DebloatPreset.getPresetList(currentPresetType);
        int total = items.size();
        int disabledCount = 0;
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final DebloatPreset.PresetItem item : items) {
            final boolean installed = PackageHelper.isPackageInstalled(this, item.packageName);
            final boolean enabled = installed && PackageHelper.isPackageEnabled(this, item.packageName);

            if (!enabled) {
                disabledCount++;
            }

            View row = inflater.inflate(R.layout.item_preset_app, llPresetContainer, false);
            TextView tvTitle = row.findViewById(R.id.tv_preset_title);
            TextView tvPkg = row.findViewById(R.id.tv_preset_pkg);
            TextView tvDesc = row.findViewById(R.id.tv_preset_desc);
            TextView tvCat = row.findViewById(R.id.tv_preset_category);
            TextView tvStatus = row.findViewById(R.id.tv_preset_status);
            final Button btnToggle = row.findViewById(R.id.btn_preset_toggle);

            tvTitle.setText(item.title);
            tvPkg.setText(item.packageName);
            tvDesc.setText(item.description);
            tvCat.setText(item.category);

            final boolean isProtected = PackageHelper.isProtectedOrNonDisable(item.packageName);

            if (!installed) {
                tvStatus.setText("Не установлен / Удален");
                tvStatus.setTextColor(0xFF6B7280);
                btnToggle.setVisibility(View.GONE);
            } else if (!enabled) {
                if (isProtected) {
                    tvStatus.setText("Заморожено (AppOps) ✓");
                } else {
                    tvStatus.setText("Отключено ✓");
                }
                tvStatus.setTextColor(0xFF10B981); // accent_green
                btnToggle.setText("Включить");
                btnToggle.setBackgroundResource(R.drawable.btn_green);
            } else {
                tvStatus.setText("Активно");
                tvStatus.setTextColor(0xFFF59E0B); // accent_yellow
                btnToggle.setText(isProtected ? "Заморозить" : "Отключить");
                btnToggle.setBackgroundResource(R.drawable.btn_red);
            }

            btnToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (enabled) {
                        boolean ok = PackageHelper.disablePackage(DebloatActivity.this, item.packageName);
                        if (ok) {
                            String msg = isProtected ?
                                item.title + " заморожен в фоне (AppOps)" :
                                item.title + " отключен";
                            Toast.makeText(DebloatActivity.this, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(DebloatActivity.this, "Ошибка (требуются права Shizuku/Root)", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        boolean ok = PackageHelper.enablePackage(DebloatActivity.this, item.packageName);
                        if (ok) {
                            Toast.makeText(DebloatActivity.this, item.title + " включен", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(DebloatActivity.this, "Ошибка (требуются права Shizuku/Root)", Toast.LENGTH_SHORT).show();
                        }
                    }
                    refreshPresetList();
                }
            });

            llPresetContainer.addView(row);
        }

        if (tvPresetCounter != null) {
            tvPresetCounter.setText("Отключено: " + disabledCount + " из " + total + " пакетов пресета ✓");
        }
        if (btnPresetApplyAll != null) {
            btnPresetApplyAll.setText("🚫 Применить пресет (" + total + ")");
        }
    }

    private void confirmBatchPreset(final boolean applyPreset) {
        if (!applyPreset) {
            // Restore all packages of current preset
            new AlertDialog.Builder(this)
                .setTitle("Включить пакеты?")
                .setMessage("Все приложения из текущего пресета («" + DebloatPreset.getPresetTitle(currentPresetType) + "») будут снова включены.")
                .setPositiveButton("Включить все", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executeEnableCurrentPreset();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
            return;
        }

        // Apply Preset: Smart bidirectional switching!
        List<DebloatPreset.PresetItem> targetItems = DebloatPreset.getPresetList(currentPresetType);
        final java.util.Set<String> targetSet = new java.util.HashSet<>();
        for (DebloatPreset.PresetItem it : targetItems) {
            targetSet.add(it.packageName);
        }

        final List<String> toDisable = new ArrayList<>();
        for (String pkg : targetSet) {
            if (PackageHelper.isPackageInstalled(this, pkg) && PackageHelper.isPackageEnabled(this, pkg)) {
                toDisable.add(pkg);
            }
        }

        final List<String> toEnable = new ArrayList<>();
        java.util.Set<String> allKnown = DebloatPreset.getAllKnownPackages();
        for (String pkg : allKnown) {
            if (!targetSet.contains(pkg) && PackageHelper.isModifiedByApp(this, pkg) && PackageHelper.isPackageInstalled(this, pkg) && !PackageHelper.isPackageEnabled(this, pkg)) {
                toEnable.add(pkg);
            }
        }

        String msg = "Синхронизация с профилем «" + DebloatPreset.getPresetTitle(currentPresetType) + "»:\n\n" +
            "• Будет отключено/заморожено: " + toDisable.size() + " приложений\n" +
            "• Будет включено обратно: " + toEnable.size() + " приложений\n\n" +
            "Восстанавливаются только пакеты из журнала приложения. Защищённые пакеты и текущий системный лаунчер не отключаются.";

        new AlertDialog.Builder(this)
            .setTitle("Применить пресет?")
            .setMessage(msg)
            .setPositiveButton("Применить", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    executeApplyPreset(toDisable, toEnable);
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void executeApplyPreset(final List<String> toDisable, final List<String> toEnable) {
        final int totalOperations = toDisable.size() + toEnable.size();
        if (totalOperations == 0) {
            Toast.makeText(this, "Нет доступных изменений для этого пресета. Проверка неполных состояний требует Shizuku/Root.", Toast.LENGTH_LONG).show();
            return;
        }

        if (pbPresetBatch != null) {
            pbPresetBatch.setMax(totalOperations);
            pbPresetBatch.setProgress(0);
            pbPresetBatch.setVisibility(View.VISIBLE);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                int disabledOk = 0;
                int enabledOk = 0;
                final List<String> failures = new ArrayList<>();

                for (String pkg : toDisable) {
                    boolean ok = PackageHelper.disablePackage(DebloatActivity.this, pkg);
                    if (ok) disabledOk++;
                    else failures.add(pkg + ": " + PackageHelper.getLastOperationError(pkg));
                    count++;
                    final int progress = count;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (pbPresetBatch != null) pbPresetBatch.setProgress(progress);
                        }
                    });
                }

                for (String pkg : toEnable) {
                    boolean ok = PackageHelper.enablePackage(DebloatActivity.this, pkg);
                    if (ok) enabledOk++;
                    else failures.add(pkg + ": " + PackageHelper.getLastOperationError(pkg));
                    count++;
                    final int progress = count;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (pbPresetBatch != null) pbPresetBatch.setProgress(progress);
                        }
                    });
                }

                final int finalDisabled = disabledOk;
                final int finalEnabled = enabledOk;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pbPresetBatch != null) pbPresetBatch.setVisibility(View.GONE);
                        refreshPresetList();
                        if (!failures.isEmpty()) {
                            StringBuilder details = new StringBuilder();
                            int limit = Math.min(8, failures.size());
                            for (int i = 0; i < limit; i++) {
                                details.append("• ").append(failures.get(i)).append("\n");
                            }
                            if (failures.size() > limit) {
                                details.append("И ещё ошибок: ").append(failures.size() - limit);
                            }
                            new AlertDialog.Builder(DebloatActivity.this)
                                .setTitle("Частичное выполнение пресета")
                                .setMessage("Подтверждено: отключено " + finalDisabled +
                                    "/" + toDisable.size() + ", восстановлено " + finalEnabled +
                                    "/" + toEnable.size() + ".\n\n" + details)
                                .setPositiveButton("Понятно", null)
                                .show();
                        } else {
                            Toast.makeText(DebloatActivity.this,
                                "Подтверждено: отключено " + finalDisabled +
                                ", восстановлено " + finalEnabled, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void executeEnableCurrentPreset() {
        List<DebloatPreset.PresetItem> targetItems = DebloatPreset.getPresetList(currentPresetType);
        final List<String> toEnable = new ArrayList<>();
        for (DebloatPreset.PresetItem it : targetItems) {
            if (PackageHelper.isModifiedByApp(this, it.packageName) && PackageHelper.isPackageInstalled(this, it.packageName) && !PackageHelper.isPackageEnabled(this, it.packageName)) {
                toEnable.add(it.packageName);
            }
        }

        if (toEnable.isEmpty()) {
            Toast.makeText(this, "В этом пресете нет отключений, зарегистрированных Lenovo Y70.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pbPresetBatch != null) {
            pbPresetBatch.setMax(toEnable.size());
            pbPresetBatch.setProgress(0);
            pbPresetBatch.setVisibility(View.VISIBLE);
        }

        PackageHelper.runBatchEnable(this, toEnable, new PackageHelper.BatchCallback() {
            @Override
            public void onProgress(final int current, final int total, String packageName, boolean success) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pbPresetBatch != null) pbPresetBatch.setProgress(current);
                    }
                });
            }

            @Override
            public void onComplete(final int successCount, final int total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pbPresetBatch != null) pbPresetBatch.setVisibility(View.GONE);
                        refreshPresetList();
                        Toast.makeText(DebloatActivity.this, "Включено " + successCount + " из " + total + " пакетов пресета.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showRollbackOptionsDialog() {
        final String[] options = new String[]{
            "Восстановить мои изменения в текущем пресете",
            "Восстановить все изменения Lenovo Y70 по журналу"
        };
        new AlertDialog.Builder(this)
            .setTitle("Безопасный откат Debloater")
            .setMessage("Откат затрагивает только пакеты, отключённые этим приложением начиная с v3.1. " +
                "Ранее отключённые пользователем или другими программами пакеты не включаются.")
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) confirmBatchPreset(false);
                    else executeGlobalRollback();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void executeGlobalRollback() {
        final java.util.Set<String> changed = PackageHelper.getModifiedPackages(this);
        final List<String> toEnable = new ArrayList<>();
        for (String pkg : changed) {
            if (PackageHelper.isPackageInstalled(this, pkg)) {
                toEnable.add(pkg);
            }
        }
        if (toEnable.isEmpty()) {
            Toast.makeText(this, "В журнале нет доступных пакетов для восстановления.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Восстановить только изменения приложения?")
            .setMessage("Будет выполнена попытка восстановления " + toEnable.size() +
                " пакетов из журнала v3.1.\n\n" +
                "Изменения других программ, данные приложений и пользовательские отключения не затрагиваются. " +
                "Некоторые пакеты могут потребовать Root или повторную выдачу доступа Shizuku.")
            .setPositiveButton("Восстановить", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    performGlobalRollback(toEnable);
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void performGlobalRollback(final List<String> toEnable) {
        if (pbPresetBatch != null) {
            pbPresetBatch.setMax(toEnable.size());
            pbPresetBatch.setProgress(0);
            pbPresetBatch.setVisibility(View.VISIBLE);
        }

        PackageHelper.runBatchEnable(this, toEnable, new PackageHelper.BatchCallback() {
            @Override
            public void onProgress(final int current, final int total, String packageName, boolean success) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pbPresetBatch != null) {
                            pbPresetBatch.setProgress(current);
                        }
                    }
                });
            }

            @Override
            public void onComplete(final int successCount, final int total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pbPresetBatch != null) {
                            pbPresetBatch.setVisibility(View.GONE);
                        }
                        refreshPresetList();
                        if (isAppsLoaded) {
                            loadInstalledApps();
                        }
                        new AlertDialog.Builder(DebloatActivity.this)
                            .setTitle(successCount == total ? "Откат проверен" : "Частичный откат")
                            .setMessage("Подтверждено восстановление: " + successCount + " из " + total + " пакетов журнала.\n\nОшибки требуют проверки прав Shizuku/Root.")
                            .setPositiveButton("Отлично", null)
                            .show();
                    }
                });
            }
        });
    }

    private void loadInstalledApps() {
        if (bulkBusy) return;
        final int request = ++appsLoadSerial;
        if (pbLoadingApps != null) pbLoadingApps.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> apps = PackageHelper.getInstalledApps(DebloatActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (request != appsLoadSerial || isFinishing()) return;
                        installedApps.clear();
                        installedApps.addAll(apps);
                        isAppsLoaded = true;

                        if (pbLoadingApps != null) pbLoadingApps.setVisibility(View.GONE);

                        if (appListAdapter == null) {
                            appListAdapter = new AppListAdapter(DebloatActivity.this, installedApps, new AppListAdapter.OnAppToggleListener() {
                            @Override
                            public void onToggle(final AppInfo app, final boolean enable) {
                                if (bulkBusy || !pendingTogglePackages.add(app.packageName)) return;
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        final boolean ok = enable
                                            ? PackageHelper.enablePackage(DebloatActivity.this, app.packageName)
                                            : PackageHelper.disablePackage(DebloatActivity.this, app.packageName);
                                        final Boolean liveState = PackageHelper.getVerifiedPackageEnabledState(
                                            DebloatActivity.this, app.packageName);
                                        final boolean verifiedState = ok && liveState != null &&
                                            liveState.booleanValue() == enable;
                                        final String failure = ok
                                            ? "Состояние изменилось или не читается. Обновите список."
                                            : PackageHelper.getLastOperationError(app.packageName);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                pendingTogglePackages.remove(app.packageName);
                                                app.setEnabledState(liveState);
                                                if (verifiedState) {
                                                    Toast.makeText(DebloatActivity.this,
                                                        app.label + ": " + (enable ? "включено" : "отключено") + " (проверено)",
                                                        Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(DebloatActivity.this,
                                                        failure, Toast.LENGTH_LONG).show();
                                                }
                                                // Rebuild the active filter, not just currently visible rows.
                                                appListAdapter.refreshFilter();
                                                updateDisabledSummary();
                                            }
                                        });
                                    }
                                }).start();
                            }
                        });

                            // Adapter was absent when the user chose a category:
                            // apply the persisted selection BEFORE showing rows.
                            appListAdapter.setFilter(filterSelection.getMode());
                            if (lvApps != null) {
                                lvApps.setAdapter(appListAdapter);
                            }
                        } else {
                            // Apply the current user selection after each async reload.
                            appListAdapter.updateList(installedApps);
                            appListAdapter.setFilter(filterSelection.getMode());
                        }
                        updateDisabledSummary();
                    }
                });
            }
        }).start();
    }

    /** Never present the bulk action as available for stale or unknown states. */
    private void updateDisabledSummary() {
        int disabled = 0;
        int protectedCount = 0;
        int unknown = 0;
        for (AppInfo app : installedApps) {
            if (app.enabledState == null) {
                unknown++;
            } else if (Boolean.FALSE.equals(app.enabledState)) {
                if (PackageHelper.isProtectedInDebloater(this, app.packageName)) {
                    protectedCount++;
                } else {
                    disabled++;
                }
            }
        }
        if (tvDisabledSummary != null) {
            tvDisabledSummary.setText("Можно включить: " + disabled +
                " · Защищённых: " + protectedCount +
                " · Не определено: " + unknown);
        }
        if (btnEnableAllDisabled != null) {
            btnEnableAllDisabled.setEnabled(!bulkBusy && disabled > 0);
        }
    }

    private void setBulkBusy(boolean busy) {
        bulkBusy = busy;
        if (btnEnableAllDisabled != null) btnEnableAllDisabled.setEnabled(!busy);
        if (btnRefreshApps != null) btnRefreshApps.setEnabled(!busy);
        if (btnFilterAll != null) btnFilterAll.setEnabled(!busy);
        if (btnFilterSystem != null) btnFilterSystem.setEnabled(!busy);
        if (btnFilterUser != null) btnFilterUser.setEnabled(!busy);
        if (btnFilterDisabled != null) btnFilterDisabled.setEnabled(!busy);
        if (etSearch != null) etSearch.setEnabled(!busy);
        if (lvApps != null) lvApps.setEnabled(!busy);
        if (!busy) updateDisabledSummary();
    }

    /**
     * The preview always loads a FRESH snapshot. A button click alone never
     * changes system packages; there is a second explicit confirmation.
     */
    private void previewEnableAllDisabled() {
        if (bulkBusy) return;
        if (!pendingTogglePackages.isEmpty()) {
            Toast.makeText(this, "Сначала дождитесь завершения текущего действия", Toast.LENGTH_LONG).show();
            return;
        }
        setBulkBusy(true);
        if (tvBulkEnableProgress != null) {
            tvBulkEnableProgress.setVisibility(View.VISIBLE);
            tvBulkEnableProgress.setText("Проверяю отключённые пакеты...");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean permitted = ShellHelper.isShizukuPermissionGranted() ||
                    ShellHelper.isRootAvailable();
                final List<AppInfo> latest = permitted
                    ? PackageHelper.getInstalledApps(DebloatActivity.this)
                    : new ArrayList<AppInfo>();
                final List<BulkEnablePolicy.Candidate> candidates = new ArrayList<>();
                int protectedCount = 0;
                int unknownCount = 0;
                for (AppInfo app : latest) {
                    boolean protectedPkg = PackageHelper.isProtectedInDebloater(
                        DebloatActivity.this, app.packageName);
                    candidates.add(new BulkEnablePolicy.Candidate(
                        app.packageName, app.enabledState, protectedPkg));
                    if (Boolean.FALSE.equals(app.enabledState) && protectedPkg) protectedCount++;
                    if (app.enabledState == null) unknownCount++;
                }
                final List<String> targets = BulkEnablePolicy.select(candidates);
                final int skippedProtected = protectedCount;
                final int skippedUnknown = unknownCount;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        setBulkBusy(false);
                        if (tvBulkEnableProgress != null)
                            tvBulkEnableProgress.setVisibility(View.GONE);
                        if (!permitted) {
                            new AlertDialog.Builder(DebloatActivity.this)
                                .setTitle("Нужен Shizuku или Root")
                                .setMessage("Для массового включения требуется доступ к системному состоянию приложений.")
                                .setPositiveButton("Открыть Shizuku", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ShellHelper.openShizuku(DebloatActivity.this);
                                    }
                                })
                                .setNegativeButton("Отмена", null)
                                .show();
                            return;
                        }
                        if (targets.isEmpty()) {
                            new AlertDialog.Builder(DebloatActivity.this)
                                .setTitle("Нет подтверждённых отключений")
                                .setMessage("Защищённых пакетов: " + skippedProtected +
                                    ", неопределённых: " + skippedUnknown +
                                    ". Неопределённые состояния не изменяются.")
                                .setPositiveButton("Понятно", null)
                                .show();
                            loadInstalledApps();
                            return;
                        }
                        StringBuilder preview = new StringBuilder();
                        preview.append("Будут включены ").append(targets.size())
                            .append(" подтверждённо отключённых пакетов.\n\n");
                        int previewCount = Math.min(targets.size(), 18);
                        for (int i = 0; i < previewCount; i++) {
                            preview.append("• ").append(targets.get(i)).append("\n");
                        }
                        if (targets.size() > previewCount) {
                            preview.append("… и ещё ").append(targets.size() - previewCount).append("\n");
                        }
                        preview.append("\nПропущено защищённых: ").append(skippedProtected)
                            .append("; неизвестных: ").append(skippedUnknown).append(".")
                            .append("\n\nВнимание: могут включиться ранее намеренно отключённые " +
                                "системные приложения. Это может изменить поведение, " +
                                "приватность и расход заряда. Данные программ не удаляются.");
                        new AlertDialog.Builder(DebloatActivity.this)
                            .setTitle("Включить все отключённые?")
                            .setMessage(preview.toString())
                            .setPositiveButton("Включить " + targets.size(), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    performBulkEnable(targets);
                                }
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                    }
                });
            }
        }).start();
    }

    /**
     * Guided recovery uses the same Android app-info page which successfully
     * changed expressweather and brapps from enabled=2 to enabled=0.
     * It never invokes an undocumented API or bypasses platform permissions.
     */
    private void beginSettingsRecovery(List<String> blocked) {
        settingsRestoreQueue.clear();
        settingsRestoreIndex = 0;
        for (String pkg : blocked) {
            if (pkg != null && pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") &&
                    !PackageHelper.isProtectedInDebloater(this, pkg) &&
                    !settingsRestoreQueue.contains(pkg)) {
                settingsRestoreQueue.add(pkg);
            }
        }
        showNextSettingsPackage();
    }

    private void showNextSettingsPackage() {
        if (isFinishing()) return;
        if (settingsRestoreIndex >= settingsRestoreQueue.size()) {
            settingsRestoreQueue.clear();
            settingsRestoreIndex = 0;
            Toast.makeText(this, "Список восстановления завершён", Toast.LENGTH_LONG).show();
            loadInstalledApps();
            return;
        }
        final String pkg = settingsRestoreQueue.get(settingsRestoreIndex);
        final boolean serviceReady = PackageEnableAccessibilityService.isEnabled(this);
        final String[] options = {
            serviceReady ? "Включить автоматически (специальный доступ)"
                         : "Настроить автовключение (один раз)",
            "Открыть системную страницу вручную",
            "Пропустить это приложение"
        };
        new AlertDialog.Builder(this)
            .setTitle("Восстановление: " + (settingsRestoreIndex + 1) + "/" +
                settingsRestoreQueue.size())
            .setMessage(pkg + "\n\n" +
                "Автовключение использует системную страницу Android: служба " +
                "может нажать только «Включить» для выбранного пакета. " +
                "При неподдерживаемом интерфейсе выбери ручной способ.")
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        if (serviceReady) {
                            if (PackageEnableAccessibilityService.arm(
                                    DebloatActivity.this, pkg)) {
                                openPackageDetailsForRestore(pkg);
                            } else {
                                Toast.makeText(DebloatActivity.this,
                                    "Не удалось подготовить безопасное автовключение",
                                    Toast.LENGTH_LONG).show();
                                showNextSettingsPackage();
                            }
                        } else {
                            explainAndRequestAccessibilityGrant();
                        }
                    } else if (which == 1) {
                        PackageEnableAccessibilityService.cancel();
                        openPackageDetailsForRestore(pkg);
                    } else {
                        settingsRestoreIndex++;
                        showNextSettingsPackage();
                    }
                }
            })
            .setNegativeButton("Завершить", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    PackageEnableAccessibilityService.cancel();
                    settingsRestoreQueue.clear();
                    settingsRestoreIndex = 0;
                    loadInstalledApps();
                }
            })
            .setCancelable(false)
            .show();
    }

    private void explainAndRequestAccessibilityGrant() {
        new AlertDialog.Builder(this)
            .setTitle("Доступ к специальным возможностям")
            .setMessage("Android потребует вручную включить службу «Lenovo Y70: " +
                "включение приложений». Такая служба имеет доступ к содержимому " +
                "экрана и может нажимать кнопки. Наш режим ограничен окном " +
                "системных настроек и действует только по разовому запросу.\n\n" +
                "Если не хочешь давать этот доступ, можно продолжить вручную. " +
                "На Android 13+ для APK, установленного из файла, может также " +
                "потребоваться разрешить «Ограниченные настройки» в сведениях о приложении.")
            .setPositiveButton("Открыть специальные возможности",
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            waitingAccessibilityGrant = true;
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Throwable e) {
                            waitingAccessibilityGrant = false;
                            Toast.makeText(DebloatActivity.this,
                                "Экран специальных возможностей недоступен",
                                Toast.LENGTH_LONG).show();
                            showNextSettingsPackage();
                        }
                    }
                })
            .setNegativeButton("Вернуться", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    showNextSettingsPackage();
                }
            })
            .show();
    }

    private void openPackageDetailsForRestore(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            pendingSettingsPackage = pkg;
            settingsPageLeft = false;
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            PackageEnableAccessibilityService.cancel();
            pendingSettingsPackage = null;
            Toast.makeText(this, "Системная страница приложения недоступна",
                Toast.LENGTH_LONG).show();
            showNextSettingsPackage();
        } catch (SecurityException e) {
            PackageEnableAccessibilityService.cancel();
            pendingSettingsPackage = null;
            Toast.makeText(this, "Прошивка запретила открытие страницы приложения",
                Toast.LENGTH_LONG).show();
            showNextSettingsPackage();
        }
    }

    private void verifyReturnedSettingsPackage(final String pkg) {
        new Thread(new Runnable() {
            @Override public void run() {
                final Boolean confirmed = PackageHelper.getVerifiedPackageEnabledState(
                    DebloatActivity.this, pkg);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        loadInstalledApps();
                        if (Boolean.TRUE.equals(confirmed)) {
                            settingsRestoreIndex++;
                            Toast.makeText(DebloatActivity.this,
                                pkg + ": теперь включено", Toast.LENGTH_SHORT).show();
                            showNextSettingsPackage();
                        } else {
                            new AlertDialog.Builder(DebloatActivity.this)
                                .setTitle("Включение не подтверждено")
                                .setMessage(pkg + "\n\n" +
                                    (confirmed == null
                                        ? "Системный статус недоступен. Проверь Shizuku."
                                        : "Пакет по-прежнему отключён. Возможно, прошивка запрещает включение."))
                                .setPositiveButton("Повторить", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        showNextSettingsPackage();
                                    }
                                })
                                .setNeutralButton("Пропустить", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        settingsRestoreIndex++;
                                        showNextSettingsPackage();
                                    }
                                })
                                .setNegativeButton("Завершить", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        settingsRestoreQueue.clear();
                                        settingsRestoreIndex = 0;
                                    }
                                })
                                .setCancelable(false)
                                .show();
                        }
                    }
                });
            }
        }).start();
    }

    /** Re-check EVERY package immediately before and after the shell command. */
    private void performBulkEnable(final List<String> targets) {
        if (bulkBusy || targets == null || targets.isEmpty()) return;
        setBulkBusy(true);
        if (pbBulkEnable != null) {
            pbBulkEnable.setVisibility(View.VISIBLE);
            pbBulkEnable.setMax(targets.size());
            pbBulkEnable.setProgress(0);
        }
        if (tvBulkEnableProgress != null) {
            tvBulkEnableProgress.setVisibility(View.VISIBLE);
            tvBulkEnableProgress.setText("Включено 0 из " + targets.size());
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int success = 0;
                int skipped = 0;
                int blocked = 0;
                final List<String> blockedPackages = new ArrayList<>();
                final List<String> failures = new ArrayList<>();
                for (int i = 0; i < targets.size(); i++) {
                    String pkg = targets.get(i);
                    boolean ok = PackageHelper.enableConfirmedDisabledPackage(
                        DebloatActivity.this, pkg);
                    if (ok) {
                        success++;
                    } else {
                        String reason = PackageHelper.getLastOperationError(pkg);
                        if (reason.startsWith("Пропуск:")) {
                            skipped++;
                        } else if (reason.startsWith("Ограничение Android:")) {
                            blocked++;
                            blockedPackages.add(pkg);
                        } else {
                            failures.add(pkg + ": " + reason);
                        }
                    }
                    final int completed = i + 1;
                    final int enabledNow = success;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (pbBulkEnable != null) pbBulkEnable.setProgress(completed);
                            if (tvBulkEnableProgress != null) {
                                tvBulkEnableProgress.setText("Проверено " + completed +
                                    "/" + targets.size() + " · Включено: " + enabledNow);
                            }
                        }
                    });
                }
                final int enabledCount = success;
                final int skippedCount = skipped;
                final int blockedCount = blocked;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (pbBulkEnable != null) pbBulkEnable.setVisibility(View.GONE);
                        if (tvBulkEnableProgress != null)
                            tvBulkEnableProgress.setVisibility(View.GONE);
                        setBulkBusy(false);
                        loadInstalledApps();
                        // A one-screen summary; never put system-server Java
                        // stack traces or dozens of package errors in a dialog.
                        StringBuilder details = new StringBuilder();
                        details.append("Включено: ").append(enabledCount).append("/")
                            .append(targets.size())
                            .append("\nЗапрещено прошивкой: ").append(blockedCount)
                            .append("\nПропущено: ").append(skippedCount)
                            .append("\nДругие ошибки: ").append(failures.size());
                        if (blockedCount > 0) {
                            details.append("\n\nAndroid не разрешает включать эти пакеты через Shizuku. ")
                                .append("Для некоторых пакетов может потребоваться Root, ")
                                .append("но даже он не гарантирует снятие защиты.");
                            int shown = Math.min(4, blockedPackages.size());
                            for (int i = 0; i < shown; i++) {
                                details.append("\n• ").append(blockedPackages.get(i));
                            }
                            if (blockedPackages.size() > shown)
                                details.append("\n… и ещё ").append(blockedPackages.size() - shown);
                        }
                        if (!failures.isEmpty()) {
                            details.append("\n\nДругие ошибки:");
                            int shown = Math.min(3, failures.size());
                            for (int i = 0; i < shown; i++)
                                details.append("\n• ").append(failures.get(i));
                            if (failures.size() > shown)
                                details.append("\n… и ещё ").append(failures.size() - shown);
                        }
                        final StringBuilder report = new StringBuilder();
                        report.append("Lenovo Y70 — результат включения отключённых\n")
                            .append("Включено: ").append(enabledCount).append("/")
                            .append(targets.size()).append("\n")
                            .append("Заблокировано Android: ").append(blockedCount).append("\n")
                            .append("Пропущено: ").append(skippedCount).append("\n")
                            .append("Ошибки: ").append(failures.size()).append("\n");
                        if (!blockedPackages.isEmpty()) {
                            report.append("\nЗаблокированные пакеты:\n");
                            for (String pkg : blockedPackages) report.append(pkg).append("\n");
                        }
                        if (!failures.isEmpty()) {
                            report.append("\nДругие ошибки:\n");
                            for (String failure : failures) report.append(failure).append("\n");
                        }
                        final String reportText = report.toString();
                        AlertDialog.Builder resultDialog = new AlertDialog.Builder(DebloatActivity.this)
                            .setTitle(blockedCount == 0 && failures.isEmpty() && skippedCount == 0
                                ? "Включение подтверждено" : "Результат восстановления")
                            .setMessage(details.toString())
                            .setNeutralButton("Копировать отчёт", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    android.content.ClipboardManager clipboard =
                                        (android.content.ClipboardManager) getSystemService(
                                            android.content.Context.CLIPBOARD_SERVICE);
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                                            "Lenovo Y70 Debloater", reportText));
                                        Toast.makeText(DebloatActivity.this,
                                            "Отчёт скопирован", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setPositiveButton("Готово", null);
                        if (blockedCount > 0) {
                            resultDialog.setNegativeButton("Через настройки", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int which) {
                                    beginSettingsRecovery(blockedPackages);
                                }
                            });
                        }
                        resultDialog.show();
                    }
                });
            }
        }).start();
    }

    private void setupFilters() {
        View.OnClickListener filterListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int id = v.getId();
                if (id == R.id.btn_filter_all) selectFilter(0);
                else if (id == R.id.btn_filter_system) selectFilter(1);
                else if (id == R.id.btn_filter_user) selectFilter(2);
                else if (id == R.id.btn_filter_disabled) selectFilter(3);
            }
        };
        btnFilterAll.setOnClickListener(filterListener);
        btnFilterSystem.setOnClickListener(filterListener);
        btnFilterUser.setOnClickListener(filterListener);
        btnFilterDisabled.setOnClickListener(filterListener);
        selectFilter(0);
    }

    /**
     * Keep visual selection and underlying data predicate in sync.
     * The adapter may not exist yet while PackageHelper scans the device.
     */
    private void selectFilter(int mode) {
        filterSelection.select(mode);
        final int selected = filterSelection.getMode();
        resetFilterButtons();
        Button active;
        if (selected == 1) active = btnFilterSystem;
        else if (selected == 2) active = btnFilterUser;
        else if (selected == 3) active = btnFilterDisabled;
        else active = btnFilterAll;
        active.setBackgroundResource(R.drawable.btn_primary);
        active.setTextColor(0xFFFFFFFF);
        if (appListAdapter != null) {
            appListAdapter.setFilter(selected);
        }
        if (selected == 3 && isAppsLoaded) {
            // Re-read actual per-user package state without resetting category.
            loadInstalledApps();
        }
    }

    private void resetFilterButtons() {
        int chipBg = R.drawable.btn_chip;
        int textMuted = 0xFF9CA3AF;

        btnFilterAll.setBackgroundResource(chipBg);
        btnFilterAll.setTextColor(textMuted);
        btnFilterSystem.setBackgroundResource(chipBg);
        btnFilterSystem.setTextColor(textMuted);
        btnFilterUser.setBackgroundResource(chipBg);
        btnFilterUser.setTextColor(textMuted);
        btnFilterDisabled.setBackgroundResource(chipBg);
        btnFilterDisabled.setTextColor(textMuted);
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (appListAdapter != null) {
                    appListAdapter.setQuery(s.toString());
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
}
