package dev.roadmap.batch.async;

import java.util.Map;

import org.slf4j.MDC;

import org.springframework.core.task.TaskDecorator;

/**
 * Copies the submitting thread's MDC (a ThreadLocal) onto the worker thread for the duration of the task.
 * decorate() runs on the CALLER thread, so that is where the snapshot is taken. The returned Runnable runs on the worker.
 *
 * <p>It restores the worker's previous MDC instead of clearing it: with CallerRunsPolicy the "worker" can be the
 * caller itself, and clearing would wipe the caller's own logging context.
 * The same pattern carries a SecurityContext (Spring Security's DelegatingSecurityContextRunnable.wrap does it for you).
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setContext(callerContext);
            try {
                task.run();
            } finally {
                setContext(previous);
            }
        };
    }

    private static void setContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
