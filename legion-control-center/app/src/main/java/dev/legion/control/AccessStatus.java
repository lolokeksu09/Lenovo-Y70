package dev.legion.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two separate questions:
 * 1) Is an installed Accessibility service enabled?
 * 2) If disabled, does Android currently permit enabling it?
 *
 * An unknown second answer MUST NOT be labelled as unrestricted or blocked.
 * A denied target may also be blocked by an administrator; it is not proof
 * that ECM/Restricted Settings specifically caused the denial.
 */
public final class AccessStatus {
    private AccessStatus() {}

    public enum State { ENABLED, DISABLED, RESTRICTED, DISABLED_UNVERIFIED, UNKNOWN }

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

    /** Default is strictly uncertain for an off service; not a restriction detector. */
    public static List<Service> resolve(List<Record> installed, Set<String> enabledIds, boolean enabledListReadable) {
        return resolve(installed, enabledIds, enabledListReadable, Collections.emptyMap());
    }

    /** Boolean.TRUE means Android allows this service's package to be enabled.
     * Boolean.FALSE means Android is preventing it (ECM or device policy).
     * Missing/null means a hidden API was not accessible and no claim can be made.
     */
    public static List<Service> resolve(List<Record> installed, Set<String> enabledIds,
                                        boolean enabledListReadable, Map<String,Boolean> targetsAllowed) {
        if(installed==null) return Collections.emptyList();
        Set<String> enabled=enabledIds==null?Collections.emptySet():enabledIds;
        Map<String,Boolean> allowed=targetsAllowed==null?Collections.emptyMap():targetsAllowed;
        Set<String> seen=new HashSet<>();
        List<Service> result=new ArrayList<>();
        for(Record r:installed) {
            if(r==null||r.id==null||r.id.trim().isEmpty()||
                    !validPackage(r.packageName)||!seen.add(r.id))continue;
            State state;
            if(!enabledListReadable)state=State.UNKNOWN;
            else if(enabled.contains(r.id))state=State.ENABLED;
            else {
                Boolean target=allowed.get(r.packageName);
                state=target==null?State.DISABLED_UNVERIFIED:
                        target?State.DISABLED:State.RESTRICTED;
            }
            result.add(new Service(r,state));
        }
        result.sort((a,b)->{
            if(a.system!=b.system)return a.system?1:-1;
            int app=a.applicationName.compareToIgnoreCase(b.applicationName);
            return app!=0?app:a.serviceName.compareToIgnoreCase(b.serviceName);
        });
        return Collections.unmodifiableList(result);
    }

    public static String label(State state) {
        if(state==null)return "Не удалось проверить";
        switch(state) {
            case ENABLED:return "Включена";
            case DISABLED:return "Выключена · доступна";
            case RESTRICTED:return "Ограничена Android";
            case DISABLED_UNVERIFIED:return "Выключена · доступ не проверен";
            default:return "Не удалось проверить";
        }
    }

    public static String restrictionDetail(State state) {
        if(state==State.RESTRICTED)
            return "Android не разрешает включение. Причина может быть в Restricted Settings или политике администратора.";
        if(state==State.DISABLED)
            return "Android допускает включение службы. Сейчас она выключена.";
        if(state==State.ENABLED)
            return "Служба включена; дополнительных действий не требуется.";
        return "Не удалось достоверно проверить системные ограничения на этой прошивке.";
    }

    /** Only actually denied services get the restricted-settings help wizard. */
    public static boolean showManualGuide(State state) {
        // When Android hides its internal check, still offer an explicit
        // troubleshooting path, but never label the service restricted.
        return state==State.RESTRICTED||state==State.DISABLED_UNVERIFIED;
    }

    public static boolean validPackage(String pkg) {
        return pkg!=null&&pkg.length()>=3&&pkg.length()<=255&&pkg.indexOf('.')>0
                &&pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    }
}
