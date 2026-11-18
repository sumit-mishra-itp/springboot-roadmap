package dev.roadmap.batch.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Step metrics and skip logging. Stateless, because one instance serves every partition's worker step concurrently.
 * afterStep() is the classic place to turn "completed but with skips" into its own exit code, which a job flow
 * (or the operator's dashboard) can branch on.
 */
public class ImportStepListener implements StepExecutionListener, SkipListener<TransactionLine, Transaction> {

    public static final String COMPLETED_WITH_SKIPS = "COMPLETED WITH SKIPS";

    private static final Logger log = LoggerFactory.getLogger(ImportStepListener.class);

    @Override
    public ExitStatus afterStep(StepExecution step) {
        log.info("{}: read={} written={} filtered={} skipped(read/process/write)={}/{}/{} commits={} rollbacks={}",
                step.getStepName(), step.getReadCount(), step.getWriteCount(), step.getFilterCount(),
                step.getReadSkipCount(), step.getProcessSkipCount(), step.getWriteSkipCount(),
                step.getCommitCount(), step.getRollbackCount());
        boolean completedWithSkips = step.getExitStatus().getExitCode().equals(ExitStatus.COMPLETED.getExitCode())
                && step.getSkipCount() > 0;
        return completedWithSkips ? new ExitStatus(COMPLETED_WITH_SKIPS) : step.getExitStatus();
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("skipped unreadable line: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(TransactionLine line, Throwable t) {
        log.warn("skipped invalid transaction {}: {}", line.id(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Transaction item, Throwable t) {
        log.warn("skipped unwritable transaction {}: {}", item.id(), t.getMessage());
    }
}
