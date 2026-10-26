package com.tmapi.scheduler;

import com.tmapi.domain.Task;
import com.tmapi.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Asynchronous deadline notification pipeline.
 *
 * Design:
 *  1. Scheduled trigger fires every 5 minutes
 *  2. Finds tasks whose deadline is within the next 24 hours and not yet notified
 *  3. Dispatches each notification asynchronously on the "deadlineExecutor" pool
 *  4. Bulk-marks tasks as notified using optimistic-locking-safe bulk UPDATE
 *
 * Race condition prevention:
 *  - The deadlineNotified flag check in the query + the bulk UPDATE are idempotent
 *  - If two scheduler instances run concurrently (multi-instance deployment),
 *    the @Transactional + optimistic locking on Task (@Version) ensures
 *    only one instance processes a given task — the other will see a stale version
 *    and either retry or skip (deadlineNotified = true already)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadlineNotificationScheduler {

    private final TaskRepository taskRepository;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Main scheduler: runs every 5 minutes, fixed delay to avoid overlapping runs.
     * Uses the Spring scheduling thread (single thread, non-blocking orchestration).
     * Heavy work is offloaded to the deadlineExecutor async pool.
     */
    @Scheduled(fixedDelayString = "${app.scheduler.deadline-check-interval-ms:300000}")
    public void checkUpcomingDeadlines() {
        Instant now  = Instant.now();
        Instant soon = now.plus(24, ChronoUnit.HOURS);

        log.debug("Running deadline check: window [{} → {}]", now, soon);

        List<Task> dueTasks = taskRepository.findPendingDeadlineNotifications(now, soon);

        if (dueTasks.isEmpty()) {
            log.debug("No pending deadline notifications found");
            return;
        }

        log.info("Dispatching deadline notifications for {} tasks", dueTasks.size());

        // Dispatch each notification asynchronously on the deadlineExecutor pool
        List<CompletableFuture<Void>> futures = dueTasks.stream()
            .map(notificationDispatcher::dispatchDeadlineNotification)
            .toList();

        // After all dispatches, mark tasks as notified in a single bulk UPDATE
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> markTasksAsNotified(dueTasks))
            .exceptionally(ex -> {
                log.error("Error during deadline notification batch", ex);
                return null;
            });
    }

    @Transactional
    public void markTasksAsNotified(List<Task> tasks) {
        List<UUID> ids = tasks.stream().map(Task::getId).toList();
        taskRepository.markDeadlineNotified(ids);
        log.info("Marked {} tasks as deadline-notified", ids.size());
    }
}
