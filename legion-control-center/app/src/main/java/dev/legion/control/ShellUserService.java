package dev.legion.control;

import org.json.JSONObject;
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
    @Override public void destroy() { System.exit(0); }
}
