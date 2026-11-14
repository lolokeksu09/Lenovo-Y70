package dev.legion.control;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** User-guided Android special access diagnostics: read-only, no hidden bypass. */
public final class AccessCenterActivity extends Activity {
    private static final int BG=0xFF060609;
    private static final int CARD=0xFF121218;
    private static final int RAISED=0xFF202028;
    private static final int BORDER=0xFF292833;
    private static final int WHITE=0xFFF6F5FC;
    private static final int MUTED=0xFFB2ADBF;
    private static final int PURPLE=0xFFC3B3FF;
    private static final int GREEN=0xFF7BE2B2;
    private static final int WARN=0xFFFFC78C;

    private ScrollView scroll;
    private LinearLayout content;
    private AccessRepository.Snapshot snapshot;
    private String selectedId;
    private String navigationWarning;
    private boolean missingRestrictionMenu;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(state!=null){
            selectedId=state.getString("selected_access_service");
            missingRestrictionMenu=state.getBoolean("missing_restriction_menu",false);
        }
        buildRoot();
        showLoading();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh(false);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("selected_access_service",selectedId);
        out.putBoolean("missing_restriction_menu",missingRestrictionMenu);
        super.onSaveInstanceState(out);
    }

    private void buildRoot() {
        scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(26),dp(20),dp(36));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                top=bars.top;bottom=bars.bottom;
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            content.setPadding(dp(20),top+dp(18),dp(20),bottom+dp(36));
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private void refresh(boolean returnTop){
        snapshot=AccessRepository.read(this);
        if(returnTop)selectedId=null;
        render();
    }

    private void showLoading(){
        content.removeAllViews();
        addTitle("Доступ приложений");
        info(content,"Проверка служб специальных возможностей…",MUTED);
    }

    private void render(){
        if(isFinishing()||content==null||snapshot==null)return;
        content.removeAllViews();
        AccessStatus.Service selected=findSelected();
        if(selectedId!=null && selected==null){
            selectedId=null;
            navigationWarning="Служба больше не обнаружена после обновления.";
        }
        if(selectedId==null)renderList();
        else renderDetails(selected);
    }

    private AccessStatus.Service findSelected(){
        if(selectedId==null||snapshot==null)return null;
        for(AccessStatus.Service s:snapshot.services)if(s.id.equals(selectedId))return s;
        return null;
    }

    private void renderList(){
        button(content,"К системным инструментам",false,()->finish());
        addTitle("Доступ приложений");
        info(content,"Управление службами специальных возможностей Android. Статус ограничений установки может быть недоступен.",MUTED);
        warningIfPresent();
        if(!snapshot.readable){
            LinearLayout error=card();
            addHeading(error,"Не удалось проверить");
            info(error,snapshot.message,WARN);
            button(error,"Повторить проверку",true,()->refresh(false));
            button(error,"Открыть спец. возможности",false,this::openAccessibility);
            content.addView(error,top(18));
            return;
        }

        List<AccessStatus.Service> services=snapshot.services;
        int enabled=0,unknown=0;
        for(AccessStatus.Service s:services){
            if(s.state==AccessStatus.State.ENABLED)enabled++;
            if(s.state==AccessStatus.State.UNKNOWN)unknown++;
        }
        LinearLayout summary=card();
        addHeading(summary,"Службы Android");
        info(summary,"Найдено: "+services.size()+"    Включено: "+enabled+
                (unknown>0?"    Неизвестно: "+unknown:""),WHITE);
        button(summary,"Проверить снова",true,()->refresh(false));
        content.addView(summary,top(20));

        if(services.isEmpty()){
            LinearLayout empty=card();
            addHeading(empty,"Службы не найдены");
            info(empty,snapshot.message.isEmpty()?
                    "Android не вернул доступные службы. Проверь настройки непосредственно в системе.":snapshot.message,MUTED);
            button(empty,"Открыть спец. возможности",false,this::openAccessibility);
            content.addView(empty,top(12));
        }else{
            addHeading(content,"Установленные службы");
            if(!snapshot.message.isEmpty())info(content,snapshot.message,WARN);
            for(AccessStatus.Service service:services){
                LinearLayout entry=card();
                TextView name=text(service.applicationName,16,WHITE,true);
                entry.addView(name);
                if(!service.serviceName.equals(service.applicationName))
                    info(entry,service.serviceName,MUTED);
                int color=statusColor(service.state);
                info(entry,AccessStatus.label(service.state),color);
                TextView open=text("Подробнее  ›",13,PURPLE,true);
                open.setGravity(Gravity.END);
                open.setPadding(0,dp(8),0,0);
                entry.addView(open);
                entry.setMinimumHeight(dp(76));
                entry.setFocusable(true);
                entry.setContentDescription(service.applicationName+", "+service.serviceName+", "+
                        AccessStatus.label(service.state)+", открыть подробности");
                entry.setOnClickListener(v->{
                    selectedId=service.id;
                    missingRestrictionMenu=false;
                    navigationWarning=null;
                    render();
                    scroll.smoothScrollTo(0,0);
                });
                content.addView(entry,top(10));
            }
        }
        info(content,"Shizuku через ADB работает независимо от состояния его службы Accessibility. Ничего не включается автоматически.",MUTED);
    }

    private void renderDetails(AccessStatus.Service service){
        button(content,"К списку служб",false,()->{
            missingRestrictionMenu=false;
            selectedId=null;
            render();
            scroll.smoothScrollTo(0,0);
        });
        addTitle(service.applicationName);
        info(content,service.serviceName,MUTED);
        warningIfPresent();

        LinearLayout status=card();
        addHeading(status,"Текущее состояние");
        info(status,AccessStatus.label(service.state),statusColor(service.state));
        info(status,"Ограниченные настройки: "+AccessStatus.restrictedStateLabel(),MUTED);
        info(status,"Невозможно достоверно узнать из обычного API, разрешил ли Android Restricted Settings.",MUTED);
        content.addView(status,top(18));

        if(AccessStatus.showManualGuide(service.state)){
            LinearLayout wizard=card();
            addHeading(wizard,"Как настроить доступ");
            info(wizard,"1. Сначала открой специальные возможности, нажми на серую службу и вызови предупреждение Android об ограниченных настройках. Это важно: до предупреждения пункта в меню может не быть.",WHITE);
            info(wizard,"2. Нажми «Открыть настройки "+service.applicationName+"»: Legion Control откроет карточку именно этого приложения. Искать его в списке не нужно.",WHITE);
            info(wizard,"3. Если появилось ⋮ → «Разрешить ограниченные настройки», подтверди действие Android. Включай службу только доверенного приложения.",WHITE);
            info(wizard,"4. Вернись в специальные возможности, включи службу вручную и перепроверь её статус здесь.",WHITE);
            button(wizard,"1. Открыть специальные возможности",false,this::openAccessibility);
            button(wizard,"2. Настройки "+service.applicationName,true,()->openSelectedAppInfo(service));
            button(wizard,"Пункт отсутствует — открыть "+service.applicationName,false,()->{
                missingRestrictionMenu=true;
                openSelectedAppInfo(service);
            });
            content.addView(wizard,top(12));
            if(missingRestrictionMenu){
                LinearLayout missing=card();
                addHeading(missing,"Если пункта нет");
                info(missing,"Открыта карточка «"+service.applicationName+"». Сначала должно появиться предупреждение Android в специальных возможностях; затем проверь меню ⋮ в карточке приложения.",WHITE);
                info(missing,"Если после этих действий пункт всё ещё отсутствует, возможна неисправность или особенность настроек установленной прошивки Motorola. Это не доказывает, что приложение небезопасно или что ограничение можно снять через Legion Control.",WARN);
                info(missing,"Android 17 использует Enhanced Confirmation: в новых сборках право показать этот пункт определяется системной службой. Обычному приложению недоступно изменение этого состояния.",MUTED);
                info(missing,"Не используй случайные команды ADB или автоматическое включение Accessibility: доступ может разрешать чтение экрана и управление устройством.",MUTED);
                button(missing,"Открыть «"+service.applicationName+"» ещё раз",true,()->openSelectedAppInfo(service));
                info(missing,"Если прямой переход скрывает пункт ⋮ на этой прошивке, попробуй открыть карточку вручную через «Все приложения». Это резервный способ, не обязательный шаг.",MUTED);
                button(missing,"Резерв: Все приложения",false,this::openAppsList);
                button(missing,"Проверить ещё раз",false,()->refresh(false));
                content.addView(missing,top(12));
            }
        }else if(service.state==AccessStatus.State.ENABLED){
            LinearLayout enabled=card();
            addHeading(enabled,"Служба уже включена");
            info(enabled,"Повторно разрешать доступ не требуется. Не меняй настройки, если приложение работает корректно.",GREEN);
            content.addView(enabled,top(12));
        }else{
            LinearLayout unknown=card();
            addHeading(unknown,"Состояние не подтверждено");
            info(unknown,"Нельзя утверждать, что служба выключена или заблокирована. Проверь её напрямую в настройках Android.",WARN);
            button(unknown,"Открыть спец. возможности",false,this::openAccessibility);
            button(unknown,"Настройки "+service.applicationName,false,()->openSelectedAppInfo(service));
            content.addView(unknown,top(12));
        }
        LinearLayout actions=card();
        addHeading(actions,"Проверка и настройки");
        button(actions,"Проверить повторно",true,()->refresh(false));
        button(actions,"Открыть «"+service.applicationName+"» в настройках",false,()->openSelectedAppInfo(service));
        content.addView(actions,top(12));
        info(content,"Legion Control не снимает ограничения Android, не выдаёт разрешения и не запускает ADB-команды для включения Accessibility.",MUTED);
    }

    /** Directly opens the chosen package's App Info. No package lookup by the user. */
    private void openSelectedAppInfo(AccessStatus.Service service){
        if(service==null||!AccessStatus.validPackage(service.packageName)){
            navigationWarning="Нельзя открыть настройки: некорректный идентификатор приложения.";
            render();
            return;
        }
        if(!SettingsNavigator.openAppInfo(this,service.packageName)){
            navigationWarning="Android не открыл карточку «"+service.applicationName+"». Используй резервный список приложений.";
            if(!SettingsNavigator.openAppList(this))render();
        }
    }

    private void openAppsList(){
        if(!SettingsNavigator.openAppList(this)){
            navigationWarning="Не удалось открыть список приложений Android. Перейди вручную: Настройки → Приложения → Все приложения.";
            render();
        }
    }

    private void openAccessibility(){
        boolean opened=SettingsNavigator.openAccessibility(this);
        if(!opened){
            navigationWarning="Android не открыл список специальных возможностей. Перейди в «Настройки» вручную.";
            render();
        }
    }

    private void openAppInfo(String pkg){
        if(!AccessStatus.validPackage(pkg)){
            navigationWarning="Некорректный идентификатор приложения.";
            render();
            return;
        }
        if(!SettingsNavigator.openAppInfo(this,pkg)){
            navigationWarning="Android не открыл информацию о приложении. Используй резервный список.";
            if(!SettingsNavigator.openAppList(this))render();
        }
    }

    private void warningIfPresent(){
        if(navigationWarning!=null){
            LinearLayout warning=card();
            info(warning,navigationWarning,WARN);
            content.addView(warning,top(12));
            navigationWarning=null;
        }
    }

    private void addTitle(String title){
        TextView t=text(title,27,WHITE,true);
        t.setPadding(0,dp(18),0,dp(8));
        content.addView(t);
    }

    private void addHeading(LinearLayout parent,String label){
        TextView t=text(label,18,WHITE,true);
        t.setPadding(0,dp(9),0,dp(7));
        parent.addView(t);
    }

    private void info(LinearLayout parent,String message,int color){
        TextView t=text(message,14,color,false);
        t.setPadding(0,dp(4),0,dp(6));
        parent.addView(t);
    }

    private TextView text(String label,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(label);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setLineSpacing(dp(3),1f);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }

    private int statusColor(AccessStatus.State state){
        return state==AccessStatus.State.ENABLED?GREEN:state==AccessStatus.State.DISABLED?WARN:MUTED;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(17),dp(16),dp(17),dp(17));
        c.setBackground(background(CARD,19,BORDER));
        return c;
    }

    private Button button(LinearLayout parent,String label,boolean primary,Runnable action){
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(primary?BG:WHITE);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(52));
        b.setPadding(dp(10),dp(11),dp(10),dp(11));
        b.setBackground(background(primary?PURPLE:RAISED,13,primary?PURPLE:BORDER));
        b.setOnClickListener(v->action.run());
        parent.addView(b,top(10));
        return b;
    }

    private GradientDrawable background(int fill,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),stroke);
        return g;
    }

    private LinearLayout.LayoutParams top(int pixels){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(pixels);
        return p;
    }

    private int dp(float d){
        return (int)(d*getResources().getDisplayMetrics().density+0.5f);
    }

    @Override public void onBackPressed(){
        if(selectedId!=null){
            selectedId=null;
            missingRestrictionMenu=false;
            render();
            scroll.smoothScrollTo(0,0);
        }else super.onBackPressed();
    }
}
