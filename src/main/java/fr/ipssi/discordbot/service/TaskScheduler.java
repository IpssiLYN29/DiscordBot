package fr.ipssi.discordbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskScheduler.class);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public void schedule(Runnable task, Duration period) {
        executor.scheduleWithFixedDelay(() -> runSafely(task), 0, period.toSeconds(), TimeUnit.SECONDS);
    }

    public void scheduleOnce(Runnable task, Duration delay) {
        executor.schedule(() -> runSafely(task), delay.toSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    private static void runSafely(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            LOGGER.error("Scheduled task failed", e);
        }
    }
}
