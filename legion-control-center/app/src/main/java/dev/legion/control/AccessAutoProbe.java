package dev.legion.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure-Java trust boundary for read-only shell eligibility responses. */
public final class AccessAutoProbe {
    private AccessAutoProbe(){}

    public static final class Targets {
        public final String[] packages;
        public final int[] uids;
        Targets(String[] packages,int[] uids) {
            this.packages=packages;
            this.uids=uids;
        }
    }

    public static final class Result {
        public final List<AccessStatus.Service> services;
        public final int confirmed;
        public final String explanation;
        Result(List<AccessStatus.Service> services,int confirmed,String explanation){
            this.services=services;
            this.confirmed=confirmed;
            this.explanation=explanation;
        }
    }

    /** No privileged query is sent for an enabled or already-resolved service. */
    public static Targets targets(List<AccessStatus.Service> services) {
        if(services==null)return new Targets(new String[0],new int[0]);
        Map<String,Integer> unique=new HashMap<>();
        for(AccessStatus.Service service:services){
            if(service==null||service.state!=AccessStatus.State.DISABLED_UNVERIFIED||
                    !AccessStatus.validPackage(service.packageName)||service.uid<0)
                continue;
            if(unique.size()>=64)break;
            unique.putIfAbsent(service.packageName,service.uid);
        }
        List<String> names=new ArrayList<>(unique.keySet());
        Collections.sort(names);
        String[] packages=names.toArray(new String[0]);
        int[] uids=new int[packages.length];
        for(int i=0;i<packages.length;i++)uids[i]=unique.get(packages[i]);
        return new Targets(packages,uids);
    }

    /** Reject unsolicited packages, conflicting answers and malformed records. */
    public static Result apply(List<AccessStatus.Service> existing,String raw) {
        if(existing==null)return new Result(Collections.emptyList(),0,"Android не вернул список служб");
        if(raw==null||raw.startsWith("ERROR\t"))
            return new Result(existing,0,"Shizuku: системный интерфейс проверки недоступен");
        Targets targets=targets(existing);
        if(targets.packages.length==0)
            return new Result(existing,0,"Нет служб, требующих дополнительной проверки");
        Set<String> expected=new HashSet<>();
        Collections.addAll(expected,targets.packages);
        Map<String,Boolean> verdicts=new HashMap<>();
        Set<String> conflicts=new HashSet<>();
        for(String line:raw.split("\\r?\\n")){
            String[] pair=line.split("\t",-1);
            if(pair.length!=2 || !expected.contains(pair[1]))continue;
            Boolean allowed=pair[0].equals("ALLOW")?Boolean.TRUE:
                    pair[0].equals("BLOCKED")?Boolean.FALSE:null;
            if(allowed==null||conflicts.contains(pair[1]))continue;
            Boolean prior=verdicts.putIfAbsent(pair[1],allowed);
            if(prior!=null && !prior.equals(allowed)){
                verdicts.remove(pair[1]);
                conflicts.add(pair[1]);
            }
        }
        if(verdicts.isEmpty())
            return new Result(existing,0,"Shizuku: Android не подтвердил состояние ограничений");

        Set<String> enabledIds=new HashSet<>();
        List<AccessStatus.Record> records=new ArrayList<>();
        Map<String,Boolean> allKnown=new HashMap<>();
        for(AccessStatus.Service service:existing){
            if(service==null)continue;
            records.add(new AccessStatus.Record(service.id,service.packageName,service.serviceName,
                    service.applicationName,service.system,service.uid));
            if(service.state==AccessStatus.State.ENABLED)enabledIds.add(service.id);
            else if(service.state==AccessStatus.State.DISABLED)allKnown.put(service.packageName,true);
            else if(service.state==AccessStatus.State.RESTRICTED)allKnown.put(service.packageName,false);
        }
        allKnown.putAll(verdicts);
        boolean enabledReadable=true;
        for(AccessStatus.Service service:existing){
            if(service.state==AccessStatus.State.UNKNOWN){enabledReadable=false;break;}
        }
        if(!enabledReadable)return new Result(existing,0,"Список активных служб Android недоступен");
        List<AccessStatus.Service> updated=AccessStatus.resolve(records,enabledIds,true,allKnown);
        return new Result(updated,verdicts.size(),"Shizuku: автоматически проверено "+verdicts.size()+" приложений");
    }
}
