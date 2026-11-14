package dev.legion.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class AccessStatusTest {
    private static int checks;
    private static void check(boolean condition,String name) {
        checks++;
        if(!condition)throw new AssertionError(name);
    }
    private static AccessStatus.Service find(List<AccessStatus.Service> items,String id){
        for(AccessStatus.Service service:items)if(service.id.equals(id))return service;
        throw new AssertionError("Missing service: "+id);
    }
    public static void main(String[] args) {
        AccessStatus.Record pdd=new AccessStatus.Record(
            "dev.pdd/.TranslationService","dev.pdd","Перевод экрана","PDD · Перевод экрана",false);
        AccessStatus.Record kimi=new AccessStatus.Record(
            "ai.kimi.claw/.AccessibilityService","ai.kimi.claw","Kimi Claw","Kimi Claw",false);
        AccessStatus.Record clone=new AccessStatus.Record(
            "bin.mt.plus/.ActivityService","bin.mt.plus","Список активности (Clone)","MT Manager",false);
        AccessStatus.Record scene=new AccessStatus.Record(
            "com.omarea.vtools/.SceneService","com.omarea.vtools","SCENE Accessibility","SCENE",false);
        AccessStatus.Record shizuku=new AccessStatus.Record(
            "moe.shizuku.privileged.api/.AccessibilityHelper","moe.shizuku.privileged.api","Accessibility Helper","Shizuku",false);
        AccessStatus.Record system=new AccessStatus.Record(
            "com.android.settings/.Service","com.android.settings","System service","Настройки",true);
        List<AccessStatus.Record> records=Arrays.asList(pdd,kimi,clone,scene,shizuku,system);
        Map<String,Boolean> verdict=new HashMap<>();
        verdict.put(kimi.packageName,false); // explicitly rejected: restricted by Android
        verdict.put(clone.packageName,true); // explicitly allowed, simply off
        verdict.put(scene.packageName,true); // disabled, can turn on
        verdict.put(shizuku.packageName,true);
        List<AccessStatus.Service> out=AccessStatus.resolve(
            records,new HashSet<>(Arrays.asList(pdd.id)),true,verdict);
        check(out.size()==6,"all distinct services present");
        check(find(out,pdd.id).state==AccessStatus.State.ENABLED,"PDD enabled");
        check(!AccessStatus.showManualGuide(find(out,pdd.id).state),"enabled no wizard");
        check(find(out,kimi.id).state==AccessStatus.State.RESTRICTED,"Kimi not mistaken for plain off");
        check(AccessStatus.showManualGuide(find(out,kimi.id).state),"blocked gets wizard");
        check(find(out,clone.id).state==AccessStatus.State.DISABLED,"MT clone plain off");
        check(!AccessStatus.showManualGuide(find(out,clone.id).state),"plain off has no restriction wizard");
        check(find(out,scene.id).state==AccessStatus.State.DISABLED,"SCENE plain off");
        check(find(out,shizuku.id).state==AccessStatus.State.DISABLED,"Shizuku plain off");
        check(out.get(out.size()-1).system,"system service last");
        check(AccessStatus.label(AccessStatus.State.ENABLED).equals("Включена"),"enabled label");
        check(AccessStatus.label(AccessStatus.State.DISABLED).contains("доступна"),"plain off labelled available");
        check(AccessStatus.label(AccessStatus.State.RESTRICTED).equals("Ограничена Android"),"blocked label");
        check(AccessStatus.label(AccessStatus.State.DISABLED_UNVERIFIED).contains("не проверен"),"uncertain label");
        check(AccessStatus.label(AccessStatus.State.UNKNOWN).equals("Не удалось проверить"),"unreadable label");
        check(AccessStatus.restrictionDetail(AccessStatus.State.RESTRICTED).contains("администратора"),"false cannot be called ECM exclusively");
        check(AccessStatus.restrictionDetail(AccessStatus.State.DISABLED).contains("выключена"),"allowed-off detail");
        check(AccessStatus.restrictionDetail(AccessStatus.State.ENABLED).contains("включена"),"enabled detail");
        check(!AccessStatus.showManualGuide(AccessStatus.State.UNKNOWN),"unknown enabled listing no wizard");
        check(AccessStatus.showManualGuide(AccessStatus.State.DISABLED_UNVERIFIED),"unknown restriction offers neutral assistance");

        List<AccessStatus.Service> without=AccessStatus.resolve(records,new HashSet<>(Arrays.asList(pdd.id)),true);
        check(find(without,kimi.id).state==AccessStatus.State.DISABLED_UNVERIFIED,"hidden API failure does not fabricate ECM verdict");
        check(find(without,clone.id).state==AccessStatus.State.DISABLED_UNVERIFIED,"hidden API failure does not fabricate unrestricted verdict");
        check(find(without,pdd.id).state==AccessStatus.State.ENABLED,"enabled unaffected by lack of ECM verdict");

        List<AccessStatus.Service> unreadable=AccessStatus.resolve(records,null,false,verdict);
        check(unreadable.stream().allMatch(x->x.state==AccessStatus.State.UNKNOWN),"enabled-list failure dominates verdict");
        Map<String,Boolean> broken=new HashMap<>();
        broken.put(kimi.packageName,null);
        check(find(AccessStatus.resolve(records,new HashSet<>(),true,broken),kimi.id).state==
            AccessStatus.State.DISABLED_UNVERIFIED,"null verdict unknown");
        List<AccessStatus.Record> dup=new ArrayList<>(records);
        dup.add(kimi);
        check(AccessStatus.resolve(dup,new HashSet<>(),true,verdict).size()==6,"duplicate service removed");
        dup.add(new AccessStatus.Record("bad-id","x;reboot","bad","bad",false));
        check(AccessStatus.resolve(dup,new HashSet<>(),true,verdict).size()==6,"invalid pkg ignored");
        check(!AccessStatus.validPackage("com.example;reboot"),"command injection rejected");
        check(!AccessStatus.validPackage("notapackage"),"single segment rejected");
        check(AccessStatus.validPackage("com.android.settings"),"valid package");
        check(AccessStatus.resolve(null,null,false).isEmpty(),"null inventory safe");
        check(AccessStatus.resolve(new ArrayList<>(),new HashSet<>(),true).isEmpty(),"empty inventory safe");
        System.out.println("AccessStatusTest PASS ("+checks+" checks)");
    }
}
