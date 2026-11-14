package dev.legion.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public final class AccessStatusTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++;
        if(!condition)throw new AssertionError(name);
    }
    public static void main(String[] args) {
        AccessStatus.Record pdd=new AccessStatus.Record(
            "dev.pdd/.TranslationService", "dev.pdd", "Перевод экрана",
            "PDD · Перевод экрана", false);
        AccessStatus.Record scene=new AccessStatus.Record(
            "com.omarea.vtools/.SceneService", "com.omarea.vtools", "SCENE Accessibility",
            "SCENE", false);
        AccessStatus.Record shizuku=new AccessStatus.Record(
            "moe.shizuku.privileged.api/.AccessibilityHelper","moe.shizuku.privileged.api",
            "Accessibility Helper", "Shizuku", false);
        AccessStatus.Record system=new AccessStatus.Record(
            "com.android.settings/.Service", "com.android.settings", "System service",
            "Настройки", true);
        List<AccessStatus.Record> records=Arrays.asList(pdd,scene,shizuku,system);
        List<AccessStatus.Service> out=AccessStatus.resolve(records,
            new HashSet<>(Arrays.asList(pdd.id)),true);
        check(out.size()==4,"all distinct records present");
        AccessStatus.Service pddFound=find(out,pdd.id);
        check(pddFound.state==AccessStatus.State.ENABLED,"PDD enabled when present");
        check(!AccessStatus.showManualGuide(pddFound.state),"no fix wizard for enabled service");
        check(find(out,scene.id).state==AccessStatus.State.DISABLED,"SCENE disabled");
        check(AccessStatus.showManualGuide(find(out,scene.id).state),"disabled gets manual guide");
        check(find(out,shizuku.id).state==AccessStatus.State.DISABLED,"Shizuku optional service not falsely on");
        check(out.get(out.size()-1).system,"system service sorted after downloaded apps");
        check(AccessStatus.label(AccessStatus.State.ENABLED).equals("Включена"),"enabled UX");
        check(AccessStatus.label(AccessStatus.State.DISABLED).equals("Выключена"),"disabled UX");
        check(AccessStatus.label(AccessStatus.State.UNKNOWN).equals("Не удалось проверить"),"unknown UX");
        check(AccessStatus.restrictedStateLabel().contains("Не определяется"),"never claim restriction detection");
        check(!AccessStatus.showManualGuide(AccessStatus.State.UNKNOWN),"unknown should not prompt changing settings");
        List<AccessStatus.Service> unknown=AccessStatus.resolve(records,null,false);
        check(unknown.stream().allMatch(a->a.state==AccessStatus.State.UNKNOWN),"unreadable enabled list");
        List<AccessStatus.Record> dup=new ArrayList<>(records);dup.add(scene);
        check(AccessStatus.resolve(dup,new HashSet<>(),true).size()==4,"duplicate service suppressed");
        dup.add(new AccessStatus.Record("bad-id","x;reboot","bad","bad",false));
        check(AccessStatus.resolve(dup,new HashSet<>(),true).size()==4,"invalid package ignored");
        check(!AccessStatus.validPackage("com.example;reboot"),"package injection refused");
        check(!AccessStatus.validPackage("notapackage"),"single segment refused");
        check(AccessStatus.validPackage("com.android.settings"),"valid settings package");
        check(AccessStatus.resolve(null,null,false).isEmpty(),"null installed list safe");
        check(AccessStatus.resolve(new ArrayList<>(),new HashSet<>(),true).isEmpty(),"empty inventory valid");
        System.out.println("AccessStatusTest PASS ("+checks+" checks)");
    }
    private static AccessStatus.Service find(List<AccessStatus.Service> items,String id){
        for(AccessStatus.Service service:items)if(service.id.equals(id))return service;
        throw new AssertionError("Missing service: "+id);
    }
}
