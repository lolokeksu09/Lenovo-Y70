package android.os;

/** Test-only fake of Android framework DeadObjectException; not bundled into APK. */
public final class DeadObjectException extends Exception {
    public DeadObjectException(String message) {
        super(message);
    }
}
