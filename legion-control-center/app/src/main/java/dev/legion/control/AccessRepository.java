package dev.legion.control;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.Process;
import java.lang.reflect.Method;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Read-only Android APIs; optional protected hidden query degrades to unknown. */
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
            // The public SDK does not expose Android's ECM eligibility. The underlying
            // AccessibilityManager has a read-only, hidden isAccessibilityTargetAllowed
            // method. Try it without modifying hidden-API policy. If the ROM denies this
            // call, do not infer that the service is unrestricted or blocked.
            Map<String,Boolean> allowedTargets=new HashMap<>();
            if(enabled!=null){
                Method targetMethod=null;
                try {
                    targetMethod=AccessibilityManager.class.getMethod(
                        "isAccessibilityTargetAllowed",String.class,int.class,int.class);
                }catch(ReflectiveOperationException|RuntimeException ignored){}
                if(targetMethod!=null){
                    int userId=Process.myUserHandle().getIdentifier();
                    for(AccessStatus.Record record:records){
                        if(enabledIds.contains(record.id)||allowedTargets.containsKey(record.packageName))continue;
                        try{
                            ApplicationInfo app=pm.getApplicationInfo(record.packageName,0);
                            Object answer=targetMethod.invoke(manager,record.packageName,app.uid,userId);
                            if(answer instanceof Boolean)allowedTargets.put(record.packageName,(Boolean)answer);
                        }catch(ReflectiveOperationException|RuntimeException ignored){
                            // Hidden API denied, missing package or vendor implementation.
                            // Preserve UNKNOWN classification for this package.
                        }
                    }
                }
            }
            List<AccessStatus.Service> result=AccessStatus.resolve(records,enabledIds,enabled!=null,allowedTargets);
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
