package dev.roadmap.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Why a SecurityContext "disappears" in @Async code: the default strategy is a ThreadLocal, and @Async simply
 * hands work to an executor thread. This test uses that same mechanism (a plain executor) directly.
 */
class SecurityContextPropagationTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext(); // what SecurityContextHolderFilter does at the end of every request
    }

    @Test
    void defaultStrategy_isThreadLocal() {
        assertThat(SecurityContextHolder.getContextHolderStrategy().getClass().getSimpleName())
                .isEqualTo("ThreadLocalSecurityContextHolderStrategy");
    }

    @Test
    void plainExecutorThread_seesNoAuthentication() throws Exception {
        login("alice");

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Authentication seenByWorker = pool.submit(SecurityContextPropagationTest::current).get();
            assertThat(seenByWorker).isNull();
        }
    }

    @Test
    void delegatingExecutor_copiesTheContextToTheWorkerThread() throws Exception {
        login("alice");

        try (ExecutorService pool = new DelegatingSecurityContextExecutorService(Executors.newSingleThreadExecutor())) {
            Authentication seenByWorker = pool.submit(SecurityContextPropagationTest::current).get();
            assertThat(seenByWorker.getName()).isEqualTo("alice");
        }
    }

    private static void login(String name) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TestingAuthenticationToken(name, "n/a", "ROLE_USER"));
        SecurityContextHolder.setContext(context);
    }

    private static Authentication current() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
