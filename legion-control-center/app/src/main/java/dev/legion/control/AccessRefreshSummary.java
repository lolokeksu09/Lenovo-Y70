package dev.legion.control;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compares real Accessibility service identifiers and states across refreshes. */
public final class AccessRefreshSummary {
    private AccessRefreshSummary() {}

    public static boolean changed(List<AccessStatus.Service> previous,
                                  List<AccessStatus.Service> current) {
        if(previous==null || current==null) return previous!=current;
        if(previous.size()!=current.size())return true;
        Map<String,AccessStatus.State> before=new HashMap<>();
        for(AccessStatus.Service service:previous){
            if(service==null)return true;
            before.put(service.id,service.state);
        }
        for(AccessStatus.Service service:current){
            if(service==null || !before.containsKey(service.id) ||
                    before.get(service.id)!=service.state)return true;
        }
        return false;
    }

    public static String summary(boolean readable, int count, boolean changed) {
        if(!readable)return "Android не предоставил данные — открой настройки и повтори проверку.";
        return changed?"Обнаружены изменения служб или их состояний ("+count+").":
                "Проверка завершена. Изменений нет ("+count+" служб).";
    }
}
