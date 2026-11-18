package dev.roadmap.batch.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.roadmap.batch.TestcontainersConfiguration;

/**
 * The whole job against a real Postgres (Testcontainers) and a real JDBC JobRepository.
 * One Spring context and one container for every test in this class. The scheduler is switched off so it can't
 * launch jobs in the middle of a test.
 */
@SpringBootTest(properties = "importer.schedule.enabled=false")
@SpringBatchTest
@Import(TestcontainersConfiguration.class)
class ImportJobTest {

    private static final String HEADER = "id,accountId,amount,currency,bookedOn";

    @Autowired JobOperatorTestUtils jobOperatorTestUtils;   // the single Job bean is injected into it
    @Autowired JobRepositoryTestUtils jobRepositoryTestUtils;
    @Autowired JdbcClient jdbc;

    @TempDir Path inbox;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("TRUNCATE bank_transaction").update();
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    void importsEveryValidRow_onePartitionPerFile_thenCleansUp() throws Exception {
        writeCsv("a.csv", validRows(1, 25));
        writeCsv("b.csv", validRows(26, 15));

        JobExecution execution = jobOperatorTestUtils.startJob(params().toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(rowCount()).isEqualTo(40);
        List<StepExecution> partitions = workerSteps(execution);
        assertThat(partitions).hasSize(2)
                .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(BatchStatus.COMPLETED));
        assertThat(partitions).extracting(StepExecution::getWriteCount).containsExactlyInAnyOrder(25L, 15L);
        // 25 items with chunk size 10 = 3 chunk transactions (10, 10, 5)
        assertThat(partitions).extracting(StepExecution::getCommitCount).contains(3L);
        assertThat(stepStatus(execution, "cleanupStep")).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.list(inbox)).as("tasklet step deleted the imported files").isEmpty();
    }

    @Test
    void skipsUnreadableAndInvalidRows_filtersZeroAmounts_andCountsEach() throws Exception {
        List<String> rows = new ArrayList<>(validRows(1, 10));
        rows.add("T0901,ACC-1,10.00,EUR");                 // 4 columns -> FlatFileParseException -> read skip
        rows.add("T0902,ACC-1,ten,EUR,2026-11-17");        // bad amount -> InvalidTransactionException -> process skip
        rows.add("T0903,ACC-1,10.00,XXX,2026-11-17");      // unknown currency -> process skip
        rows.add("T0904,ACC-1,0.00,EUR,2026-11-17");       // zero amount -> processor returns null -> filtered
        rows.add("T0905,ACC-1,0,GBP,2026-11-17");          // filtered
        writeCsv("mixed.csv", rows);

        JobExecution execution = jobOperatorTestUtils.startJob(params().toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution worker = workerSteps(execution).getFirst();
        assertThat(worker.getReadCount()).isEqualTo(14);   // the unparseable line never became an item
        assertThat(worker.getReadSkipCount()).isEqualTo(1);
        assertThat(worker.getProcessSkipCount()).isEqualTo(2);
        assertThat(worker.getFilterCount()).isEqualTo(2);
        assertThat(worker.getWriteCount()).isEqualTo(10);
        // Batch 6's ChunkOrientedStep skips the bad item without rolling back and replaying the chunk (Batch 5 did).
        assertThat(worker.getRollbackCount()).isZero();
        assertThat(worker.getExitStatus().getExitCode()).isEqualTo(ImportStepListener.COMPLETED_WITH_SKIPS);
        assertThat(rowCount()).isEqualTo(10);
    }

    @Test
    void failsTheJob_whenTheSkipLimitIsExceeded() throws Exception {
        List<String> rows = new ArrayList<>(validRows(1, 5));
        for (int i = 0; i <= ImportJobConfig.SKIP_LIMIT; i++) {
            rows.add("T09%02d,ACC-1,1.00,XXX,2026-11-17".formatted(i));
        }
        writeCsv("garbage.csv", rows);

        JobExecution execution = jobOperatorTestUtils.startJob(params().toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(workerSteps(execution).getFirst().getProcessSkipCount()).isEqualTo(ImportJobConfig.SKIP_LIMIT);
        assertThat(Files.list(inbox)).as("cleanup never ran").hasSize(1);
    }

    @Test
    void retriesATransientFailure_andStillImportsTheItem() throws Exception {
        writeCsv("flaky.csv", validRows(1, 15));

        JobExecution execution = jobOperatorTestUtils.startJob(
                params().addString("chaos.transient-fail-id", "T0007", false).toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution worker = workerSteps(execution).getFirst();
        assertThat(worker.getSkipCount()).isZero();
        assertThat(worker.getWriteCount()).isEqualTo(15);
        // Batch 6 retries the single item in place (Framework 7 core retry), so the chunk is NOT rolled back...
        assertThat(worker.getRollbackCount()).isZero();
        // ...and the retry waits for the default RetryPolicy back-off (1 s) before the second attempt.
        assertThat(Duration.between(worker.getStartTime(), worker.getEndTime())).isGreaterThan(Duration.ofMillis(900));
        assertThat(jdbc.sql("SELECT count(*) FROM bank_transaction WHERE id = 'T0007'").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void restartResumesFromTheLastCommittedChunk_withoutDuplicates() throws Exception {
        writeCsv("statement.csv", validRows(1, 35));
        writeCsv("other-branch.csv", validRows(101, 12));   // a second, clean partition
        JobParametersBuilder identifying = params();

        // Run 1: crash on item 25. Chunks 1-10 and 11-20 are committed, chunk 21-30 rolls back.
        JobExecution failed = jobOperatorTestUtils.startJob(new JobParametersBuilder(identifying.toJobParameters())
                .addString("chaos.crash-on-id", "T0025", false)
                .toJobParameters());
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(rowCount()).as("20 committed from the failed file + 12 from the clean one").isEqualTo(32);
        assertThat(Files.list(inbox)).as("cleanup did not run").hasSize(2);

        // Run 2: same IDENTIFYING parameters (the crash flag was non-identifying) = same JobInstance = a restart.
        JobExecution restarted = jobOperatorTestUtils.startJob(identifying.toJobParameters());

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getId()).isEqualTo(failed.getJobInstance().getId());
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        assertThat(workerSteps(restarted)).as("the COMPLETED partition is not re-run").hasSize(1);
        StepExecution resumedWorker = workerSteps(restarted).getFirst();
        assertThat(resumedWorker.getReadCount()).as("items 21..35 only").isEqualTo(15);
        assertThat(rowCount()).isEqualTo(47);
        assertThat(jdbc.sql("SELECT count(DISTINCT id) FROM bank_transaction").query(Long.class).single())
                .isEqualTo(47);
    }

    private JobParametersBuilder params() {
        // Forward slashes: a Spring resource pattern is a URL, not a Windows path.
        String pattern = "file:" + inbox.toAbsolutePath().toString().replace('\\', '/') + "/*.csv";
        JobParameters unique = jobOperatorTestUtils.getUniqueJobParameters();   // adds a random identifying long
        return new JobParametersBuilder(unique).addString("input.files", pattern);
    }

    private static List<String> validRows(int firstId, int count) {
        List<String> rows = new ArrayList<>();
        for (int id = firstId; id < firstId + count; id++) {
            rows.add("T%04d,ACC-%d,%d.50,EUR,2026-11-17".formatted(id, id % 3, id));
        }
        return rows;
    }

    private void writeCsv(String name, List<String> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.addAll(rows);
        Files.write(inbox.resolve(name), lines);
    }

    private long rowCount() {
        return jdbc.sql("SELECT count(*) FROM bank_transaction").query(Long.class).single();
    }

    private static List<StepExecution> workerSteps(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(s -> s.getStepName().startsWith(ImportJobConfig.WORKER_STEP + ":"))
                .toList();
    }

    // Not returning StepExecution on purpose: @SpringBatchTest treats such methods as step-scope factories.
    private static BatchStatus stepStatus(JobExecution execution, String name) {
        return execution.getStepExecutions().stream().filter(s -> s.getStepName().equals(name))
                .map(StepExecution::getStatus).findFirst().orElseThrow();
    }
}
