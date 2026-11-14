package dev.legion.control;

import org.json.JSONObject;
import android.os.IBinder;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import rikka.shizuku.SystemServiceHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Invoked only through Shizuku UserService binder; runs with shell UID. */
public final class ShellUserService extends ICommandService.Stub {
    public ShellUserService() {}

    @Override public String execute(String command) {
        JSONObject result = new JSONObject();
        java.lang.Process process = null;
        try {
            if (!CommandPolicy.permittedCommand(command)) throw new SecurityException("Operation not allowlisted");
            process = new ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start();
            final java.lang.Process running = process;
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final String[] readerError = new String[1];
            Thread reader = new Thread(() -> {
                try (InputStream input = running.getInputStream()) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = input.read(buf)) != -1) {
                        if (bytes.size() + n > 131072) throw new IllegalStateException("Output limit exceeded");
                        bytes.write(buf, 0, n);
                    }
                } catch (Exception e) { readerError[0] = e.toString(); }
            }, "legion-shell-reader");
            reader.setDaemon(true);
            reader.start();
            // Deadline is independent of stdout EOF; a hanging process cannot stall the Binder forever.
            if (!running.waitFor(15, TimeUnit.SECONDS)) throw new IllegalStateException("Command timeout (15s)");
            reader.join(1000);
            if (reader.isAlive()) throw new IllegalStateException("Output reader timeout");
            if (readerError[0] != null) throw new IllegalStateException(readerError[0]);
            result.put("exit", running.exitValue());
            result.put("output", bytes.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            try { result.put("exit", -1); result.put("output", e.getClass().getSimpleName() + ": " + e.getMessage()); } catch (Exception ignored) {}
        }
        return result.toString();
    }
    /**
     * This service runs as Shizuku's shell UID, where hidden Binder API access is
     * supported. Query the exact verdict used by Settings, never set app ops,
     * accessibility services, permissions or device policy.
     */
    @Override public String queryAccessibilityTargets(String[] packages, int[] uids, int userId) {
        if(packages==null||uids==null||packages.length!=uids.length||
                packages.length>64||userId<0||userId>100000)
            return "ERROR\tINVALID_REQUEST";
        for(int i=0;i<packages.length;i++){
            if(!AccessStatus.validPackage(packages[i])||uids[i]<0)
                return "ERROR\tINVALID_TARGET";
            if(uids[i]/100000!=userId) return "ERROR\tWRONG_USER";
        }
        try{
            IBinder binder=SystemServiceHelper.getSystemService("accessibility");
            if(binder==null)return "ERROR\tSERVICE_UNAVAILABLE";
            Class<?> stub=Class.forName("android.view.accessibility.IAccessibilityManager$Stub");
            Method asInterface=stub.getMethod("asInterface",IBinder.class);
            Object manager=asInterface.invoke(null,binder);
            if(manager==null)return "ERROR\tBINDER_UNAVAILABLE";
            Method eligibility=manager.getClass().getMethod(
                "isAccessibilityTargetAllowed",String.class,int.class,int.class);
            Set<String> seen=new HashSet<>();
            StringBuilder output=new StringBuilder();
            for(int i=0;i<packages.length;i++){
                String pkg=packages[i];
                if(!seen.add(pkg))continue;
                try{
                    Object value=eligibility.invoke(manager,pkg,uids[i],userId);
                    if(!(value instanceof Boolean))continue;
                    output.append(((Boolean)value)?"ALLOW\t":"BLOCKED\t")
                        .append(pkg).append('\n');
                }catch(Exception ignored){
                    // A vendor may reject a subset of targets; do not fabricate a verdict.
                }
            }
            return output.length()==0?"ERROR\tNO_VERDICTS":output.toString();
        }catch(Throwable denied){
            // Hidden implementation or Binder security is vendor-specific.
            return "ERROR\tAPI_NOT_AVAILABLE";
        }
    }

    @Override public void destroy() { System.exit(0); }
}
