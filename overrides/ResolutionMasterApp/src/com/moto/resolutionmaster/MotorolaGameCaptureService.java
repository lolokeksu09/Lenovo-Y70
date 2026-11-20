package com.moto.resolutionmaster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * User-started, 90-second diagnostic recording while Wild Rift is in front.
 * It changes NO thermal settings, touch tuning, or protected OEM permissions.
 * Small summaries are saved privately; no raw dumpsys data is retained.
 */
public final class MotorolaGameCaptureService extends Service {
    public static final String ACTION_START =
        "com.moto.resolutionmaster.motorola_game_capture.START";
    public static final String ACTION_STOP =
        "com.moto.resolutionmaster.motorola_game_capture.STOP";

    private static final int NOTIFICATION_ID = 7314;
    private static final String CHANNEL_ID = "motorola_game_capture";
    private volatile boolean cancelled;
    private volatile boolean running;
    private Thread worker;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Диагностика игрового режима",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Кратковременный сбор сведений о GameTime и MAXE");
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            cancelled = true;
            if (worker != null) worker.interrupt();
            if (!running) stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action) || running) {
            if (!running) stopSelf();
            return START_NOT_STICKY;
        }
        if (!ShellHelper.isShizukuPermissionGranted() &&
                !ShellHelper.isRootAvailable()) {
            MotorolaCaptureStore.finish(this, "Нет Shizuku/Root",
                "Доступ для чтения системных сервисов отсутствует.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!MotorolaCaptureStore.begin(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        cancelled = false;
        running = true;
        try {
            // Android 14+ uses the specialUse type declared in the manifest.
            startForeground(NOTIFICATION_ID, notification(0));
        } catch (RuntimeException e) {
            running = false;
            MotorolaCaptureStore.finish(this,
                "Не удалось начать фоновую запись: " +
                    e.getClass().getSimpleName(), "");
            stopSelf();
            return START_NOT_STICKY;
        }

        worker = new Thread(new Runnable() {
            @Override public void run() { collect(); }
        }, "MotorolaGameCapture");
        worker.start();
        return START_NOT_STICKY;
    }

    private void collect() {
        final MotorolaCaptureReport report = new MotorolaCaptureReport();
        final long started = SystemClock.elapsedRealtime();
        String outcome = "Завершено";
        try {
            for (int i = 0; i < MotorolaCaptureReport.MAX_SAMPLES && !cancelled; i++) {
                boolean error = false;
                String light;
                String motorola = null;
                try {
                    ShellHelper.CommandResult read = ShellHelper.runCommandWithResult(
                        this, MotorolaCaptureFormat.LIGHT_COMMAND);
                    light = read.success
                        ? MotorolaCaptureFormat.light(read.detail)
                        : "system=ошибка Shell " + read.exitCode;
                    if (!read.success || light.contains("неполный ответ")) error = true;

                    // Only four potentially heavier dumpsys reads per session.
                    if (i == 0 || i == 4 || i == 8 || i == 11) {
                        ShellHelper.CommandResult dump = ShellHelper.runCommandWithResult(
                            this, "dumpsys moto_extend_service");
                        motorola = dump.success
                            ? MotorolaCaptureFormat.motorola(dump.detail)
                            : "Motorola=Shell error " + dump.exitCode;
                        if (!dump.success ||
                                motorola.contains("нет подтверждённых данных")) {
                            error = true;
                        }
                    }
                } catch (Throwable e) {
                    light = "system=исключение " + e.getClass().getSimpleName();
                    error = true;
                }
                int seconds = (int) ((SystemClock.elapsedRealtime() - started) / 1000L);
                report.append(seconds, light, motorola, error);
                MotorolaCaptureStore.progress(this, report.render("Запись идёт"),
                    report.samples());
                updateNotification(report.samples());

                if (i + 1 < MotorolaCaptureReport.MAX_SAMPLES && !cancelled) {
                    try {
                        Thread.sleep(MotorolaCaptureReport.SAMPLE_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        cancelled = true;
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (cancelled) outcome = "Остановлено пользователем или системой";
        } catch (Throwable unexpected) {
            Log.e("MotoGameCapture", "Capture interrupted", unexpected);
            outcome = "Ошибка записи: " + unexpected.getClass().getSimpleName();
        } finally {
            MotorolaCaptureStore.finish(this, outcome, report.render(outcome));
            running = false;
            try { stopForeground(true); }
            catch (RuntimeException ignored) { }
            stopSelf();
        }
    }

    private void updateNotification(int count) {
        NotificationManager manager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.notify(NOTIFICATION_ID, notification(count));
            } catch (RuntimeException ignored) {
                // Notification delivery is not proof of captured samples.
            }
        }
    }

    private Notification notification(int samples) {
        Intent open = new Intent(this, MotorolaGameActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, MotorolaGameCaptureService.class)
            .setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Legion Y70 · GameTime / MAXE")
            .setContentText("Запись " + samples + "/" +
                MotorolaCaptureReport.MAX_SAMPLES + " · без изменения настроек")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopIntent)
            .build();
    }

    @Override public void onDestroy() {
        cancelled = true;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
