package dev.roadmap.batch.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Real scheduler (Boot's TaskSchedulingAutoConfiguration), mocked JobOperator: proves the triggers, not the job. */
class ImportSchedulerTest {

    @TempDir Path inbox;

    @Test
    void cronAndFixedDelayBothFire_onTheSingleDefaultSchedulerThread() throws Exception {
        Files.writeString(inbox.resolve("today.csv"), "id,accountId,amount,currency,bookedOn\n");
        Map<String, String> threadByTrigger = new ConcurrentHashMap<>();
        JobOperator jobOperator = mock(JobOperator.class);
        doAnswer(call -> {
            JobParameters params = call.getArgument(1);
            threadByTrigger.put(params.getString("trigger"), Thread.currentThread().getName());
            return null;
        }).when(jobOperator).start(any(Job.class), any(JobParameters.class));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withBean(JobOperator.class, () -> jobOperator)
                .withBean(Job.class, () -> mock(Job.class))
                .withUserConfiguration(ImportScheduler.class)
                .withPropertyValues(
                        "importer.schedule.enabled=true",
                        "importer.inbox=file:" + inbox.toAbsolutePath().toString().replace('\\', '/') + "/*.csv",
                        "importer.schedule.cron=* * * * * *",       // every second (6 fields: seconds first)
                        "importer.schedule.zone=UTC",
                        "importer.schedule.inbox-poll=PT0.2S")
                .run(context -> {
                    await().atMost(Duration.ofSeconds(5))
                            .until(() -> threadByTrigger.keySet().containsAll(List.of("cron", "inbox-poll")));

                    assertThat(threadByTrigger.values()).containsOnly("scheduling-1");
                });
    }

    @Test
    void fixedRate_neverOverlapsItself_onTheSingleThreadScheduler() {
        runSlowFixedRateTask().run(context -> {
            SlowFixedRateTask task = awaitThreeRuns(context.getBean(SlowFixedRateTask.class));

            assertThat(task.maxConcurrent.get()).as("runs queue up behind each other").isEqualTo(1);
            long gap = task.starts.get(2) - task.starts.get(1);
            assertThat(gap).as("rate is 50 ms but each run takes 200 ms").isGreaterThanOrEqualTo(190);
        });
    }

    @Test
    void fixedRate_stillNeverOverlapsItself_withABiggerPool() {
        // ScheduledThreadPoolExecutor.scheduleAtFixedRate: late runs "start late, but will not concurrently execute".
        runSlowFixedRateTask().withPropertyValues("spring.task.scheduling.pool.size=4").run(context -> {
            SlowFixedRateTask task = awaitThreeRuns(context.getBean(SlowFixedRateTask.class));

            assertThat(task.maxConcurrent.get()).isEqualTo(1);
        });
    }

    @Test
    void fixedRate_overlapsItself_onTheVirtualThreadScheduler() {
        // SimpleAsyncTaskScheduler starts a new (virtual) thread for every fixed-rate execution.
        runSlowFixedRateTask().withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            SlowFixedRateTask task = awaitThreeRuns(context.getBean(SlowFixedRateTask.class));

            assertThat(task.maxConcurrent.get()).isGreaterThan(1);
        });
    }

    private static ApplicationContextRunner runSlowFixedRateTask() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SlowFixedRateTask.class);
    }

    private static SlowFixedRateTask awaitThreeRuns(SlowFixedRateTask task) {
        await().atMost(Duration.ofSeconds(5)).until(() -> task.starts.size() >= 3);
        return task;
    }

    @EnableScheduling
    static class SlowFixedRateTask {

        final List<Long> starts = new CopyOnWriteArrayList<>();
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();

        @Scheduled(fixedRate = 50)
        void work() throws InterruptedException {
            starts.add(System.currentTimeMillis());
            maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(200);
            running.decrementAndGet();
        }
    }
}
