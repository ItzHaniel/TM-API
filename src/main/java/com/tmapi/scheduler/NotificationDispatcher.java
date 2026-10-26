package com.tmapi.scheduler;

import com.tmapi.domain.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous notification dispatcher.
 * Each task notification is processed on the "deadlineExecutor" thread pool,
 * allowing the scheduler orchestration thread to remain unblocked.
 *
 * In production this would integrate with:
 *  - Email (JavaMailSender / SES)
 *  - Push notifications (Firebase FCM)
 *  - Webhooks / Slack
 *
 * The @Async annotation causes Spring to wrap this in a proxy that submits
 * the method to the named executor, returning a CompletableFuture immediately.
 */
@Component
@Slf4j
public class NotificationDispatcher {

    /**
     * Dispatches a single deadline notification asynchronously.
     * Returns CompletableFuture<Void> so callers can track completion
     * and batch-mark tasks after all notifications are sent.
     */
    @Async("deadlineExecutor")
    public CompletableFuture<Void> dispatchDeadlineNotification(Task task) {
        try {
            log.info(
                "[DeadlineNotification] Task '{}' (id={}) in workspace={} — deadline={}",
                task.getTitle(),
                task.getId(),
                task.getWorkspace().getId(),
                task.getDeadline()
            );

            // ── Integration point ──────────────────────────────────────────────
            // Replace the block below with your actual notification logic:
            //
            //   emailService.sendDeadlineReminder(task.getAssignee(), task);
            //   slackService.postDeadlineAlert(task);
            //   pushNotificationService.send(task.getAssignee().getId(), task);
            //
            // ──────────────────────────────────────────────────────────────────

            // Simulated async work (remove in production)
            simulateNotificationSend();

            return CompletableFuture.completedFuture(null);

        } catch (Exception ex) {
            log.error("Failed to dispatch deadline notification for task {}: {}", task.getId(), ex.getMessage(), ex);
            // Return completed future (not failed) so one failure doesn't block the batch
            return CompletableFuture.completedFuture(null);
        }
    }

    private void simulateNotificationSend() {
        // Simulate non-blocking I/O latency (e.g., HTTP call to mail service)
        // In production: replace with actual integration
    }
}
