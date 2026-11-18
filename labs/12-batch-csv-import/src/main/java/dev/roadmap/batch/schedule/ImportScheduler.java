package dev.roadmap.batch.schedule;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Two triggers for the same job: a nightly cron and a fixed-delay inbox poll.
 * Both run on Boot's auto-configured scheduler, which has ONE thread by default, so they never overlap each other.
 * JobOperator.start() is synchronous by default, so the scheduler thread is busy for the whole job run.
 *
 * <p>In a cluster every node would fire. Guard the methods with ShedLock's @SchedulerLock (note 12, section 7).
 */
@Component
@EnableScheduling
@ConditionalOnBooleanProperty("importer.schedule.enabled")
public class ImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportScheduler.class);

    private final JobOperator jobOperator;
    private final Job transactionImportJob;
    private final String inbox;

    public ImportScheduler(JobOperator jobOperator, Job transactionImportJob, @Value("${importer.inbox}") String inbox) {
        this.jobOperator = jobOperator;
        this.transactionImportJob = transactionImportJob;
        this.inbox = inbox;
    }

    @Scheduled(cron = "${importer.schedule.cron}", zone = "${importer.schedule.zone}")
    public void nightlyImport() throws Exception {
        launch("cron");
    }

    @Scheduled(fixedDelayString = "${importer.schedule.inbox-poll}")
    public void pollInbox() throws Exception {
        launch("inbox-poll");
    }

    private void launch(String trigger) throws Exception {
        if (new PathMatchingResourcePatternResolver().getResources(inbox).length == 0) {
            log.debug("{}: inbox {} is empty", trigger, inbox);
            return;
        }
        JobExecution execution = jobOperator.start(transactionImportJob, parameters(trigger));
        log.info("{}: import finished with {}", trigger, execution == null ? "no execution" : execution.getStatus());
    }

    private JobParameters parameters(String trigger) {
        return new JobParametersBuilder()
                .addString("input.files", inbox)
                .addString("run.id", UUID.randomUUID().toString())  // identifying: a new JobInstance per run
                .addString("trigger", trigger, false)               // non-identifying: informational only
                .toJobParameters();
    }
}
