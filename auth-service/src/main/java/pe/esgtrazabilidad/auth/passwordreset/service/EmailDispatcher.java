package pe.esgtrazabilidad.auth.passwordreset.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.email.EmailSendException;
import pe.esgtrazabilidad.auth.email.EmailSender;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore;

/**
 * Sends on a separate thread, so the request answers as fast (and as uniformly) whether or not an
 * email goes out, and a slow or failing provider never reaches the caller. A send that works marks its
 * request row as sent (the daily cap counts those). A failure is only logged: the adapter already
 * logged its fixed reason, and nothing is retried.
 */
@Component
class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger("esg.auth.email");

    private final EmailSender sender;
    private final PasswordResetStore store;
    private final ThreadPoolExecutor executor;

    EmailDispatcher(EmailSender sender, PasswordResetStore store) {
        this.sender = sender;
        this.store = store;
        AtomicInteger number = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                1,
                2,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(50),
                runnable -> {
                    Thread thread = new Thread(runnable, "email-dispatch-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    void resetCode(UUID requestId, String to, String code) {
        dispatch(requestId, () -> sender.sendResetCode(to, code));
    }

    void passwordChanged(UUID requestId, String to, Instant when) {
        dispatch(requestId, () -> sender.sendPasswordChanged(to, when));
    }

    private void dispatch(UUID requestId, Runnable send) {
        try {
            executor.execute(() -> {
                try {
                    send.run();
                    store.markSent(requestId);
                } catch (EmailSendException failure) {
                    // Logged by the adapter with its fixed reason.
                } catch (RuntimeException unexpected) {
                    log.warn("event=email_failed reason=unexpected type={}", unexpected.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException queueFull) {
            log.warn("event=email_failed reason=queue_full");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
