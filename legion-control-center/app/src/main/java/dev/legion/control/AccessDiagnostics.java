package dev.legion.control;

import java.util.Collections;
import java.util.List;

/** Human-readable, opt-in diagnostics. Never includes command output, logs or device identifiers. */
public final class AccessDiagnostics {
    private AccessDiagnostics() {}

    public static final class Data {
        public String version="";
        public String model="";
        public String androidVersion="";
        public boolean readable;
        public List<AccessStatus.Service> services=Collections.emptyList();
        public String selectedId="";
        public int manualRefreshes;
        public int buttonTests;
        public int timeouts;
        public long lastDurationMs=-1;
        public String lastResult="";
        public String autoProbeSource="";
        public int automaticallyChecked;
    }

    public static String format(Data data){
        if(data==null)throw new IllegalArgumentException("Missing diagnostics input");
        List<AccessStatus.Service> services=data.services==null?Collections.emptyList():data.services;
        int enabled=0,plainOff=0,blocked=0,unverified=0;
        AccessStatus.Service selected=null;
        for(AccessStatus.Service s:services){
            if(s==null)continue;
            if(s.id.equals(data.selectedId))selected=s;
            switch(s.state){
                case ENABLED:enabled++;break;
                case DISABLED:plainOff++;break;
                case RESTRICTED:blocked++;break;
                default:unverified++;break;
            }
        }
        StringBuilder report=new StringBuilder("LEGION CONTROL / ДИАГНОСТИКА ДОСТУПА\n");
        report.append("Версия: ").append(clean(data.version)).append('\n');
        report.append("Модель (по данным прошивки): ").append(clean(data.model)).append('\n');
        report.append("Android: ").append(clean(data.androidVersion)).append('\n');
        report.append("Список служб доступен: ").append(data.readable?"Да":"Нет").append('\n');
        report.append("Всего служб: ").append(services.size()).append('\n');
        report.append("Включено: ").append(enabled).append('\n');
        report.append("Выключено, разрешено системой: ").append(plainOff).append('\n');
        report.append("Ограничено Android: ").append(blocked).append('\n');
        report.append("Ограничение не подтверждено: ").append(unverified).append('\n');
        report.append("Нажатий самопроверки: ").append(Math.max(0,data.buttonTests)).append('\n');
        report.append("Ручных проверок служб: ").append(Math.max(0,data.manualRefreshes)).append('\n');
        report.append("Тайм-аутов чтения: ").append(Math.max(0,data.timeouts)).append('\n');
        report.append("Последняя длительность проверки: ");
        report.append(data.lastDurationMs>=0?data.lastDurationMs+" мс":"Нет данных").append('\n');
        report.append("Результат: ").append(clean(data.lastResult)).append('\n');
        report.append("Источник автоматической проверки: ").append(clean(data.autoProbeSource)).append('\n');
        report.append("Автоматически проверено через Shizuku: ")
            .append(Math.max(0,data.automaticallyChecked)).append('\n');
        if(selected!=null) {
            report.append("Выбранная служба: ").append(clean(selected.serviceName)).append('\n');
            report.append("Пакет выбранного приложения: ").append(clean(selected.packageName)).append('\n');
            report.append("Статус: ").append(AccessStatus.label(selected.state)).append('\n');
        }
        report.append("\nИнформация собирается локально по запросу пользователя. ");
        report.append("Отчёт не содержит IMEI, серийный номер, полный список пакетов или shell-дампы. ");
        report.append("Системные ограничения могут быть недоступны для чтения на прошивке.\n");
        return report.toString();
    }

    private static String clean(String value){
        if(value==null||value.trim().isEmpty())return "Не определено";
        String text=value.replaceAll("[\\p{Cntrl}]+"," ").trim();
        return text.length()>140?text.substring(0,140):text;
    }
}
