package dev.roadmap.jpa.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    /** Suspends the caller's transaction and commits on its own: the audit row survives a caller rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(String message) {
        auditLogs.save(new AuditLog(message));
    }

    /** REQUIRED (the default): joins the caller's transaction, so it commits or rolls back with it. */
    @Transactional
    public void recordInCallerTransaction(String message) {
        auditLogs.save(new AuditLog(message));
    }

    /** Joins the caller's transaction and fails: the shared transaction is marked rollback-only on the way out. */
    @Transactional
    public void recordThenFail(String message) {
        auditLogs.save(new AuditLog(message));
        throw new IllegalStateException("audit store rejected: " + message);
    }
}
