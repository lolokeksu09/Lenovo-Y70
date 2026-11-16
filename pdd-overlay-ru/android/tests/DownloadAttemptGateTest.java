package app.pinduoduo.overlayru;

public final class DownloadAttemptGateTest {
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        DownloadAttemptGate gate = new DownloadAttemptGate();
        int first = gate.begin();
        require(first > 0, "first request starts");
        require(gate.begin() == -1, "double-tap is single-flight");
        require(!gate.markOverdue(first + 1), "unrelated timer is ignored");
        require(gate.markOverdue(first), "request becomes overdue");
        require(gate.inFlight() && gate.overdue(), "overdue download is not cancelled");
        require(gate.begin() == -1, "overdue request must not dispatch second download");
        require(gate.finish(first), "late success accepted");
        require(!gate.inFlight(), "finished request clears pending state");
        int next = gate.begin();
        require(next > first, "next request starts only after prior completion");
        require(!gate.finish(first), "old callback ignored after next starts");
        require(gate.finish(next), "current success accepted");
        require(!gate.finish(next), "duplicate completion ignored");
        System.out.println("PASS: 10 single-flight download lifecycle checks");
    }
}
