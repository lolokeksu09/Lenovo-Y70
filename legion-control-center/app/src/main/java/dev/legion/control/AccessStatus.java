package dev.legion.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Non-privileged Accessibility inventory status. Not a Restricted Settings detector. */
public final class AccessStatus {
    private AccessStatus() {}

    public enum State { ENABLED, DISABLED, UNKNOWN }

    public static final class Record {
        public final String id;
        public final String packageName;
        public final String serviceName;
        public final String applicationName;
        public final boolean system;

        public Record(String id, String packageName, String serviceName, String applicationName, boolean system) {
            this.id=id;
            this.packageName=packageName;
            this.serviceName=serviceName;
            this.applicationName=applicationName;
            this.system=system;
        }
    }

    public static final class Service {
        public final String id;
        public final String packageName;
        public final String serviceName;
        public final String applicationName;
        public final boolean system;
        public final State state;

        private Service(Record record, State state) {
            this.id=record.id;
            this.packageName=record.packageName;
            this.serviceName=record.serviceName;
            this.applicationName=record.applicationName;
            this.system=record.system;
            this.state=state;
        }
    }

    public static List<Service> resolve(List<Record> installed, Set<String> enabledIds, boolean enabledListReadable) {
        if(installed==null) return Collections.emptyList();
        Set<String> enabled=enabledIds==null?Collections.emptySet():enabledIds;
        Set<String> seen=new HashSet<>();
        List<Service> result=new ArrayList<>();
        for(Record r:installed) {
            if(r==null || r.id==null || r.id.trim().isEmpty() ||
                    !validPackage(r.packageName) || !seen.add(r.id)) continue;
            State state=!enabledListReadable?State.UNKNOWN:
                    enabled.contains(r.id)?State.ENABLED:State.DISABLED;
            result.add(new Service(r,state));
        }
        result.sort((a,b)->{
            if(a.system!=b.system)return a.system?1:-1;
            int app=a.applicationName.compareToIgnoreCase(b.applicationName);
            return app!=0?app:a.serviceName.compareToIgnoreCase(b.serviceName);
        });
        return Collections.unmodifiableList(result);
    }

    /** Only the Settings screen can confirm whether an Android restriction was lifted. */
    public static String restrictedStateLabel() {
        return "Не определяется автоматически";
    }

    public static String label(State state) {
        if(state==null)return "Не удалось проверить";
        switch(state){
            case ENABLED:return "Включена";
            case DISABLED:return "Выключена";
            default:return "Не удалось проверить";
        }
    }

    public static boolean showManualGuide(State state) {
        return state==State.DISABLED;
    }

    public static boolean validPackage(String pkg) {
        if(pkg==null||pkg.length()<3||pkg.length()>255||pkg.indexOf('.')<=0)return false;
        // Conservative package name for safe ACTION_APPLICATION_DETAILS_SETTINGS navigation.
        return pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    }
}
