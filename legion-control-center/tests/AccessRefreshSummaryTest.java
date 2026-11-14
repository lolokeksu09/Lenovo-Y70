package dev.legion.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class AccessRefreshSummaryTest {
    private static int checks=0;
    private static void ok(boolean expression, String message){
        checks++;
        if(!expression)throw new AssertionError(message);
    }
    private static AccessStatus.Record record(String id, String pkg){
        return new AccessStatus.Record(id,pkg,"Test Service","Test",false);
    }
    public static void main(String[] args){
        AccessStatus.Record one=record("org.example.one/.Svc","org.example.one");
        AccessStatus.Record two=record("org.example.two/.Svc","org.example.two");
        List<AccessStatus.Service> initial=AccessStatus.resolve(
            Arrays.asList(one,two),Collections.singleton(one.id),true);
        List<AccessStatus.Service> reordered=AccessStatus.resolve(
            Arrays.asList(two,one),Collections.singleton(one.id),true);
        ok(!AccessRefreshSummary.changed(initial,reordered),"Unchanged state is not a change");
        ok(AccessRefreshSummary.summary(true,2,false).contains("Изменений нет"),
           "Completing unchanged refresh must give visible result");
        HashMap<String,Boolean> eligible=new HashMap<>();
        eligible.put(two.packageName,false);
        List<AccessStatus.Service> blocked=AccessStatus.resolve(
            Arrays.asList(one,two),Collections.singleton(one.id),true,eligible);
        ok(AccessRefreshSummary.changed(initial,blocked),"Permission change detected");
        List<AccessStatus.Service> removed=AccessStatus.resolve(
            Collections.singleton(one),Collections.singleton(one.id),true);
        ok(AccessRefreshSummary.changed(initial,removed),"Missing service detected");
        List<AccessStatus.Service> added=AccessStatus.resolve(
            Arrays.asList(one,two,record("org.example.three/.Svc","org.example.three")),
            Collections.singleton(one.id),true);
        ok(AccessRefreshSummary.changed(initial,added),"New service detected");
        ok(AccessRefreshSummary.changed(null,initial),"Initial refresh is new");
        ok(!AccessRefreshSummary.changed(null,null),"No list has no changes");
        ok(AccessRefreshSummary.summary(false,0,false).contains("не предоставил"),
           "An unreadable API response must not claim refresh succeeded");
        ok(AccessRefreshSummary.summary(true,2,true).contains("Обнаружены изменения"),
           "Changed state must be announced");
        ok(AccessRefreshSummary.summary(true,0,false).contains("Изменений нет"),
           "Valid empty inventory must report completion");
        System.out.println("AccessRefreshSummaryTest PASS ("+checks+" checks)");
    }
}
