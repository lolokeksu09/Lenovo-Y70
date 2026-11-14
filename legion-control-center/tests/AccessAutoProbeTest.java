package dev.legion.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class AccessAutoProbeTest {
    private static int n;
    private static void ok(boolean b,String msg){n++;if(!b)throw new AssertionError(msg);}
    private static AccessStatus.Service find(List<AccessStatus.Service> list,String pkg){
        for(AccessStatus.Service s:list)if(s.packageName.equals(pkg))return s;
        throw new AssertionError("No pkg "+pkg);
    }
    public static void main(String[] args){
        AccessStatus.Record kimi=new AccessStatus.Record("com.kimi/.A","com.kimi",
            "Kimi Claw","Kimi Claw",false,10211);
        AccessStatus.Record scene=new AccessStatus.Record("com.omarea.vtools/.A","com.omarea.vtools",
            "SCENE","SCENE",false,10212);
        AccessStatus.Record pdd=new AccessStatus.Record("com.pdd/.A","com.pdd",
            "Перевод","PDD",false,10213);
        AccessStatus.Record old=new AccessStatus.Record("com.no.uid/.A","com.no.uid",
            "Old","Old",false);
        List<AccessStatus.Service> services=AccessStatus.resolve(
            Arrays.asList(kimi,scene,pdd,old),
            new HashSet<>(Collections.singleton(pdd.id)),true);
        AccessAutoProbe.Targets targets=AccessAutoProbe.targets(services);
        ok(targets.packages.length==2,"Only unknown off with known UID probed");
        ok(targets.packages[0].equals("com.kimi"),"Deterministic sorted package order");
        ok(targets.uids[0]==10211,"Uses actual PackageManager UID");
        AccessAutoProbe.Result result=AccessAutoProbe.apply(services,
            "BLOCKED\tcom.kimi\nALLOW\tcom.omarea.vtools\n");
        ok(result.confirmed==2,"Two confirmed answers");
        ok(find(result.services,"com.kimi").state==AccessStatus.State.RESTRICTED,
            "Kimi restricted via real Android server");
        ok(find(result.services,"com.omarea.vtools").state==AccessStatus.State.DISABLED,
            "Scene simply off");
        ok(find(result.services,"com.pdd").state==AccessStatus.State.ENABLED,
            "PDD remains enabled");
        ok(find(result.services,"com.no.uid").state==AccessStatus.State.DISABLED_UNVERIFIED,
            "Unknown UID remains unknown");
        ok(result.explanation.contains("автоматически"),"Provenance labelled");
        ok(AccessAutoProbe.apply(services,"ERROR\tAPI_NOT_AVAILABLE").confirmed==0,
            "Unsupported binder never fabricates verdicts");
        ok(AccessAutoProbe.apply(services,"ALLOW\tcom.evil").confirmed==0,
            "Ignore unsolicited package");
        ok(AccessAutoProbe.apply(services,"ALLOW\tcom.kimi\nBLOCKED\tcom.kimi\n").confirmed==0,
            "Conflicting repeated answers rejected");
        ok(AccessAutoProbe.apply(services,"BLOCKED\tcom.kimi\nBLOCKED\tcom.kimi\n").confirmed==1,
            "Identical duplicated answer safe");
        ok(AccessAutoProbe.apply(services,"ERROR\tINVALID_TARGET").services.get(0)
            .state==services.get(0).state,"Failure keeps previous states");
        ok(AccessAutoProbe.apply(Collections.emptyList(),"ALLOW\tcom.kimi").confirmed==0,
            "No arbitrary service state injection");
        ok(AccessAutoProbe.targets(null).packages.length==0,"Null list safe");
        ok(AccessAutoProbe.apply(null,"ALLOW\tcom.kimi").services.isEmpty(),"Null input safe");
        System.out.println("AccessAutoProbeTest PASS ("+n+" checks)");
    }
}
