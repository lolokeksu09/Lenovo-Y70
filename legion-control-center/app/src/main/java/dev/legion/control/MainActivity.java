package dev.legion.control;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.text.TextUtils;
import android.os.IBinder;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import java.util.ArrayList;
import java.util.List;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

/** Fully native, no web UI; vetted operations only, via Shizuku UserService. */
public final class MainActivity extends Activity {
    // Consistent AMOLED surfaces and semantic state colors. No colored ornaments or invented metrics.
    private static final int BG=0xFF060609, CARD=0xFF121218, BORDER=0xFF24242B;
    private static final int WHITE=0xFFF6F5FC, MUTED=0xFF92919F, PURPLE=0xFFC3B3FF, GREEN=0xFF7BE2B2, WARN=0xFFFFC78C;
    private static final int RAISED=0xFF202028;
    private static final String[] TABS={"Обзор","Игры","Дисплей","Система","История"};
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout container, main, tabs;
    private ScrollView bodyScroll;
    private TextView connectionText, footer, liveSummary, profileSummary, activeFontStatus;
    private final List<Button> actionButtons = new ArrayList<>();
    private ICommandService remote;
    private int currentTab=0;
    private boolean connecting=false, busy=false;
    private boolean showAllHistory=false;
    private String lastHistoryExport="";
    private String lastDiagnostic="";
    private final Shizuku.OnBinderReceivedListener received=()->{ updateConnection(); connectIfPossible(); };
    private final Shizuku.OnBinderDeadListener dead=()->{ remote=null;connecting=false;updateConnection(); };
    private final Shizuku.OnRequestPermissionResultListener permission=(code,grant)->{
        if(code==1021){ updateConnection(); if(grant==PackageManager.PERMISSION_GRANTED) connectIfPossible(); }
    };
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            remote=ICommandService.Stub.asInterface(binder); connecting=false;
            updateConnection(); renderTab();
        }
        @Override public void onServiceDisconnected(ComponentName name){
            remote=null;connecting=false;updateConnection();
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        buildRoot();
        if (state != null) currentTab=Math.max(0,Math.min(TABS.length-1,state.getInt("activeTab",0)));
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addRequestPermissionResultListener(permission);
        updateConnection();renderTab();
    }
    @Override protected void onSaveInstanceState(Bundle state){ state.putInt("activeTab",currentTab);super.onSaveInstanceState(state); }
    @Override protected void onResume(){
        super.onResume();
        updateConnection();
        connectIfPossible();
    }
    @Override protected void onDestroy(){
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        executor.shutdownNow();super.onDestroy();
    }
    private boolean authorized(){
        try{return Shizuku.pingBinder() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}
        catch(Throwable t){return false;}
    }
    private void updateConnection(){
        if(connectionText==null)return;
        boolean ready=remote!=null&&authorized();
        connectionText.setText(ready?"● SHIZUKU ПОДКЛЮЧЁН":authorized()?"● ПОДКЛЮЧЕНИЕ К СЛУЖБЕ":"● ТРЕБУЕТСЯ ДОСТУП SHIZUKU");
        connectionText.setTextColor(ready?GREEN:WARN);
    }
    private void connectIfPossible(){
        if(!authorized()||remote!=null||connecting)return;
        connecting=true;updateConnection();
        try{
            Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(new ComponentName(getPackageName(),ShellUserService.class.getName()))
                    .processNameSuffix("legion_control").daemon(false).debuggable(false).version(3);
            Shizuku.bindUserService(args,connection);
        }catch(Throwable t){connecting=false;showError("Не удалось подключить UserService: "+t.getMessage());}
    }
    private void requestAccess(){
        try{
            if(!Shizuku.pingBinder()) {showError("Запусти Shizuku и вернись в приложение.");return;}
            if(authorized()){connectIfPossible();return;}
            Shizuku.requestPermission(1021);
        }catch(Throwable t){showError("Ошибка Shizuku: "+t.getMessage());}
    }
    /** Restored v1.2 top navigation, header and compact connection card; v1.3 controls remain. */
    private void buildRoot(){
        bodyScroll=new ScrollView(this);
        bodyScroll.setFillViewport(true);
        bodyScroll.setBackgroundColor(BG);
        bodyScroll.setClipToPadding(false);
        main=new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(20),dp(25),dp(20),dp(40));
        bodyScroll.addView(main);
        // Android 15+ draws behind system bars: apply real status/navigation bar insets.
        if(Build.VERSION.SDK_INT>=30) getWindow().setDecorFitsSystemWindows(false);
        bodyScroll.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                top=bars.top;bottom=bars.bottom;
            }else{
                top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();
            }
            main.setPadding(dp(20),top+dp(18),dp(20),bottom+dp(32));
            return insets;
        });
        setContentView(bodyScroll);
        bodyScroll.requestApplyInsets();

        TextView eyebrow=text("L E G I O N  /  C O N T R O L",12,PURPLE,true);
        main.addView(eyebrow);
        TextView title=text("Система под контролем.",30,WHITE,true);
        title.setPadding(0,dp(10),0,dp(5));
        main.addView(title);
        main.addView(text("Lenovo Legion Y70  ·  Motorola ROM  ·  без root",13,MUTED,false));

        LinearLayout conn=card();
        conn.setPadding(dp(15),dp(15),dp(15),dp(15));
        connectionText=text("● ПРОВЕРКА SHIZUKU",12,WARN,true);
        conn.addView(connectionText);
        button(conn,"Проверить подключение",false,()->{requestAccess();updateConnection();});
        main.addView(conn,withTop(dp(19)));

        HorizontalScrollView navScroll=new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        navScroll.addView(tabs);
        main.addView(navScroll,withTop(dp(18)));

        container=new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        main.addView(container,withTop(dp(12)));
        footer=text("Изменения только по подтверждению. Без root, без управления ядром, без удаления пакетов.",12,MUTED,false);
        footer.setPadding(0,dp(22),0,0);
        main.addView(footer);
    }
    private void renderTab(){
        if(container==null)return;
        tabs.removeAllViews();
        for(int i=0;i<TABS.length;i++){
            final int tab=i;
            TextView chip=text(TABS[i],13,i==currentTab?BG:MUTED,true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(15),dp(11),dp(15),dp(11));
            chip.setMinHeight(dp(48));
            chip.setBackground(round(i==currentTab?PURPLE:CARD,14,BORDER));
            chip.setFocusable(true);
            chip.setSelected(i==currentTab);
            chip.setContentDescription("Раздел "+TABS[i]+(i==currentTab?", выбран":""));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
            lp.setMargins(0,0,dp(8),0);
            tabs.addView(chip,lp);
            chip.setOnClickListener(v->{
                if(busy)return;
                currentTab=tab;
                renderTab();
                bodyScroll.post(()->bodyScroll.smoothScrollTo(0,0));
            });
        }
        container.removeAllViews();
        actionButtons.clear();
        if (!getPreferences(0).getString("pending", "").isEmpty()) {
            LinearLayout warning=card();
            line(warning, "ТРЕБУЕТСЯ ВОССТАНОВЛЕНИЕ", "Есть незавершённое изменение. Новые операции заблокированы до проверки и отката.");
            button(warning,"Восстановить предыдущие значения",true,
                ()->confirm("Восстановить незавершённое изменение?", "Система получит команды для возврата исходных значений.",this::recoverPending));
            container.addView(warning,withTop(dp(12)));
        }
        switch(currentTab){
            case 0:overview();break;
            case 1:games();break;
            case 2:display();break;
            case 3:system();break;
            case 4:history();break;
        }
        updateBusyState();
    }
    private void section(String heading,String desc){
        TextView t=text(heading,20,WHITE,true);t.setPadding(0,dp(15),0,dp(4));container.addView(t);
        TextView d=text(desc,13,MUTED,false);container.addView(d);
    }
    private void overview(){
        section("Устройство", "Актуальные значения считываются через Shizuku, а не из прошлых отчётов.");
        LinearLayout c=card();
        line(c,"ИНФОРМАЦИЯ","Определение аппаратного кода, прошивки и настроек экрана. Значение Motorola не подменяет модель телефона.");
        liveSummary=line(c,"ТЕКУЩЕЕ СОСТОЯНИЕ","Нажми «Обновить данные устройства»");
        button(c,"Обновить данные устройства",true,this::runOverviewRead);
        button(c,"Сформировать диагностический отчёт",false,this::exportDiagnostic);
        container.addView(c,withTop(dp(12)));
        LinearLayout profile=card();
        line(profile,"Быстрые профили", "Параметры экрана и трёх анимаций меняются одной транзакцией. CPU, GPU, GameManager и энергосбережение не трогаем.");
        profileSummary=line(profile,"Текущий профиль", "Нажми «Считать значения», чтобы проверить настройки.");
        button(profile,"Для игр   ·   120 Гц / 0,5×",true,()->confirm("Применить профиль «Для игр»?", "Изменятся 5 системных значений: частота экрана и три анимации. Исходные значения можно восстановить через «История».",()->applyProfile("game")));
        button(profile,"На каждый день   ·   90 Гц / 0,5×",false,()->confirm("Применить повседневный профиль?", "Изменятся 5 системных значений. Энергосбережение не затрагивается. Откат доступен в разделе «История».",()->applyProfile("daily")));
        button(profile,"Считать значения профиля",false,this::readProfile);
        container.addView(profile,withTop(dp(12)));
        LinearLayout tip=card();line(tip,"Игровой режим", "По последнему аудиту Wild Rift поддерживает standard/custom. Режим performance не предлагается. Проверить текущее состояние можно во вкладке «Игры».");container.addView(tip,withTop(dp(12)));
    }
    private void runOverviewRead(){
        executeAsync(()->{
            String output=exec(CommandPolicy.DEVICE_SUMMARY_COMMAND);
            SystemInspector.Result result=SystemInspector.device(output);
            onUi(()->{
                if(currentTab==0 && liveSummary!=null && liveSummary.isAttachedToWindow())
                    liveSummary.setText(SystemInspector.deviceBrief(result));
                showStructuredResults("Данные устройства",result);
            });
        });
    }
    private void games(){
        section("Wild Rift","Только реально доступные игровые режимы Android. FPS и лимиты CPU не подменяются.");
        LinearLayout c=card();line(c,"ПАКЕТ",CommandPolicy.WILD_RIFT);
        button(c,"Проверить режимы GameManager",true,()->runRead("cmd game list-modes com.riotgames.league.wildrift; cmd game list-configs com.riotgames.league.wildrift","Режимы Wild Rift"));
        button(c,"Вернуть стандартный режим",false,()->confirm("Вернуть стандартный игровой режим?","Будет изменён только GameManager для Wild Rift.",()->changeGame("standard")));
        container.addView(c,withTop(dp(12)));
        LinearLayout info=card();line(info,"ДОСТУПНЫЕ РЕЖИМЫ ПО АУДИТУ","standard, custom");line(info,"PERFORMANCE","Не предлагается: отсутствует в available game modes");container.addView(info,withTop(dp(10)));
    }
    private void display(){
        section("Дисплей","Применяются только поддерживаемые профили. Энергосбережение не отключается автоматически.");
        LinearLayout c=card();line(c,"РАЗРЕШЕНИЕ","По последнему аудиту: 1440 × 3168 · 442 dpi");
        line(c,"ЧАСТОТА","60, 90 и 120 Гц. Режим 144 Гц проверяется на устройстве перед применением.");
        button(c,"Проверить текущие значения",true,()->runRead("settings get global low_power; settings get system min_refresh_rate; settings get system peak_refresh_rate; wm size; wm density","Дисплей"));
        for(int hz:new int[]{60,90,120,144})
            button(c,"Установить "+hz+" Гц"+(hz==144?" (проверка поддержки)":""),false,
                ()->confirm("Установить "+hz+" Гц?","Будут изменены min_refresh_rate и peak_refresh_rate. При энергосбережении система может ограничить частоту до 60 Гц. Для 144 Гц сначала проверяется фактическая поддержка дисплея.",()->changeRate(hz)));
        button(c,"Вернуть системный выбор частоты",false,
            ()->confirm("Вернуть автоматическую частоту?","Будут удалены только пользовательские переопределения min_refresh_rate и peak_refresh_rate. Android снова выберет частоту по настройкам прошивки. Откат доступен в «История».",this::changeRateAuto));
        container.addView(c,withTop(dp(12)));
        LinearLayout anim=card();line(anim,"АНИМАЦИЯ ИНТЕРФЕЙСА","Три стандартных системных коэффициента");
        button(anim,"0× — без анимаций",false,()->changeAnimations("0.0"));
        button(anim,"0,5× — быстрая",false,()->changeAnimations("0.5"));
        button(anim,"1× — штатная",false,()->changeAnimations("1.0"));container.addView(anim,withTop(dp(12)));
    }
    private void system(){
        section("Системные инструменты", "Проверка функций Motorola, шрифтов и разрешений через Shizuku.");

        LinearLayout mods=card();
        line(mods,"MOTOROLA", "Пять компонентов прошивки. Показываем фактическую установку, не системные пути.");
        button(mods,"Проверить компоненты Motorola",true,()->runStructuredRead(
            "for p in com.motorola.gamemode com.motorola.freeform com.motorola.mobiledesktop com.motorola.batterycare com.motorola.personalize; do echo ===$p; pm path $p | head -1; done", "Модули Motorola"));
        container.addView(mods,withTop(dp(12)));

        LinearLayout font=card();
        line(font,"СИСТЕМНЫЕ ШРИФТЫ", "Выбор только из установленного набора Android. Изменения можно откатить.");
        activeFontStatus=line(font,"ТЕКУЩИЙ ШРИФТ", "Не проверен · нажми «Проверить шрифты»");
        button(font,"Проверить шрифты",true,()->readFonts());
        LinearLayout fontChoices=new LinearLayout(this);
        fontChoices.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<CommandPolicy.FONTS.size();i+=2){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0,dp(9),0,0);
            for(int j=i;j<Math.min(i+2,CommandPolicy.FONTS.size());j++){
                String pkg=CommandPolicy.FONTS.get(j);
                String name=fontLabel(pkg);
                actionChip(row,name,false,()->confirm("Установить шрифт «"+name+"»?", "Системное оформление Motorola может заменить выбранный шрифт.",()->changeFont(pkg)));
            }
            fontChoices.addView(row);
        }
        font.addView(fontChoices,withTop(dp(8)));
        container.addView(font,withTop(dp(12)));

        LinearLayout apps=card();
        line(apps,"ФОНОВЫЕ ПРОЦЕССЫ", "Ограничение может задерживать уведомления. Управление разрешено только выбранным приложениям.");
        java.util.Map<String,TextView> appStates=new java.util.LinkedHashMap<>();
        for(String pkg:CommandPolicy.BACKGROUND_PACKAGES){
            if(pkg.equals("com.termux"))continue;
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(13),dp(12),dp(13),dp(12));
            item.setBackground(round(RAISED,14,BORDER));
            TextView name=text(shortName(pkg),16,WHITE,true);
            item.addView(name);
            TextView state=text("Состояние не проверено",13,MUTED,false);
            state.setPadding(0,dp(4),0,dp(9));
            item.addView(state);
            appStates.put(pkg,state);
            LinearLayout options=new LinearLayout(this);
            options.setOrientation(LinearLayout.HORIZONTAL);
            actionChip(options,"Разрешить",false,()->changeAppOp(pkg,"allow"));
            actionChip(options,"Ограничить",false,()->confirm("Ограничить "+shortName(pkg)+"?","Это может повлиять на уведомления. Вернуть настройку можно здесь или через «История».",()->changeAppOp(pkg,"ignore")));
            actionChip(options,"Сброс",false,()->changeAppOp(pkg,"default"));
            item.addView(options);
            apps.addView(item,withTop(dp(10)));
        }
        button(apps,"Обновить состояния AppOps",true,()->readAppOpStates(appStates));
        container.addView(apps,withTop(dp(12)));

        LinearLayout safety=card();
        line(safety,"БЕЗОПАСНОСТЬ СИСТЕМЫ", "Отчёт об отключённых пакетах. Экстренные службы и остальные компоненты здесь не изменяются.");
        button(safety,"Проверить отключённые службы",false,()->runStructuredRead("pm list packages -d","Отключённые службы"));
        container.addView(safety,withTop(dp(12)));
    }
    private String fontLabel(String pkg){
        String name=pkg.substring(pkg.lastIndexOf('.')+1);
        return name.equals("notoserifsource")?"Noto Serif":name;
    }
    /** Compact buttons preserve tap targets and are blocked during a Shizuku write. */
    private void actionChip(LinearLayout row,String caption,boolean primary,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(caption);b.setTextSize(12);
        b.setTextColor(primary?BG:WHITE);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setPadding(dp(5),dp(7),dp(5),dp(7));
        b.setBackground(round(primary?PURPLE:CARD,11,primary?PURPLE:BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        if(row.getChildCount()>0)lp.leftMargin=dp(7);
        row.addView(b,lp);
        actionButtons.add(b);
        b.setOnClickListener(v->{if(busy)return;action.run();});
    }
    private void readFonts(){
        executeAsync(()->{
            String raw=exec("cmd overlay list --user 0 | grep -E 'com.android.theme.font'");
            SystemInspector.Result result=SystemInspector.fonts(raw);
            onUi(()->{
                if(currentTab==3&&activeFontStatus!=null&&activeFontStatus.isAttachedToWindow())
                    activeFontStatus.setText(result.headline);
                showStructuredResults("Системные шрифты",result);
            });
        });
    }
    private void readAppOpStates(java.util.Map<String,TextView> states){
        executeAsync(()->{
            java.util.Map<String,String> results=new java.util.LinkedHashMap<>();
            for(String pkg:states.keySet()){
                String output=exec(CommandPolicy.appOpsGet(pkg));
                results.put(pkg,SystemInspector.appOpState(output));
            }
            onUi(()->{
                if(currentTab!=3)return;
                for(java.util.Map.Entry<String,String> item:results.entrySet()){
                    TextView view=states.get(item.getKey());
                    if(view!=null&&view.isAttachedToWindow()){
                        view.setText("Фоновая работа: "+item.getValue());
                        view.setTextColor(item.getValue().equals("Ограничен")?WARN:item.getValue().equals("Разрешён")?GREEN:MUTED);
                    }
                }
            });
        });
    }
    private void runStructuredRead(String cmd,String title){
        executeAsync(()->{
            String raw=exec(cmd);
            SystemInspector.Result result;
            if(title.equals("Модули Motorola"))result=SystemInspector.motorola(raw);
            else if(title.equals("Системные шрифты"))result=SystemInspector.fonts(raw);
            else result=SystemInspector.disabled(raw);
            onUi(()->showStructuredResults(title,result));
        });
    }
    private String installedVersion(){
        try {return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception e){return "версия не определена";}
    }
    private String shortName(String pkg){return pkg.equals("org.telegram.messenger")?"Telegram":pkg.equals("dev.monolith.launcher")?"MONOLITH":"Wild Rift";}
    private void history(){
        section("Восстановление и откат","Снимок сохраняется до первой системной команды и остаётся после перезапуска приложения.");
        String last=getPreferences(0).getString("undo","");
        String pending=getPreferences(0).getString("pending","");
        if(!pending.isEmpty()){ LinearLayout restore=card();line(restore,"НЕЗАВЕРШЁННАЯ ОПЕРАЦИЯ",getPreferences(0).getString("pending_label","Доступен аварийный откат"));button(restore,"Восстановить исходные значения",true,()->confirm("Восстановление", "Отменить незавершённое изменение?",this::recoverPending));container.addView(restore,withTop(dp(12)));}
        LinearLayout c=card();line(c,"ПОСЛЕДНЯЯ ОПЕРАЦИЯ",last.isEmpty()?"Изменений пока нет":getPreferences(0).getString("undo_label","Доступен откат"));
        Button undoButton=button(c,"Откатить последнее изменение",true,()->confirm("Вернуть предыдущие настройки?","Будут восстановлены значения, сохранённые перед последней операцией.",this::undo));
        undoButton.setEnabled(!last.isEmpty());
        container.addView(c,withTop(dp(12)));

        List<ActionHistory.Entry> events=ActionHistory.read(getPreferences(0).getString("action_history",""));
        LinearLayout journal=card();
        line(journal,"ЖУРНАЛ ДЕЙСТВИЙ",events.isEmpty()?"Событий пока нет":"Последние события сохраняются после перезапуска. Откат доступен только для последнего изменения.");
        int shown=showAllHistory?events.size():Math.min(6,events.size());
        for(int i=0;i<shown;i++){
            ActionHistory.Entry event=events.get(i);
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(13),dp(12),dp(13),dp(12));
            item.setBackground(round(RAISED,12,BORDER));
            String state=event.type.equals(ActionHistory.APPLIED)?"Применено":event.type.equals(ActionHistory.UNDONE)?"Отменено":"Восстановлено";
            int stateColor=event.type.equals(ActionHistory.APPLIED)?PURPLE:GREEN;
            TextView status=text(state,12,stateColor,true);
            item.addView(status);
            TextView label=text(event.label,15,WHITE,true);
            label.setPadding(0,dp(5),0,dp(4));
            item.addView(label);
            String stamp=new SimpleDateFormat("dd.MM.yyyy  HH:mm",Locale.getDefault()).format(new Date(event.timestamp));
            item.addView(text(stamp,12,MUTED,false));
            journal.addView(item,withTop(dp(8)));
        }
        if(events.size()>6)button(journal,showAllHistory?"Свернуть журнал":"Показать все ("+events.size()+")",false,()->{showAllHistory=!showAllHistory;renderTab();});
        if(!events.isEmpty()) button(journal,"Экспортировать журнал TXT",false,this::exportHistory);
        container.addView(journal,withTop(dp(12)));

        LinearLayout info=card();line(info,"ЗАЩИТНЫЕ ОГРАНИЧЕНИЯ","Нет удаления/отключения пакетов, правок thermal/CPU/GPU, скрытых настроек ядра или автоматического отключения энергосбережения.");container.addView(info,withTop(dp(12)));
        LinearLayout about=card();
        line(about,"О ПРИЛОЖЕНИИ", "Legion Control · " + installedVersion());
        line(about,"ДОСТУП", "Shizuku (ADB Shell). Root не требуется.");
        line(about,"КОНФИДЕНЦИАЛЬНОСТЬ", "Без доступа к интернету и аналитики. Журнал хранится на устройстве, TXT экспортируется только вручную.");
        line(about,"СОВМЕСТИМОСТЬ", "Разработано для Lenovo Legion Y70 (XT2611-1) с прошивкой Motorola. Работа с другими устройствами не подтверждена.");
        container.addView(about,withTop(dp(12)));
    }
    private void runRead(String command,String title){
        executeAsync(()->{
            String output=exec(command);
            if("Режимы Wild Rift".equals(title)||"Дисплей".equals(title)){
                SystemInspector.Result structured="Режимы Wild Rift".equals(title)?SystemInspector.gameModes(output):SystemInspector.display(output);
                onUi(()->showStructuredResults(title,structured));
            }else onUi(()->showDialog(title,output));
        });
    }
    private interface Work{void run() throws Exception;}
    private void executeAsync(Work work){
        if(busy){showError("Дождись завершения текущей операции.");return;}
        if(remote==null||!authorized()){requestAccess();return;}
        busy=true;updateBusyState();
        executor.execute(()->{try{work.run();}catch(Exception e){onUi(()->showError(formatError(e)));}finally{onUi(()->{
            busy=false;updateConnection();
            if(!getPreferences(0).getString("pending","").isEmpty())renderTab();
            else updateBusyState();
        });}});
    }
    private String formatError(Exception e){
        String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        if(e.getSuppressed().length>0) message += "\nВНИМАНИЕ: автоматический откат завершился ошибкой. Открой «История» и восстанови исходные значения.";
        return message;
    }
    private String exec(String cmd) throws Exception {
        ICommandService svc=remote;
        if(svc==null||!authorized()) throw new IllegalStateException("Shizuku не подключён");
        JSONObject json=new JSONObject(svc.execute(cmd));
        String result=json.optString("output","");
        if(json.optInt("exit",-1)!=0) throw new IllegalStateException("Команда не выполнена: "+result);
        return result.trim();
    }
    private String setting(String ns,String key) throws Exception {return exec(CommandPolicy.getSetting(ns,key));}
    private MutationTransaction.Store transactionStore(){
        return new MutationTransaction.Store(){
            public boolean hasPending(){return !getPreferences(0).getString("pending","").isEmpty();}
            public boolean stage(String snapshot,String label){return getPreferences(0).edit().putString("pending",snapshot).putString("pending_label",label).commit();}
            public boolean complete(String snapshot,String label){
                String history=ActionHistory.append(getPreferences(0).getString("action_history",""),System.currentTimeMillis(),ActionHistory.APPLIED,label);
                return getPreferences(0).edit().putString("undo",snapshot).putString("undo_label",label)
                    .putString("action_history",history).remove("pending").remove("pending_label").commit();
            }
            public boolean clearPending(){return getPreferences(0).edit().remove("pending").remove("pending_label").commit();}
        };
    }
    private void transact(JSONObject snapshot,String label,MutationTransaction.Action change,MutationTransaction.Action rollback) throws Exception{
        MutationTransaction.apply(transactionStore(),snapshot.toString(),label,change,rollback);
    }
    /** One write-ahead journal for all five writes; no half-applied 'gaming optimizer'. */
    private void applyProfile(String id){
        final QuickProfile profile=QuickProfile.find(id);
        executeAsync(()->{
            String[] namespaces={"system","system","global","global","global"};
            String[] keys={"min_refresh_rate","peak_refresh_rate","window_animation_scale","transition_animation_scale","animator_duration_scale"};
            String[] targets={profile.hz+".0",profile.hz+".0",profile.animation,profile.animation,profile.animation};
            org.json.JSONArray entries=new org.json.JSONArray();
            for(int i=0;i<keys.length;i++){
                String before=setting(namespaces[i],keys[i]);
                CommandPolicy.restoreValue(keys[i],before);
                entries.put(new JSONObject().put("ns",namespaces[i]).put("key",keys[i]).put("value",before));
            }
            JSONObject snapshot=new JSONObject().put("kind","profile").put("entries",entries);
            transact(snapshot,"Профиль: "+profile.title,()->{
                for(int i=0;i<keys.length;i++) exec(CommandPolicy.putSetting(namespaces[i],keys[i],targets[i]));
                for(int i=0;i<keys.length;i++) if(!targets[i].equals(setting(namespaces[i],keys[i])))
                    throw new IllegalStateException("Не подтверждён параметр: "+keys[i]);
            },()->restoreAndVerify(snapshot));
            onUi(()->{if(profileSummary!=null&&profileSummary.isAttachedToWindow())profileSummary.setText(profile.hz+" Гц • анимации "+profile.animation+"×");showDialog("Профиль применён",profile.title+": "+profile.hz+" Гц, анимации "+profile.animation+"×. Для отката открой «История».");});
        });
    }
    private void readProfile(){
        executeAsync(()->{
            String min=setting("system","min_refresh_rate"),peak=setting("system","peak_refresh_rate");
            String a=setting("global","window_animation_scale"),b=setting("global","transition_animation_scale"),c=setting("global","animator_duration_scale");
            String summary="Частота: "+min+"–"+peak+" Гц\nАнимации: "+a+" / "+b+" / "+c+"×";
            onUi(()->{if(profileSummary!=null&&profileSummary.isAttachedToWindow()) profileSummary.setText(summary);});
        });
    }
    private void restoreComposite(JSONObject undo) throws Exception {
        org.json.JSONArray entries=undo.getJSONArray("entries");
        if(entries.length()!=5)throw new IllegalArgumentException("Снимок профиля повреждён");
        Exception first=null;
        for(int i=0;i<entries.length();i++){
            JSONObject e=entries.getJSONObject(i);
            try {
                String ns=e.getString("ns"),key=e.getString("key");
                String value=CommandPolicy.restoreValue(key,e.getString("value"));
                exec(value.equals("null")?CommandPolicy.deleteSetting(ns,key):CommandPolicy.putSetting(ns,key,value));
            } catch(Exception failure){if(first==null)first=failure;}
        }
        if(first!=null)throw first;
    }
    private void changeRate(int hz){
        if(!CommandPolicy.supportedRate(hz))return;
        executeAsync(()->{
            if(hz==144){
                String modes=exec(CommandPolicy.DISPLAY_144_CHECK);
                if(!CommandPolicy.display144Supported(modes))
                    throw new IllegalStateException("Дисплей не подтвердил режим 144 Гц. Настройки не изменены.");
            }
            String oldMin=setting("system","min_refresh_rate");String oldPeak=setting("system","peak_refresh_rate");
            CommandPolicy.restoreValue("min_refresh_rate",oldMin);
            CommandPolicy.restoreValue("peak_refresh_rate",oldPeak);
            JSONObject undo=new JSONObject().put("kind","settings");
            undo.put("ns","system").put("keys",new org.json.JSONArray().put("min_refresh_rate").put("peak_refresh_rate")).put("values",new org.json.JSONArray().put(oldMin).put(oldPeak));
            transact(undo,"Частота "+hz+" Гц",()->{
                exec(CommandPolicy.putSetting("system","min_refresh_rate",hz+".0"));
                exec(CommandPolicy.putSetting("system","peak_refresh_rate",hz+".0"));
                if(!setting("system","min_refresh_rate").equals(hz+".0")||!setting("system","peak_refresh_rate").equals(hz+".0")) throw new IllegalStateException("Проверка записи не пройдена");
            },()->restoreAndVerify(undo));
            onUi(()->showDialog("Готово","Установлено "+hz+" Гц. Энергосбережение может ограничивать активный режим."));
        });
    }
    /** Clears only our two refresh-rate overrides, preserving an exact undo snapshot. */
    private void changeRateAuto(){
        executeAsync(()->{
            String oldMin=setting("system","min_refresh_rate");
            String oldPeak=setting("system","peak_refresh_rate");
            CommandPolicy.restoreValue("min_refresh_rate",oldMin);
            CommandPolicy.restoreValue("peak_refresh_rate",oldPeak);
            JSONObject undo=new JSONObject().put("kind","settings").put("ns","system")
                .put("keys",new org.json.JSONArray().put("min_refresh_rate").put("peak_refresh_rate"))
                .put("values",new org.json.JSONArray().put(oldMin).put(oldPeak));
            transact(undo,"Системный выбор частоты",()->{
                exec(CommandPolicy.deleteSetting("system","min_refresh_rate"));
                exec(CommandPolicy.deleteSetting("system","peak_refresh_rate"));
                if(!"null".equals(setting("system","min_refresh_rate")) ||
                   !"null".equals(setting("system","peak_refresh_rate")))
                   throw new IllegalStateException("Android не подтвердил удаление переопределений");
            },()->restoreAndVerify(undo));
            onUi(()->showDialog("Системный режим", "Переопределения удалены. Частоту снова выбирает прошивка. Откат — в разделе «История»."));
        });
    }
    private void changeAnimations(String value){
        executeAsync(()->{
            String[] keys={"window_animation_scale","transition_animation_scale","animator_duration_scale"};
            org.json.JSONArray ka=new org.json.JSONArray(),va=new org.json.JSONArray();
            for(String key:keys){
                String previous=setting("global",key);
                CommandPolicy.restoreValue(key,previous);
                ka.put(key);va.put(previous);
            }
            JSONObject undo=new JSONObject().put("kind","settings").put("ns","global").put("keys",ka).put("values",va);
            transact(undo,"Анимации "+value+"×",()->{
                for(String key:keys)exec(CommandPolicy.putSetting("global",key,value));
                for(String key:keys)if(!setting("global",key).equals(value))throw new IllegalStateException("Запись не подтверждена: "+key);
            },()->restoreAndVerify(undo));
            onUi(()->showDialog("Готово","Анимации: "+value+"×"));
        });
    }
    private void changeFont(String font){
        executeAsync(()->{
            String before=exec("cmd overlay list --user 0");String old=CommandPolicy.enabledFont(before);
            JSONObject undo=new JSONObject().put("kind","font").put("before",old).put("after",font);
            transact(undo,"Шрифт "+font.substring(font.lastIndexOf('.')+1),()->{
                exec(CommandPolicy.overlayFont(font));
                String after=exec("cmd overlay list --user 0");
                if(!after.contains("[x] "+font))throw new IllegalStateException("Оверлей не включился");
            },()->restoreAndVerify(undo));
            onUi(()->{
                if(currentTab==3&&activeFontStatus!=null&&activeFontStatus.isAttachedToWindow())
                    activeFontStatus.setText("Активен: "+fontLabel(font));
                showDialog("Шрифт применён", "Выбран "+fontLabel(font)+". Некоторые экраны могут обновиться после перезапуска.");
            });
        });
    }
    private void changeGame(String mode){
        executeAsync(()->{
            String listing=exec("cmd game list-modes "+CommandPolicy.WILD_RIFT);
            if(!CommandPolicy.gameModeAvailable(listing,mode))throw new IllegalStateException("GameManager не поддерживает режим "+mode);
            String old=CommandPolicy.currentGameMode(listing);
            if(old.equals("unknown"))throw new IllegalStateException("Не удалось прочитать предыдущий режим");
            JSONObject snapshot=new JSONObject().put("kind","game").put("before",old);
            transact(snapshot,"GameManager "+mode,()->{
                exec(CommandPolicy.gameMode(mode));
                String now=exec("cmd game list-modes "+CommandPolicy.WILD_RIFT);
                if(!CommandPolicy.currentGameMode(now).equals(mode))throw new IllegalStateException("Режим не применился");
            },()->restoreAndVerify(snapshot));
            onUi(()->showDialog("Готово","Wild Rift: "+mode));
        });
    }
    private void changeAppOp(String pkg,String target){
        executeAsync(()->{
            String old=CommandPolicy.appOpsMode(exec(CommandPolicy.appOpsGet(pkg)));
            if(old.equals("unknown"))throw new IllegalStateException("Не удалось определить текущий AppOp");
            JSONObject snapshot=new JSONObject().put("kind","appops").put("pkg",pkg).put("before",old);
            transact(snapshot,"Фон "+shortName(pkg)+": "+target,()->{
                exec(CommandPolicy.appOpsSet(pkg,target));
                String now=CommandPolicy.appOpsMode(exec(CommandPolicy.appOpsGet(pkg)));
                if(!now.equals(target))throw new IllegalStateException("Не удалось подтвердить AppOp: "+now);
            },()->restoreAndVerify(snapshot));
            onUi(()->showDialog("Настройка сохранена","Для "+shortName(pkg)+" выбран режим «"+SystemInspector.appOpState("RUN_ANY_IN_BACKGROUND: "+target)+"». Нажми «Обновить состояния AppOps» для проверки."));
        });
    }
    private void restoreSettings(JSONObject undo) throws Exception {
        String ns=undo.getString("ns");org.json.JSONArray keys=undo.getJSONArray("keys"),values=undo.getJSONArray("values");
        Exception first=null;
        for(int i=0;i<keys.length();i++){
            String k=keys.getString(i),v=values.getString(i);
            try{String canonical=CommandPolicy.restoreValue(k,v);exec(canonical.equals("null")?CommandPolicy.deleteSetting(ns,k):CommandPolicy.putSetting(ns,k,canonical));}
            catch(Exception e){if(first==null)first=e;}
        }
        if(first!=null)throw first;
    }
    private void restoreFont(JSONObject undo) throws Exception {
        String old=undo.optString("before"),newFont=undo.optString("after");
        if(old.isEmpty()){if(!CommandPolicy.FONTS.contains(newFont))throw new IllegalArgumentException("Font not allowed");exec("cmd overlay disable --user 0 "+newFont);}
        else exec(CommandPolicy.overlayFont(old));
    }
    private void restoreAndVerify(JSONObject entry) throws Exception {
        String kind=entry.getString("kind");
            switch(kind){
                case "settings":restoreSettings(entry);break;
                case "profile":restoreComposite(entry);break;
                case "font":restoreFont(entry);break;
                case "game":exec(CommandPolicy.gameMode(entry.getString("before")));break;
                case "appops":exec(CommandPolicy.appOpsSet(entry.getString("pkg"),entry.getString("before")));break;
                default:throw new IllegalArgumentException("Неизвестный формат отката");
            }
            if(kind.equals("settings")) {
                String ns=entry.getString("ns");
                org.json.JSONArray keys=entry.getJSONArray("keys"), values=entry.getJSONArray("values");
                for(int i=0;i<keys.length();i++){
                    String expected=CommandPolicy.restoreValue(keys.getString(i),values.getString(i));
                    String actual=setting(ns,keys.getString(i));
                    if(!actual.equals(expected) && !(expected.equals("null") && actual.equals("null")))
                        throw new IllegalStateException("Откат не подтверждён: "+keys.getString(i));
                }
            } else if(kind.equals("profile")) {
                org.json.JSONArray entries=entry.getJSONArray("entries");
                for(int i=0;i<entries.length();i++){
                    JSONObject e=entries.getJSONObject(i);
                    String ns=e.getString("ns"),key=e.getString("key");
                    String value=CommandPolicy.restoreValue(key,e.getString("value"));
                    if(!value.equals(setting(ns,key)))throw new IllegalStateException("Не подтверждён откат: "+key);
                }
            } else if(kind.equals("game")) {
                if(!CommandPolicy.currentGameMode(exec("cmd game list-modes "+CommandPolicy.WILD_RIFT)).equals(entry.getString("before"))) throw new IllegalStateException("GameManager не подтвердил откат");
            } else if(kind.equals("appops")) {
                String mode=CommandPolicy.appOpsMode(exec(CommandPolicy.appOpsGet(entry.getString("pkg"))));
                if(!mode.equals(entry.getString("before"))) throw new IllegalStateException("AppOps не подтвердил откат");
            } else if(kind.equals("font")) {
                if(!CommandPolicy.enabledFont(exec("cmd overlay list --user 0")).equals(entry.getString("before"))) throw new IllegalStateException("Шрифт не восстановлен");
            }
    }
    private void recoverPending(){
        String pending=getPreferences(0).getString("pending", "");
        if(pending.isEmpty()){showError("Нет незавершённых изменений.");return;}
        executeAsync(()->{
            restoreAndVerify(new JSONObject(pending));
            String label=getPreferences(0).getString("pending_label","Системные настройки");
            String history=ActionHistory.append(getPreferences(0).getString("action_history",""),System.currentTimeMillis(),ActionHistory.RECOVERED,label);
            if(!getPreferences(0).edit().putString("action_history",history).remove("pending").remove("pending_label").commit())
                throw new IllegalStateException("Не удалось сохранить результат восстановления");
            onUi(()->{showDialog("Восстановление завершено","Исходные значения проверены. Новые операции доступны.");renderTab();});
        });
    }
    private void undo(){
        if(!getPreferences(0).getString("pending","").isEmpty()){
            showError("Есть незавершённая операция. Сначала восстанови её исходные значения.");
            return;
        }
        String text=getPreferences(0).getString("undo","");
        if(text.isEmpty()){showError("Нет сохранённой операции.");return;}
        executeAsync(()->{
            restoreAndVerify(new JSONObject(text));
            String label=getPreferences(0).getString("undo_label","Системные настройки");
            String history=ActionHistory.append(getPreferences(0).getString("action_history",""),System.currentTimeMillis(),ActionHistory.UNDONE,label);
            if(!getPreferences(0).edit().putString("action_history",history).remove("undo").remove("undo_label").commit())
                throw new IllegalStateException("Не удалось сохранить результат отката");
            onUi(()->{showDialog("Восстановлено","Команды отката выполнены. Проверь результат в системных настройках.");renderTab();});
        });
    }
    /** Export only human-readable action titles and times; never secret shell output. */
    private void exportHistory(){
        List<ActionHistory.Entry> events=ActionHistory.read(getPreferences(0).getString("action_history",""));
        if(events.isEmpty()){showError("Журнал пуст — экспортировать нечего.");return;}
        StringBuilder report=new StringBuilder("LEGION CONTROL / ЖУРНАЛ ИЗМЕНЕНИЙ\n");
        report.append("Записи информативны: откат доступен только для последнего изменения.\n\n");
        SimpleDateFormat format=new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault());
        for(ActionHistory.Entry item:events){
            String label=item.type.equals(ActionHistory.APPLIED)?"ПРИМЕНЕНО":item.type.equals(ActionHistory.UNDONE)?"ОТМЕНЕНО":"ВОССТАНОВЛЕНО";
            report.append(format.format(new Date(item.timestamp))).append("  ").append(label).append("  ")
                .append(item.label.replace('\n',' ')).append('\n');
        }
        lastHistoryExport=report.toString();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE,"legion_history_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".txt");
        startActivityForResult(intent,3102);
    }
    private void exportDiagnostic(){
        executeAsync(()->{
            lastDiagnostic="LEGION CONTROL 1.7.0 / READ ONLY\n"+new Date()+"\n\n"+
                    exec("id; getprop ro.product.model; getprop ro.boot.hardware.sku; getprop ro.soc.model; getprop ro.build.version.release")+"\n\nDISPLAY / POWER\n"+
                    exec("settings get global low_power; settings get system min_refresh_rate; settings get system peak_refresh_rate; wm size; wm density")+"\n\nGAME MODES\n"+
                    exec("cmd game list-modes com.riotgames.league.wildrift; cmd game list-configs com.riotgames.league.wildrift")+"\n\nOVERLAYS\n"+
                    exec("cmd overlay list --user 0 | grep -E 'com.android.theme.font' ")+"\n\nAPP OPS\n"+
                    exec("cmd appops get com.termux RUN_ANY_IN_BACKGROUND; cmd appops get org.telegram.messenger RUN_ANY_IN_BACKGROUND");
            onUi(()->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TITLE,"legion_control_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".txt");
                startActivityForResult(i,3101);
            });
        });
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if((req==3101||req==3102)&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
                if(stream==null)throw new IllegalStateException("Не удалось открыть файл");
                String content=req==3102?lastHistoryExport:lastDiagnostic;
                stream.write(content.getBytes(StandardCharsets.UTF_8));
                showDialog("Экспорт готов","TXT сохранён в выбранную папку.");
            }catch(Exception e){showError("Ошибка экспорта: "+e.getMessage());}
        }
    }
    private void confirm(String title,String message,Runnable yes){
        showActionDialog(title, message, "Применить", yes, "Отмена", null, false);
    }
    private void showDialog(String title,String content){
        showActionDialog(title, content==null||content.isEmpty()?"Пустой ответ":content, "OK", null,
                needsCopy(content)?"Копировать":null, needsCopy(content)?()->copyText(content):null, isMonospaceContent(content));
    }
    private void showError(String s){
        showActionDialog("Операция не выполнена", s, "Понятно", null, null, null, false);
    }
    /** Structured detail sheet: overview first, technical dump only on request. */
    private void showStructuredResults(String title,SystemInspector.Result result){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(20),dp(20),dp(17));
        panel.setBackground(round(CARD,24,BORDER));

        TextView heading=text(title,23,WHITE,true);
        panel.addView(heading);
        TextView summary=text(result.headline,16,PURPLE,true);
        summary.setPadding(0,dp(12),0,dp(5));
        panel.addView(summary);
        TextView hint=text(result.description,13,MUTED,false);
        hint.setPadding(0,0,0,dp(13));
        panel.addView(hint);

        ScrollView scroll=new ScrollView(this){
            @Override protected void onMeasure(int w,int h){
                int limit=(int)(getResources().getDisplayMetrics().heightPixels*0.55f);
                super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        LinearLayout rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for(SystemInspector.Item item:result.items){
            LinearLayout entry=new LinearLayout(this);
            entry.setPadding(dp(14),dp(13),dp(14),dp(12));
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setBackground(round(RAISED,14,BORDER));
            boolean isDevice="Данные устройства".equals(title);
            if(isDevice){
                TextView name=text(item.name,12,MUTED,false);
                entry.addView(name);
                TextView value=text(item.state,17,WHITE,true);
                value.setPadding(0,dp(5),0,0);
                value.setTextIsSelectable(true);
                entry.addView(value);
            }else{
                LinearLayout top=new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                TextView name=text(item.name,15,WHITE,true);
                top.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
                TextView state=text(item.state,12,item.positive?GREEN:WARN,true);
                state.setGravity(Gravity.END);
                top.addView(state);
                entry.addView(top);
            }
            if(!item.detail.isEmpty()){
                TextView detail=text(item.detail,12,MUTED,false);
                detail.setPadding(0,dp(5),0,0);
                entry.addView(detail);
            }
            rows.addView(entry,withTop(dp(7)));
        }
        if(result.items.isEmpty()){
            TextView empty=text("Нет элементов для отображения. Проверь доступ Shizuku или открой технические сведения.",14,MUTED,false);
            rows.addView(empty,withTop(dp(9)));
        }
        TextView technical=text(result.technical,12,0xFFD1D0DD,false);
        technical.setTextIsSelectable(true);
        technical.setTypeface(Typeface.MONOSPACE);
        technical.setPadding(dp(10),dp(11),dp(10),dp(11));
        technical.setBackground(round(RAISED,12,BORDER));
        technical.setVisibility(View.GONE);
        rows.addView(technical,withTop(dp(8)));
        scroll.addView(rows);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        Button details=dialogButton("Технические сведения",false,()->{
            boolean show=technical.getVisibility()!=View.VISIBLE;
            technical.setVisibility(show?View.VISIBLE:View.GONE);
        });
        panel.addView(details,withTop(dp(13)));
        LinearLayout buttons=new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button copy=dialogButton("Копировать",false,()->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Legion Control",result.technical));
            dialog.dismiss();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.rightMargin=dp(9);
        buttons.addView(copy,lp);
        Button close=dialogButton("Закрыть",true,dialog::dismiss);
        buttons.addView(close,new LinearLayout.LayoutParams(0,-2,1f));
        panel.addView(buttons,withTop(dp(9)));
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);
        frame.setPadding(dp(13),dp(10),dp(13),dp(10));
        frame.addView(panel);
        dialog.setContentView(frame);
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs=dialog.getWindow().getAttributes();
            attrs.width=WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height=WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount=0.65f;
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setAttributes(attrs);
        }
    }
    private void showActionDialog(String title,String content,String primaryLabel,Runnable primary,String secondaryLabel,Runnable secondary,boolean mono){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(18),dp(20),dp(16));
        panel.setBackground(round(CARD,22,BORDER));

        TextView titleView=text(title,24,WHITE,true);
        titleView.setPadding(0,0,0,dp(8));
        panel.addView(titleView);

        ScrollView messageScroll=new ScrollView(this){
            @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
                int limit=Math.min(dp(360),(int)(getResources().getDisplayMetrics().heightPixels*0.58f));
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));
            }
        };
        messageScroll.setFillViewport(false);
        LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,-2);
        TextView body=text(content,15,0xFFD8D7E4,false);
        body.setTextIsSelectable(true);
        body.setEllipsize(null);
        body.setPadding(0,0,0,0);
        if(mono) body.setTypeface(Typeface.MONOSPACE);
        messageScroll.addView(body,new ViewGroup.LayoutParams(-1,-2));
        panel.addView(messageScroll,messageLp);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0,dp(16),0,0);
        panel.addView(actions,new LinearLayout.LayoutParams(-1,-2));

        if(!TextUtils.isEmpty(secondaryLabel)){
            Button secondaryButton=dialogButton(secondaryLabel,false,()->{
                dialog.dismiss();
                if(secondary!=null) secondary.run();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
            lp.rightMargin=dp(10);
            actions.addView(secondaryButton,lp);
        }
        Button primaryButton=dialogButton(primaryLabel,true,()->{
            dialog.dismiss();
            if(primary!=null) primary.run();
        });
        actions.addView(primaryButton,new LinearLayout.LayoutParams(TextUtils.isEmpty(secondaryLabel)?-1:0,-2,TextUtils.isEmpty(secondaryLabel)?0f:1f));

        int outer=dp(16);
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);
        frame.setPadding(outer, outer, outer, outer);
        frame.addView(panel,new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        dialog.setContentView(frame);

        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes=dialog.getWindow().getAttributes();
            attributes.dimAmount=0.68f;
            attributes.width=WindowManager.LayoutParams.MATCH_PARENT;
            attributes.height=WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(attributes);
        }
    }
    private Button dialogButton(String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(primary?BG:WHITE);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinHeight(dp(48));b.setPadding(dp(14),dp(10),dp(14),dp(10));
        b.setBackground(round(primary?PURPLE:RAISED,13,primary?PURPLE:0xFF303039));
        b.setOnClickListener(v->action.run());
        return b;
    }
    private boolean needsCopy(String content){ return content!=null && content.length()>120; }
    private boolean isMonospaceContent(String content){
        if(content==null) return false;
        return content.contains("package:")||content.contains("getprop")||content.contains("[")||content.contains("===")||content.contains("uid=")||content.contains("\n• ");
    }
    private void copyText(String content){
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("legion-control", content));
        showActionDialog("Скопировано", "Текст окна скопирован в буфер обмена.", "OK", null, null, null, false);
    }
    private String formatDialogContent(String title,String raw){
        String content=(raw==null?"":raw.trim());
        if(content.isEmpty()) return "Пустой ответ";
        if("Модули Motorola".equals(title)) return formatMotorolaModules(content);
        if("Системные шрифты".equals(title)) return formatFontOverlays(content);
        return content;
    }
    private String formatMotorolaModules(String raw){
        StringBuilder out=new StringBuilder();
        String current=null;
        for(String line:raw.split("\n")){
            line=line.trim();
            if(line.startsWith("===")){
                current=line.substring(3);
                String label=current.substring(current.lastIndexOf('.')+1)
                        .replace("gamemode","GameMode")
                        .replace("freeform","Freeform")
                        .replace("mobiledesktop","Mobile Desktop")
                        .replace("batterycare","Battery Care")
                        .replace("personalize","Personalize");
                if(out.length()>0) out.append("\n\n");
                out.append("• ").append(label);
            } else if(line.startsWith("package:")) {
                out.append("\n  ✓ Установлен");
            }
        }
        return out.length()==0?raw:out.toString();
    }
    private String formatFontOverlays(String raw){
        StringBuilder out=new StringBuilder();
        out.append("Доступные системные шрифты:\n\n");
        for(String line:raw.split("\n")){
            line=line.trim();
            if(line.isEmpty()) continue;
            boolean active=line.startsWith("[x]");
            String pkg=line.replace("[x]","").replace("[ ]","").trim();
            String label=pkg.substring(pkg.lastIndexOf('.')+1);
            out.append(active?"• Активный: ":"• ").append(label).append("\n");
        }
        return out.toString().trim();
    }
    private void onUi(Runnable r){runOnUiThread(r);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable round(int fill,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(17),dp(18),dp(17),dp(16));v.setBackground(round(CARD,20,BORDER));return v;}
    private LinearLayout.LayoutParams withTop(int mt){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=mt;return p;}
    private TextView text(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(2),1.0f);if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    private TextView line(LinearLayout c,String name,String value){
        TextView n=text(name,11,PURPLE,true);n.setLetterSpacing(0.05f);c.addView(n,withTop(dp(6)));
        TextView v=text(value,14,WHITE,false);v.setPadding(0,dp(3),0,dp(11));v.setTextIsSelectable(true);c.addView(v);return v;
    }
    private Button button(LinearLayout parent,String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(primary?BG:WHITE);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);b.setMinHeight(dp(52));b.setPadding(dp(13),dp(11),dp(13),dp(11));
        b.setBackground(round(primary?PURPLE:RAISED,13,primary?PURPLE:0xFF303039));
        LinearLayout.LayoutParams lp=withTop(dp(10));parent.addView(b,lp);
        b.setOnClickListener(v->{if(busy){showError("Дождись завершения предыдущего действия.");return;}action.run();});
        if(container!=null)actionButtons.add(b);
        return b;
    }
    private void updateBusyState(){
        for(Button b:actionButtons) {
            // Respect controls intentionally disabled by their own content state.
            if(busy) {
                if(b.getTag()==null) b.setTag(Boolean.valueOf(b.isEnabled()));
                b.setEnabled(false);
                b.setAlpha(0.55f);
            } else {
                if(b.getTag() instanceof Boolean) b.setEnabled((Boolean)b.getTag());
                b.setTag(null);
                b.setAlpha(b.isEnabled()?1f:0.45f);
            }
        }
    }
}
