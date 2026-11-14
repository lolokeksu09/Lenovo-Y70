package dev.legion.control;

/**
 * Main-thread only coordinator for Accessibility refreshes.
 * Coalesces redundant refreshes and rejects responses from timed-out requests.
 */
public final class AccessRefreshCoordinator {
    public static final class Ticket {
        public final long id;
        public final boolean manual;
        public final boolean returnTop;
        private Ticket(long id,boolean manual,boolean returnTop){
            this.id=id;this.manual=manual;this.returnTop=returnTop;
        }
    }
    private long sequence;
    private boolean running;
    private boolean queued;
    private boolean queuedManual;
    private boolean queuedReturnTop;

    public Ticket request(boolean manual,boolean returnTop){
        if(running){
            queued=true;
            queuedManual|=manual;
            queuedReturnTop|=returnTop;
            return null;
        }
        running=true;
        return new Ticket(++sequence,manual,returnTop);
    }

    public boolean isCurrent(long id){
        return running&&id==sequence;
    }

    /** Complete a request (including timeout); returns a ticket for deferred work, if any. */
    public Ticket finish(long id){
        if(!isCurrent(id))return null;
        running=false;
        if(!queued)return null;
        boolean manual=queuedManual,returnTop=queuedReturnTop;
        queued=false;queuedManual=false;queuedReturnTop=false;
        return request(manual,returnTop);
    }

    public void close(){
        running=false;queued=false;queuedManual=false;queuedReturnTop=false;
        sequence++;
    }
}
