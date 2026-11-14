package dev.legion.control;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Bounded, local activity history. This is NOT a multi-step rollback stack. */
public final class ActionHistory {
    public static final int LIMIT=25;
    public static final String APPLIED="APPLIED";
    public static final String UNDONE="UNDONE";
    public static final String RECOVERED="RECOVERED";

    private ActionHistory() {}

    public static final class Entry {
        public final long timestamp;
        public final String type;
        public final String label;
        Entry(long timestamp,String type,String label) {
            this.timestamp=timestamp;this.type=type;this.label=label;
        }
    }

    private static boolean validType(String s) {
        return APPLIED.equals(s)||UNDONE.equals(s)||RECOVERED.equals(s);
    }

    private static String encode(Entry e) {
        String safe=Base64.getUrlEncoder().withoutPadding().encodeToString(e.label.getBytes(StandardCharsets.UTF_8));
        return e.timestamp+"|"+e.type+"|"+safe;
    }

    /** Read valid entries only, newest-first. Corrupt lines are ignored, not displayed. */
    public static List<Entry> read(String raw) {
        if(raw==null||raw.isEmpty())return Collections.emptyList();
        List<Entry> entries=new ArrayList<>();
        for(String line:raw.split("\n")) {
            if(entries.size()==LIMIT)break;
            String[] parts=line.split("\\|",3);
            if(parts.length!=3||!validType(parts[1]))continue;
            try {
                long when=Long.parseLong(parts[0]);
                if(when<=0||parts[2].length()>512)continue;
                String label=new String(Base64.getUrlDecoder().decode(parts[2]),StandardCharsets.UTF_8);
                if(label.trim().isEmpty()||label.length()>140)continue;
                entries.add(new Entry(when,parts[1],label));
            }catch(IllegalArgumentException ignored){}
        }
        return Collections.unmodifiableList(entries);
    }

    public static String append(String previous,long timestamp,String type,String label) {
        if(timestamp<=0||!validType(type)||label==null||label.trim().isEmpty()||label.length()>140)
            throw new IllegalArgumentException("Invalid history event");
        List<Entry> old=read(previous);
        StringBuilder out=new StringBuilder(encode(new Entry(timestamp,type,label)));
        for(int i=0;i<Math.min(LIMIT-1,old.size());i++)out.append('\n').append(encode(old.get(i)));
        return out.toString();
    }
}
