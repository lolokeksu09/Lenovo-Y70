package dev.legion.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public final class AccessDiagnosticsTest {
    private static int checks;
    private static void check(boolean ok,String message){
        checks++;
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args){
        AccessStatus.Record enabled=new AccessStatus.Record("a.one/.Service","a.one","Service One","One",false);
        AccessStatus.Record blocked=new AccessStatus.Record("b.two/.Service","b.two","Service Two","Two",false);
        AccessStatus.Record allowed=new AccessStatus.Record("c.three/.Service","c.three","Service Three","Three",false);
        AccessStatus.Record unknown=new AccessStatus.Record("d.four/.Service","d.four","Service Four","Four",false);
        HashMap<String,Boolean> verdict=new HashMap<>();
        verdict.put(blocked.packageName,false);
        verdict.put(allowed.packageName,true);
        AccessDiagnostics.Data d=new AccessDiagnostics.Data();
        d.services=AccessStatus.resolve(Arrays.asList(enabled,blocked,allowed,unknown),
            new HashSet<>(Collections.singletonList(enabled.id)),true,verdict);
        d.readable=true;
        d.version="1.8.0-beta8";
        d.model="motorola edge 70 max";
        d.androidVersion="17";
        d.selectedId=blocked.id;
        d.manualRefreshes=3;
        d.buttonTests=2;
        d.timeouts=1;
        d.lastDurationMs=235;
        d.lastResult="Данные обновлены";
        String text=AccessDiagnostics.format(d);
        check(text.contains("Версия: 1.8.0-beta8"),"app version included");
        check(text.contains("Включено: 1"),"enabled count");
        check(text.contains("Выключено, разрешено системой: 1"),"allowed-off count");
        check(text.contains("Ограничено Android: 1"),"restricted count");
        check(text.contains("Ограничение не подтверждено: 1"),"unknown count");
        check(text.contains("Нажатий самопроверки: 2"),"UI tap counter");
        check(text.contains("Ручных проверок служб: 3"),"manual refresh counter");
        check(text.contains("Тайм-аутов чтения: 1"),"timeout counter");
        check(text.contains("Последняя длительность проверки: 235 мс"),"measured duration");
        check(text.contains("Пакет выбранного приложения: b.two"),"selected package, not all apps");
        check(!text.contains("c.three")&&!text.contains("a.one")&&!text.contains("d.four"),
            "other installed package identifiers are excluded");
        check(text.contains("Статус: Ограничена Android"),"selected restriction status");
        d.selectedId="nothing";
        text=AccessDiagnostics.format(d);
        check(!text.contains("Пакет выбранного приложения:"),"no selected package fallback");
        d.services=Collections.emptyList();
        d.readable=false;
        d.lastDurationMs=-1;
        d.model="fake\nadmin: true";
        text=AccessDiagnostics.format(d);
        check(text.contains("Список служб доступен: Нет"),"no read means no fabricated success");
        check(text.contains("Всего служб: 0"),"empty service list");
        check(text.contains("длительность проверки: Нет данных"),"missing timings");
        check(!text.contains("\nadmin: true"),"newlines stripped from vendor supplied fields");
        boolean rejected=false;
        try{AccessDiagnostics.format(null);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"null input rejected");
        System.out.println("AccessDiagnosticsTest PASS ("+checks+" checks)");
    }
}
