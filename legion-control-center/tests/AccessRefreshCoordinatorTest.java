package dev.legion.control;

public final class AccessRefreshCoordinatorTest {
    private static int checks;
    private static void ok(boolean value,String label){
        checks++;
        if(!value)throw new AssertionError(label);
    }
    public static void main(String[] args){
        AccessRefreshCoordinator gate=new AccessRefreshCoordinator();
        AccessRefreshCoordinator.Ticket first=gate.request(false,false);
        ok(first!=null && !first.manual,"automatic first request starts");
        ok(gate.isCurrent(first.id),"first request owns result");
        ok(gate.request(false,false)==null,"resume event during read coalesced");
        ok(gate.request(true,true)==null,"manual refresh and returnTop coalesce");
        AccessRefreshCoordinator.Ticket deferred=gate.finish(first.id);
        ok(deferred!=null && deferred.id>first.id,"deferred refresh receives new ticket");
        ok(deferred.manual && deferred.returnTop,"manual and returnTop propagated");
        ok(!gate.isCurrent(first.id),"stale first response rejected");
        ok(gate.finish(first.id)==null,"late callback cannot complete current read");
        ok(gate.isCurrent(deferred.id),"deferred work is current");
        ok(gate.finish(deferred.id)==null,"no unwanted third read");
        ok(!gate.isCurrent(deferred.id),"completion closes ticket");

        AccessRefreshCoordinator.Ticket timed=gate.request(true,false);
        ok(gate.isCurrent(timed.id),"manual retry allowed");
        gate.finish(timed.id); // timeout uses same terminal gate transition
        ok(!gate.isCurrent(timed.id),"late Binder response after timeout ignored");
        AccessRefreshCoordinator.Ticket retry=gate.request(true,false);
        ok(retry.id>timed.id,"retry uses fresh request id");
        gate.close();
        ok(!gate.isCurrent(retry.id),"destroy rejects async callbacks");
        ok(gate.finish(retry.id)==null,"destroyed request not completed");
        AccessRefreshCoordinator.Ticket clean=gate.request(false,false);
        ok(clean.id>retry.id,"sequence monotonic");
        gate.finish(clean.id);
        System.out.println("AccessRefreshCoordinatorTest PASS ("+checks+" checks)");
    }
}
