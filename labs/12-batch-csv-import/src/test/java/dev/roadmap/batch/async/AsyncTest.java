package dev.roadmap.batch.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async needs no database, so these tests use ApplicationContextRunner: Boot's real TaskExecutionAutoConfiguration
 * plus the lab's async beans, in a tiny context that starts in milliseconds.
 */
class AsyncTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncConfig.class, ReportService.class, ReportGenerator.class)
            .withPropertyValues("spring.task.execution.mode=force", "spring.task.execution.thread-name-prefix=app-async-");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void asyncMethodReturnsImmediately_andRunsOnTheNamedPool() {
        runner.run(context -> {
            ReportService service = context.getBean(ReportService.class);

            CompletableFuture<Report.Section> future = service.fetchSection("balances");

            assertThat(future).as("returned before the 100 ms of work finished").isNotDone();
            ExecutionInfo ranOn = future.join().executedOn();
            assertThat(ranOn.thread()).startsWith("report-").isNotEqualTo(Thread.currentThread().getName());
        });
    }

    @Test
    void selfInvocation_bypassesTheProxy_andRunsOnTheCallerThread() {
        runner.run(context -> {
            ExecutionInfo ranOn = context.getBean(ReportService.class).currentExecutionViaSelfCall().join();

            assertThat(ranOn.thread()).isEqualTo(Thread.currentThread().getName());
        });
    }

    @Test
    void plainAsync_usesBootsPlatformThreadPool_whenVirtualThreadsAreOff() {
        runner.run(context -> {
            ExecutionInfo ranOn = context.getBean(ReportService.class).currentExecution().join();

            assertThat(ranOn.thread()).startsWith("app-async-");
            assertThat(ranOn.virtual()).isFalse();
        });
    }

    @Test
    void plainAsync_runsOnVirtualThreads_whenThePropertyIsOn_butTheCustomPoolDoesNot() {
        runner.withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            ReportService service = context.getBean(ReportService.class);

            assertThat(service.currentExecution().join().virtual()).isTrue();
            assertThat(service.fetchSection("fx").join().executedOn().virtual())
                    .as("a user-defined ThreadPoolTaskExecutor ignores spring.threads.virtual.enabled")
                    .isFalse();
        });
    }

    @Test
    void mdcFollowsTheTask_onBootsExecutor_andOnTheCustomPool() {
        runner.run(context -> {
            ReportService service = context.getBean(ReportService.class);
            MDC.put("requestId", "req-42");

            assertThat(service.currentExecution().join().requestId())
                    .as("Boot applied the TaskDecorator bean to applicationTaskExecutor").isEqualTo("req-42");
            assertThat(service.fetchSection("accounts").join().executedOn().requestId()).isEqualTo("req-42");
        });
    }

    @Test
    void mdcFollowsTheTask_onVirtualThreadsToo() {
        runner.withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            MDC.put("requestId", "req-7");

            ExecutionInfo ranOn = context.getBean(ReportService.class).currentExecution().join();

            assertThat(ranOn.virtual()).isTrue();
            assertThat(ranOn.requestId()).isEqualTo("req-7");
        });
    }

    @Test
    void voidAsyncFailure_goesToTheAsyncUncaughtExceptionHandler() {
        runner.run(context -> {
            context.getBean(ReportService.class).audit(" ");   // returns normally: the caller never sees the exception

            AsyncFailureHandler handler = context.getBean(AsyncFailureHandler.class);
            await().atMost(Duration.ofSeconds(5)).until(() -> handler.failures() == 1);
        });
    }

    @Test
    void reportFansOutInParallel_andDegradesAFailedSection() {
        runner.run(context -> {
            Report report = context.getBean(ReportGenerator.class)
                    .generate(List.of("accounts", "balances", "cards", "broken-fx"));

            assertThat(report.sections()).extracting(Report.Section::content)
                    .containsExactly("data for accounts", "data for balances", "data for cards", "unavailable");
            assertThat(report.sections().subList(0, 3)).extracting(s -> s.executedOn().thread())
                    .as("each section ran on its own pool thread at the same time").doesNotHaveDuplicates();
        });
    }

    @Test
    void withoutAnyExecutorBean_asyncFallsBackToSimpleAsyncTaskExecutor_aNewThreadPerCall() {
        new ApplicationContextRunner()
                .withUserConfiguration(AsyncOnly.class, ReportService.class)
                .run(context -> {
                    ExecutionInfo ranOn = context.getBean(ReportService.class).currentExecution().join();

                    assertThat(ranOn.thread()).startsWith("SimpleAsyncTaskExecutor-");
                });
    }

    @EnableAsync
    static class AsyncOnly {
    }
}
