package dev.legion.control;

public final class AccessEvidenceTest {
    private static int assertions;
    private static void yes(boolean value,String message){
        assertions++;if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args){
        long now=1800000000000L;
        yes(AccessEvidence.parse("SWITCH_AVAILABLE",now-1000,now,now-3000)==
            AccessEvidence.Verdict.SWITCH_AVAILABLE,"allowed manual evidence");
        yes(AccessEvidence.parse("RESTRICTED_SCREEN",now-1000,now,now-3000)==
            AccessEvidence.Verdict.RESTRICTED_SCREEN,"restricted manual evidence");
        yes(AccessEvidence.parse("OTHER",now-1000,now,0)==AccessEvidence.Verdict.NONE,
            "unknown token never trusted");
        yes(AccessEvidence.parse(null,now-1000,now,0)==AccessEvidence.Verdict.NONE,
            "no evidence remains unknown");
        yes(AccessEvidence.parse("SWITCH_AVAILABLE",now+1000,now,0)==AccessEvidence.Verdict.NONE,
            "future evidence invalid");
        yes(AccessEvidence.parse("RESTRICTED_SCREEN",now-AccessEvidence.MAX_AGE_MS-1,now,0)==AccessEvidence.Verdict.NONE,
            "old evidence expires");
        yes(AccessEvidence.parse("RESTRICTED_SCREEN",now-1000,now,now-500)==AccessEvidence.Verdict.NONE,
            "package update invalidates evidence");
        yes(AccessEvidence.label(AccessStatus.State.DISABLED_UNVERIFIED,
            AccessEvidence.Verdict.SWITCH_AVAILABLE).contains("переключатель доступен"),
            "user-allowed-off label");
        yes(AccessEvidence.label(AccessStatus.State.DISABLED_UNVERIFIED,
            AccessEvidence.Verdict.RESTRICTED_SCREEN).contains("вручную"),
            "manually seen blocked label");
        yes(AccessEvidence.label(AccessStatus.State.DISABLED_UNVERIFIED,
            AccessEvidence.Verdict.NONE).equals("Выключена · доступ не проверен"),
            "no invented automatic verdict");
        yes(AccessEvidence.label(AccessStatus.State.ENABLED,
            AccessEvidence.Verdict.RESTRICTED_SCREEN).equals("Включена"),
            "Android enabled overrides stale restricted note");
        yes(AccessEvidence.label(AccessStatus.State.DISABLED,
            AccessEvidence.Verdict.RESTRICTED_SCREEN).equals("Выключена · доступна"),
            "system allowed overrides manual report");
        yes(AccessEvidence.label(AccessStatus.State.RESTRICTED,
            AccessEvidence.Verdict.SWITCH_AVAILABLE).equals("Ограничена Android"),
            "system restricted overrides manual report");
        yes(AccessEvidence.label(AccessStatus.State.UNKNOWN,
            AccessEvidence.Verdict.SWITCH_AVAILABLE).equals("Не удалось проверить"),
            "unreadable list cannot be marked allowed");
        yes(!AccessEvidence.needsRestrictedWizard(AccessStatus.State.DISABLED_UNVERIFIED,
            AccessEvidence.Verdict.SWITCH_AVAILABLE),"plain off no restricted wizard");
        yes(AccessEvidence.needsRestrictedWizard(AccessStatus.State.DISABLED_UNVERIFIED,
            AccessEvidence.Verdict.RESTRICTED_SCREEN),"manually blocked gets guide");
        yes(AccessEvidence.needsRestrictedWizard(AccessStatus.State.RESTRICTED,
            AccessEvidence.Verdict.SWITCH_AVAILABLE),"automatic restriction gets guide");
        yes(!AccessEvidence.canConfirm(AccessStatus.State.ENABLED),"enabled service not manually classified");
        yes(AccessEvidence.canConfirm(AccessStatus.State.DISABLED_UNVERIFIED),"unknown off can be classified");
        yes(AccessEvidence.explanation(AccessStatus.State.DISABLED_UNVERIFIED,
            AccessEvidence.Verdict.RESTRICTED_SCREEN).contains("не системная проверка"),
            "manual evidence explanation accurate");
        System.out.println("AccessEvidenceTest PASS ("+assertions+" checks)");
    }
}
