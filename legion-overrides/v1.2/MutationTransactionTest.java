package dev.legion.control;
import java.util.ArrayList;
import java.util.List;

public final class MutationTransactionTest {
    private static int assertions;
    static void expect(boolean value) { assertions++; if (!value) throw new AssertionError("Assertion " + assertions); }
    static final class Fake implements MutationTransaction.Store {
        String pending = "", undo = "", undoLabel = "";
        boolean stageOK = true, finishOK = true, clearOK = true;
        final List<String> log = new ArrayList<>();
        public boolean hasPending() { return !pending.isEmpty(); }
        public boolean stage(String snapshot, String label) {log.add("stage"); if (!stageOK) return false;pending=snapshot;return true;}
        public boolean complete(String snapshot,String label) {log.add("complete");if(!finishOK)return false;undo=snapshot;undoLabel=label;pending="";return true;}
        public boolean clearPending(){log.add("clear"); if(!clearOK)return false;pending="";return true;}
    }
    static Exception attempt(Fake s, MutationTransaction.Action action, MutationTransaction.Action rollback) {
        try {MutationTransaction.apply(s, "snapshot", "display", action, rollback);return null;}
        catch(Exception e){return e;}
    }
    public static void main(String[] args) {
        Fake s=new Fake();
        expect(attempt(s, ()->{s.log.add("change");expect("snapshot".equals(s.pending));},()->s.log.add("rollback"))==null);
        expect(s.log.toString().equals("[stage, change, complete]"));
        expect(s.pending.isEmpty() && s.undo.equals("snapshot") && s.undoLabel.equals("display"));

        Fake failedWrite=new Fake();failedWrite.stageOK=false;
        Exception e=attempt(failedWrite,()->failedWrite.log.add("change"),()->failedWrite.log.add("rollback"));
        expect(e!=null && failedWrite.log.toString().equals("[stage]"));

        Fake failChange=new Fake();
        e=attempt(failChange,()->{failChange.log.add("change");throw new Exception("write failed");},()->failChange.log.add("rollback"));
        expect(e!=null && "write failed".equals(e.getMessage()));
        expect(failChange.log.toString().equals("[stage, change, rollback, clear]"));
        expect(failChange.pending.isEmpty() && failChange.undo.isEmpty());

        Fake rollbackFailed=new Fake();rollbackFailed.undo="older";
        e=attempt(rollbackFailed,()->{throw new Exception("write failed");},()->{throw new Exception("restore failed");});
        expect(e!=null && e.getSuppressed().length==1);
        expect(rollbackFailed.pending.equals("snapshot") && rollbackFailed.undo.equals("older"));
        expect(attempt(rollbackFailed,()->rollbackFailed.log.add("change"),()->{})!=null);
        expect(!rollbackFailed.log.contains("change"));

        Fake failFinish=new Fake();failFinish.finishOK=false;
        e=attempt(failFinish,()->failFinish.log.add("change"),()->failFinish.log.add("rollback"));
        expect(e!=null && failFinish.log.toString().equals("[stage, change, complete, rollback, clear]"));
        expect(failFinish.pending.isEmpty());

        Fake clearFails=new Fake();clearFails.clearOK=false;
        e=attempt(clearFails,()->{throw new Exception("write failed");},()->{});
        expect(e!=null && e.getSuppressed().length==1 && clearFails.hasPending());
        System.out.println("MutationTransactionTest PASS ("+assertions+" assertions)");
    }
}
