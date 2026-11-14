package dev.legion.control;

/** Explicit user-observed switch status, never passed off as an Android API verdict. */
public final class AccessEvidence {
    public static final long MAX_AGE_MS=7L*24L*60L*60L*1000L;
    public enum Verdict { NONE, SWITCH_AVAILABLE, RESTRICTED_SCREEN }

    private AccessEvidence(){}

    public static Verdict parse(String raw,long recordedAt,long now,long packageUpdatedAt){
        if(raw==null||now<=0||recordedAt<=0||recordedAt>now||
                now-recordedAt>MAX_AGE_MS||recordedAt<packageUpdatedAt)return Verdict.NONE;
        if(raw.equals("SWITCH_AVAILABLE"))return Verdict.SWITCH_AVAILABLE;
        if(raw.equals("RESTRICTED_SCREEN"))return Verdict.RESTRICTED_SCREEN;
        return Verdict.NONE;
    }

    public static String label(AccessStatus.State actual,Verdict evidence){
        if(actual==null)return "Статус не определён";
        if(actual!=AccessStatus.State.DISABLED_UNVERIFIED)
            return AccessStatus.label(actual);
        if(evidence==Verdict.SWITCH_AVAILABLE)return "Выключена · переключатель доступен";
        if(evidence==Verdict.RESTRICTED_SCREEN)return "Ограничена · указано вручную";
        return AccessStatus.label(actual);
    }

    public static String explanation(AccessStatus.State actual,Verdict evidence){
        if(actual!=AccessStatus.State.DISABLED_UNVERIFIED)
            return AccessStatus.restrictionDetail(actual);
        if(evidence==Verdict.SWITCH_AVAILABLE)
            return "Ты подтвердил, что переключатель можно включить в настройках Android. Это не автоматическая проверка.";
        if(evidence==Verdict.RESTRICTED_SCREEN)
            return "Ты подтвердил сообщение Android об ограниченных настройках. Это отметка пользователя, а не системная проверка Legion Control.";
        return "Автоматическая проверка ограничений недоступна на этой прошивке. Открой службу в Android и укажи увиденное состояние.";
    }

    public static boolean needsRestrictedWizard(AccessStatus.State actual,Verdict evidence){
        if(actual==AccessStatus.State.RESTRICTED)return true;
        return actual==AccessStatus.State.DISABLED_UNVERIFIED &&
               evidence!=Verdict.SWITCH_AVAILABLE;
    }

    public static boolean canConfirm(AccessStatus.State actual){
        return actual==AccessStatus.State.DISABLED_UNVERIFIED;
    }
}
