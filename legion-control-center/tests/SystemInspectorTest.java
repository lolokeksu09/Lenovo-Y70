package dev.legion.control;
public final class SystemInspectorTest {
    private static int count=0;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    public static void main(String[]args){
        String modules="===com.motorola.gamemode\npackage:/product/priv-app/GameMode/GameMode.apk\n"+
                "===com.motorola.freeform\npackage:/system_ext/priv-app/MotoFreeForm/MotoFreeForm.apk\n"+
                "===com.motorola.mobiledesktop\n"+
                "===com.motorola.batterycare\npackage:/system_ext/priv-app/BatteryCare/BatteryCare.apk\n"+
                "===com.motorola.personalize\npackage:/system_ext/priv-app/Personalize/Personalize.apk";
        SystemInspector.Result m=SystemInspector.motorola(modules);
        check(m.items.size()==5,"all module rows");
        check(m.headline.contains("4 из 5"),"installed count");
        check(m.items.get(0).name.equals("GameMode"),"friendly module name");
        check(m.items.get(2).state.equals("Не найден"),"no invented installation");
        check(!m.items.get(0).detail.contains("/product/"),"path hidden");
        check(m.technical.contains("/product/"),"raw preserved for details");
        check(SystemInspector.motorola("").items.isEmpty(),"empty not shown as installed");
        String fonts="[ ] com.android.theme.font.SpaceGrotesk\n[x] com.android.theme.font.RookeryRegular\n[ ] com.android.theme.font.notoserifsource";
        SystemInspector.Result f=SystemInspector.fonts(fonts);
        check(f.items.size()==3,"font rows");
        check(f.headline.contains("RookeryRegular"),"active font");
        check(f.items.get(1).positive,"active marker");
        check(f.items.get(2).name.equals("Noto Serif Source"),"font display name");
        check(SystemInspector.fonts("No overlay list").items.isEmpty(),"no invented fonts");
        SystemInspector.Result d=SystemInspector.disabled("package:com.android.stk\npackage:some.pkg\npackage:com.android.emergency");
        check(d.headline.contains("3 отключённых"),"disabled count");
        check(d.items.size()==2,"critical safety list");
        check(SystemInspector.appOpState("RUN_ANY_IN_BACKGROUND: ignore").equals("Ограничен"),"restricted state");
        check(SystemInspector.appOpState("No operations.\nDefault mode: allow").equals("По умолчанию"),"default state");
        check(SystemInspector.appOpState("RUN_ANY_IN_BACKGROUND: allow").equals("Разрешён"),"allowed state");
        check(SystemInspector.appOpState("").equals("Не определено"),"empty state");
        SystemInspector.Result g=SystemInspector.gameModes("com.riotgames.league.wildrift current mode: standard, available game modes: [standard,custom]");
        check(g.headline.equals("Активен: Стандартный"),"game current mode");
        check(g.items.size()==3,"game available modes only");
        check(SystemInspector.gameModes("").items.isEmpty(),"game empty");
        SystemInspector.Result display=SystemInspector.display("0\n120.0\n120.0\nPhysical size: 1440x3168\nOverride density: 442");
        check(display.items.size()==5,"display parsed");
        check(display.items.get(0).state.equals("Выключено"),"power saver state");
        check(display.items.get(1).state.equals("120 Гц"),"rate not real fps");
        check(SystemInspector.display("").items.isEmpty(),"display empty");
        System.out.println("SystemInspectorTest PASS ("+count+" checks)");
    }
}
