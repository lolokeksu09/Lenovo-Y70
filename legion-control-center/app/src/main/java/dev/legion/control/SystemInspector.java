package dev.legion.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Converts read-only Android shell output into user-facing, deterministic rows.
 *  This class has no Android dependency and never executes shell commands. */
public final class SystemInspector {
    private SystemInspector() {}
    public static final class Item {
        public final String name;
        public final String state;
        public final String detail;
        public final boolean positive;
        public Item(String name, String state, String detail, boolean positive) {
            this.name = name;
            this.state = state;
            this.detail = detail;
            this.positive = positive;
        }
    }
    public static final class Result {
        public final String headline;
        public final String description;
        public final List<Item> items;
        public final String technical;
        public Result(String headline, String description, List<Item> items, String technical) {
            this.headline=headline;
            this.description=description;
            this.items=Collections.unmodifiableList(new ArrayList<>(items));
            this.technical=technical;
        }
    }
    private static final String[][] MOTO = {
        {"com.motorola.gamemode","GameMode","Игровые инструменты Motorola"},
        {"com.motorola.freeform","Freeform","Оконный режим приложений"},
        {"com.motorola.mobiledesktop","Mobile Desktop","Режим рабочего стола"},
        {"com.motorola.batterycare","Battery Care","Средства ухода за батареей"},
        {"com.motorola.personalize","Personalize","Оформление и персонализация"}
    };
    public static Result motorola(String output){
        String raw=output==null?"":output.trim();
        if(raw.isEmpty()) return new Result("Нет данных","Не удалось прочитать список модулей.", Collections.emptyList(),raw);
        List<Item> items=new ArrayList<>();
        int installed=0, seen=0;
        String[] lines=raw.split("\\r?\\n");
        for(String[] module:MOTO){
            boolean header=false, found=false;
            String path="";
            for(String line:lines){
                String v=line.trim();
                if(v.startsWith("===")){
                    header=v.substring(3).trim().equals(module[0]);
                    if(header)seen++;
                } else if(header && v.startsWith("package:")){
                    found=true;
                    path=v.substring(8);
                }
            }
            if(found) installed++;
            items.add(new Item(module[1],found?"Установлен":(seen==0?"Неизвестно":"Не найден"),module[2],found));
        }
        return new Result(installed+" из "+MOTO.length+" установлены", "Наличие пакета не гарантирует работу функции на прошивке Motorola.",items,raw);
    }
    public static Result fonts(String output){
        String raw=output==null?"":output.trim();
        List<Item> items=new ArrayList<>();
        String active=null;
        for(String line:raw.split("\\r?\\n")){
            String t=line.trim();
            if(!(t.startsWith("[x]")||t.startsWith("[ ]"))) continue;
            boolean enabled=t.startsWith("[x]");
            String pkg=t.substring(3).trim();
            if(!pkg.startsWith("com.android.theme.font."))continue;
            String font=pkg.substring("com.android.theme.font.".length());
            String pretty=font.equals("notoserifsource")?"Noto Serif Source":font;
            if(enabled)active=pretty;
            items.add(new Item(pretty,enabled?"Активный":"Доступен", "Системный шрифт Android",enabled));
        }
        String title=active==null?"Активный не определён":"Активен: "+active;
        return new Result(title,items.isEmpty()?"Список шрифтов не получен.":"Доступные оверлеи шрифтов. Выбирай шрифт из раздела «Система».",items,raw);
    }
    public static Result disabled(String output){
        String raw=output==null?"":output.trim();
        List<Item> items=new ArrayList<>();
        String[] important={"com.android.emergency","com.android.cellbroadcastreceiver","com.google.android.cellbroadcastreceiver","com.android.dialer","com.android.stk"};
        int count=0;
        for(String line:raw.split("\\r?\\n")){
            String t=line.trim();
            if(t.startsWith("package:")){
                count++;
                String pkg=t.substring(8);
                for(String critical:important) if(pkg.equals(critical)){
                    items.add(new Item(pkg,"Отключён","Может влиять на экстренную связь или предупреждения",false));
                    break;
                }
            }
        }
        return new Result(count+" отключённых пакетов",items.isEmpty()?"Критичные пакеты из контрольного списка не обнаружены.":"Внимание: среди отключённых пакетов обнаружены системные службы. Изменения не выполнялись.",items,raw);
    }
    public static Result gameModes(String output){
        String raw=output==null?"":output.trim();
        String current=CommandPolicy.currentGameMode(raw);
        List<Item> items=new ArrayList<>();
        if(!current.equals("unknown")){
            items.add(new Item("Текущий режим",current,"Выбран системой Android",true));
            for(String mode:new String[]{"standard","custom","performance","battery"}){
                if(CommandPolicy.gameModeAvailable(raw,mode))
                    items.add(new Item(mode,"Доступен","GameManager допускает этот режим",true));
            }
        }
        return new Result(current.equals("unknown")?"Режим не определён":"Активен: "+current,
            current.equals("unknown")?"GameManager не вернул ожидаемые данные.":"Доступность режимов определяется GameManager, а не названием телефона.",items,raw);
    }
    public static Result display(String output){
        String raw=output==null?"":output.trim();
        String[] lines=raw.split("\r?\n");
        List<Item> items=new ArrayList<>();
        if(lines.length>=3){
            items.add(new Item("Энергосбережение","1".equals(lines[0].trim())?"Включено":"0".equals(lines[0].trim())?"Выключено":"Не определено","Режим энергосбережения задаёт Android",!"1".equals(lines[0].trim())));
            items.add(new Item("Нижняя граница",displayValue(lines[1]),"Настройка min_refresh_rate, не измеренный FPS",true));
            items.add(new Item("Верхняя граница",displayValue(lines[2]),"Настройка peak_refresh_rate, не измеренный FPS",true));
            for(int i=3;i<lines.length;i++){
                String line=lines[i].trim();
                if(line.startsWith("Physical size:")||line.startsWith("Override size:")||line.startsWith("Physical density:")||line.startsWith("Override density:")){
                    String[] parts=line.split(":",2);
                    items.add(new Item(parts[0].trim(),parts[1].trim(),"Показатель WindowManager",true));
                }
            }
        }
        return new Result(items.isEmpty()?"Нет данных":"Параметры экрана",
            "Частоты в настройках не всегда равны фактически активной частоте дисплея.",items,raw);
    }
    private static String displayValue(String text){
        String v=text.trim();
        return v.equals("null")?"Выбирает Android":v+" Гц";
    }
    /** Parsed device facts are intentionally separated from the low-level Shizuku identity. */
    public static Result device(String output) {
        String raw=output==null?"":output.trim();
        java.util.Map<String,String> fields=new java.util.HashMap<>();
        for(String line:raw.split("\\r?\\n")){
            int colon=line.indexOf(':');
            if(colon<=0)continue;
            String key=line.substring(0,colon).trim();
            if(!(key.equals("Model")||key.equals("SKU")||key.equals("SoC")||key.equals("Android")||
                    key.equals("Power saving")||key.equals("Peak refresh")||key.equals("Minimum refresh")))continue;
            String value=line.substring(colon+1).trim();
            if(value.isEmpty()||value.length()>90)continue;
            fields.put(key,value);
        }
        if(fields.size()<3)return new Result("Данные не получены", "Повтори проверку при подключённом Shizuku.",Collections.emptyList(),raw);
        List<Item> items=new ArrayList<>();
        String sku=fields.get("SKU"),model=fields.get("Model");
        String title="Данные устройства";
        if("XT2611-1".equalsIgnoreCase(sku))title="Lenovo Legion Y70";
        if(sku!=null)items.add(new Item("Аппаратный код",sku,"Идентификатор устройства, независимый от имени прошивки",true));
        if(model!=null)items.add(new Item("Установленная прошивка",model,"Имя, указанное прошивкой; это не аппаратная модель",true));
        if(fields.containsKey("SoC"))items.add(new Item("Платформа SoC",fields.get("SoC"),"Идентификатор процессорной платформы",true));
        if(fields.containsKey("Android"))items.add(new Item("Версия Android",fields.get("Android"),"Версия установленной системы",true));
        String power=fields.get("Power saving");
        if(power!=null){
            String state="1".equals(power)?"Включено":"0".equals(power)?"Выключено":"Не определено";
            String note="1".equals(power)?"60 Гц при энергосбережении — нормальная политика системы":"Состояние энергосбережения, а не показатель производительности";
            items.add(new Item("Энергосбережение",state,note,true));
        }
        String min=fields.get("Minimum refresh"),max=fields.get("Peak refresh");
        if(min!=null||max!=null){
            String first=refreshValue(min),last=refreshValue(max);
            String state=first.equals(last)?first:first+" — "+last;
            items.add(new Item("Частота в настройках",state,"Границы min/peak, а НЕ фактическая частота дисплея или FPS",true));
        }
        return new Result(title,"Сведения прочитаны через Shizuku. Для диагностики доступен технический вывод.",items,raw);
    }
    private static String refreshValue(String value){
        if(value==null||value.equalsIgnoreCase("null"))return "Авто";
        try{
            double n=Double.parseDouble(value);
            if(!Double.isFinite(n)||n<1||n>1000)return "Не определено";
            return Math.rint(n)==n?String.format(Locale.ROOT,"%.0f Гц",n):String.format(Locale.ROOT,"%.1f Гц",n);
        }catch(NumberFormatException e){return "Не определено";}
    }
    public static String deviceBrief(Result result){
        if(result==null||result.items.isEmpty())return "Не удалось получить данные устройства.";
        String android="—",soc="—",power="—",refresh="—";
        for(Item i:result.items){
            if(i.name.equals("Версия Android"))android=i.state;
            if(i.name.equals("Платформа SoC"))soc=i.state;
            if(i.name.equals("Энергосбережение"))power=i.state.toLowerCase(Locale.ROOT);
            if(i.name.equals("Частота в настройках"))refresh=i.state;
        }
        return result.headline+"  ·  Android "+android+"\nSoC "+soc+"  ·  энергосбережение "+power+"\nЧастота в настройках: "+refresh+" (не текущая частота)";
    }

    public static String appOpState(String output){
        if(output==null||output.trim().isEmpty())return "Не определено";
        switch(CommandPolicy.appOpsMode(output)){
            case "allow":return "Разрешён";
            case "ignore":case "deny":return "Ограничен";
            case "default":return "По умолчанию";
            default:return "Не определено";
        }
    }
}
