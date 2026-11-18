package dev.roadmap.batch.importer;

import java.io.IOException;
import java.nio.file.Files;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * transactionImportJob = importFilesStep (partitioned, one worker per CSV file) then cleanupStep (tasklet).
 *
 * <p>No @EnableBatchProcessing: in Boot 4 that annotation switches Boot's batch auto-configuration OFF.
 * Boot provides the JDBC JobRepository (spring-boot-starter-batch-jdbc), the JobOperator and the transaction manager.
 *
 * <p>Why partitioning and not a multi-threaded step: each partition has its own reader, its own StepExecution and
 * its own ExecutionContext, so every file stays restartable from its last committed chunk. A classic multi-threaded
 * step shares one reader between threads, so a saved read position means nothing on restart.
 */
@Configuration(proxyBeanMethods = false)
public class ImportJobConfig {

    public static final String JOB_NAME = "transactionImportJob";
    public static final String WORKER_STEP = "importFileStep";
    static final int CHUNK_SIZE = 10;
    static final int SKIP_LIMIT = 10;

    private static final ResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();

    @Bean
    Job transactionImportJob(JobRepository jobRepository, Step importFilesStep, Step cleanupStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(importFilesStep)
                .next(cleanupStep)
                .build();
    }

    /** Manager step: creates one StepExecution per file and runs the worker step for each on its own thread. */
    @Bean
    Step importFilesStep(JobRepository jobRepository, Step importFileStep, MultiResourcePartitioner filePartitioner) {
        var partitionExecutor = new SimpleAsyncTaskExecutor("partition-");
        partitionExecutor.setConcurrencyLimit(4);
        return new StepBuilder("importFilesStep", jobRepository)
                .partitioner(WORKER_STEP, filePartitioner)
                .step(importFileStep)
                .taskExecutor(partitionExecutor)
                .build();
    }

    /** Worker step: the chunk-oriented read, process, write loop. One transaction per chunk of 10 items. */
    @Bean
    Step importFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                        FlatFileItemReader<TransactionLine> transactionReader,
                        TransactionProcessor transactionProcessor,
                        JdbcBatchItemWriter<Transaction> transactionWriter) {
        return new StepBuilder(WORKER_STEP, jobRepository)
                .<TransactionLine, Transaction>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(transactionReader)
                .processor(transactionProcessor)
                .writer(transactionWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class, InvalidTransactionException.class)
                .skipLimit(SKIP_LIMIT)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener(new ImportStepListener())
                .build();
    }

    /** One partition per file matching the 'input.files' job parameter, keyed 'fileName' in each ExecutionContext. */
    @Bean
    @JobScope
    MultiResourcePartitioner filePartitioner(@Value("#{jobParameters['input.files']}") String inputFiles) throws IOException {
        var partitioner = new MultiResourcePartitioner();
        partitioner.setResources(RESOLVER.getResources(inputFiles));
        return partitioner;
    }

    /** Step-scoped: a fresh reader per partition. Its read position is saved in that partition's ExecutionContext. */
    @Bean
    @StepScope
    FlatFileItemReader<TransactionLine> transactionReader(@Value("#{stepExecutionContext['fileName']}") Resource file) {
        return new FlatFileItemReaderBuilder<TransactionLine>()
                .name("transactionReader")
                .resource(file)
                .linesToSkip(1)
                .delimited()
                .names("id", "accountId", "amount", "currency", "bookedOn")
                .targetType(TransactionLine.class)
                .build();
    }

    @Bean
    @StepScope
    TransactionProcessor transactionProcessor(
            @Value("#{jobParameters['chaos.transient-fail-id']}") String transientFailId,
            @Value("#{jobParameters['chaos.crash-on-id']}") String crashOnId) {
        return new TransactionProcessor(transientFailId, crashOnId);
    }

    @Bean
    JdbcBatchItemWriter<Transaction> transactionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Transaction>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO bank_transaction (id, account_id, amount, currency, booked_on)
                        VALUES (:id, :accountId, :amount, :currency, :bookedOn)""")
                .itemSqlParameterSourceProvider(SimplePropertySqlParameterSource::new)
                .build();
    }

    /** Tasklet step: one-shot work that is not read/process/write. Runs only after every partition succeeded. */
    @Bean
    Step cleanupStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, Tasklet deleteImportedFiles) {
        return new StepBuilder("cleanupStep", jobRepository)
                .tasklet(deleteImportedFiles, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet deleteImportedFiles(@Value("#{jobParameters['input.files']}") String inputFiles) {
        return (contribution, chunkContext) -> {
            for (Resource file : RESOLVER.getResources(inputFiles)) {
                Files.deleteIfExists(file.getFile().toPath());
            }
            return RepeatStatus.FINISHED;
        };
    }
}
