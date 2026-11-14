package dev.legion.control;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Android public API only; does not request Shizuku or modify accessibility privileges. */
public final class AccessRepository {
    private AccessRepository() {}

    public static final class Snapshot {
        public final List<AccessStatus.Service> services;
        public final boolean readable;
        public final String message;

        Snapshot(List<AccessStatus.Service> services, boolean readable, String message) {
            this.services=services;this.readable=readable;this.message=message;
        }
    }

    public static Snapshot read(Context context) {
        AccessibilityManager manager=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(manager==null)
            return new Snapshot(Collections.emptyList(),false,"Служба Android Accessibility недоступна. Открой системные настройки вручную.");
        try {
            List<AccessibilityServiceInfo> installed=manager.getInstalledAccessibilityServiceList();
            if(installed==null)
                return new Snapshot(Collections.emptyList(),false,"Android не вернул список установленных служб.");
            List<AccessibilityServiceInfo> enabled=manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            Set<String> enabledIds=new HashSet<>();
            if(enabled!=null) {
                for(AccessibilityServiceInfo info:enabled)
                    if(info!=null && info.getId()!=null)enabledIds.add(info.getId());
            }
            PackageManager pm=context.getPackageManager();
            List<AccessStatus.Record> records=new ArrayList<>();
            for(AccessibilityServiceInfo info:installed) {
                if(info==null||info.getId()==null)continue;
                ResolveInfo ri=info.getResolveInfo();
                if(ri==null||ri.serviceInfo==null||ri.serviceInfo.packageName==null)continue;
                String pkg=ri.serviceInfo.packageName;
                if(!AccessStatus.validPackage(pkg))continue;
                String serviceName=safeLabel(ri.loadLabel(pm),ri.serviceInfo.name);
                String appName=pkg;
                boolean system=false;
                ApplicationInfo app=ri.serviceInfo.applicationInfo;
                if(app!=null) {
                    appName=safeLabel(pm.getApplicationLabel(app),pkg);
                    system=(app.flags & (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
                }
                records.add(new AccessStatus.Record(info.getId(),pkg,serviceName,appName,system));
            }
            List<AccessStatus.Service> result=AccessStatus.resolve(records,enabledIds,enabled!=null);
            String message=enabled==null?"Android не подтвердил список включённых служб.":
                    result.isEmpty()?"Службы Accessibility не обнаружены. Список может зависеть от прошивки.":"";
            return new Snapshot(result,true,message);
        }catch(RuntimeException e) {
            return new Snapshot(Collections.emptyList(),false,
                    "Android не разрешил проверить службы. Открой системные настройки и повтори попытку.");
        }
    }

    private static String safeLabel(CharSequence source,String fallback) {
        String value=source==null?"":source.toString().trim();
        if(value.isEmpty())value=fallback==null?"Приложение":fallback;
        if(value.length()>100)value=value.substring(0,100);
        return value;
    }
}
