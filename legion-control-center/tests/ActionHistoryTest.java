package dev.legion.control;

import java.util.List;

public final class ActionHistoryTest {
    static int checks;
    static void ok(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    static void invalid(Runnable r) {
        boolean threw=false;
        try {r.run();}catch(IllegalArgumentException e){threw=true;}
        ok(threw);
    }
    public static void main(String[] args) {
        long t=1780000000000L;
        String history=ActionHistory.append("", t, ActionHistory.APPLIED, "Профиль: Для игр");
        List<ActionHistory.Entry> items=ActionHistory.read(history);
        ok(items.size()==1);
        ok(items.get(0).timestamp==t);
        ok(items.get(0).type.equals(ActionHistory.APPLIED));
        ok(items.get(0).label.equals("Профиль: Для игр"));

        history=ActionHistory.append(history,t+1000,ActionHistory.UNDONE,"Профиль: Для игр");
        items=ActionHistory.read(history);
        ok(items.size()==2);
        ok(items.get(0).type.equals(ActionHistory.UNDONE));
        ok(items.get(1).type.equals(ActionHistory.APPLIED));

        // Roundtrip labels containing delimiters and newlines, without corrupting other entries.
        history=ActionHistory.append(history,t+2000,ActionHistory.RECOVERED,"Шрифт | восстановлен\nпроверено");
        items=ActionHistory.read(history);
        ok(items.get(0).label.equals("Шрифт | восстановлен\nпроверено"));
        ok(items.get(1).type.equals(ActionHistory.UNDONE));

        for(int i=0;i<100;i++) history=ActionHistory.append(history,t+3000+i,ActionHistory.APPLIED,"Test "+i);
        items=ActionHistory.read(history);
        ok(items.size()==ActionHistory.LIMIT);
        ok(items.get(0).label.equals("Test 99"));
        ok(items.get(items.size()-1).label.equals("Test "+(100-ActionHistory.LIMIT)));
        ok(ActionHistory.read("corrupted\n"+history+"\nnot|valid|%%").size()==ActionHistory.LIMIT);
        ok(ActionHistory.read(null).isEmpty());
        ok(ActionHistory.read("").isEmpty());

        invalid(()->ActionHistory.append("",0,ActionHistory.APPLIED,"Hello"));
        invalid(()->ActionHistory.append("",t,"UNKNOWN","Hello"));
        invalid(()->ActionHistory.append("",t,ActionHistory.APPLIED,""));
        invalid(()->ActionHistory.append("",t,ActionHistory.APPLIED," "));
        invalid(()->ActionHistory.append("",t,ActionHistory.APPLIED,"X".repeat(141)));
        System.out.println("ActionHistoryTest PASS ("+checks+" checks)");
    }
}
