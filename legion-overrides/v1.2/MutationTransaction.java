package dev.legion.control;

/** Write-ahead journal for system mutations. Independent of UI/Android classes for testing. */
public final class MutationTransaction {
    private MutationTransaction() {}

    public interface Store {
        boolean hasPending();
        boolean stage(String snapshot, String label);
        boolean complete(String snapshot, String label);
        boolean clearPending();
    }
    public interface Action { void run() throws Exception; }

    /**
     * Persist a recoverable snapshot BEFORE the first system command.
     * If rollback fails, preserve pending state for later recovery after a restart.
     */
    public static void apply(Store store, String snapshot, String label,
                             Action change, Action rollback) throws Exception {
        if (store == null || snapshot == null || snapshot.isEmpty() ||
                label == null || label.isEmpty() || change == null || rollback == null)
            throw new IllegalArgumentException("Invalid transaction");
        if (store.hasPending())
            throw new IllegalStateException("Сначала восстанови незавершённое изменение в разделе «История»");
        if (!store.stage(snapshot, label))
            throw new IllegalStateException("Не удалось сохранить снимок до изменения; настройки не тронуты");
        try {
            change.run();
            if (!store.complete(snapshot, label))
                throw new IllegalStateException("Не удалось завершить журнал операции");
        } catch (Exception failure) {
            try {
                rollback.run();
                if (!store.clearPending())
                    throw new IllegalStateException("Настройки возвращены, но запись о восстановлении не очищена");
            } catch (Exception recoveryFailure) {
                failure.addSuppressed(recoveryFailure);
            }
            throw failure;
        }
    }
}
