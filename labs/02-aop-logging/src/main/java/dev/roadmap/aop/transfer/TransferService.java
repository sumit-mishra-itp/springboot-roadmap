package dev.roadmap.aop.transfer;

import org.springframework.stereotype.Service;

import dev.roadmap.aop.CallLog;
import dev.roadmap.aop.audit.Audited;
import dev.roadmap.aop.logging.Loggable;

/** One method, two aspects. Which wraps which is decided by the aspects' @Order, not by annotation order here. */
@Service
public class TransferService {

    private final CallLog callLog;

    public TransferService(CallLog callLog) {
        this.callLog = callLog;
    }

    @Loggable
    @Audited(action = "FUNDS_TRANSFER")
    public String transfer(String from, String to, long amountCents) {
        callLog.record("target:transfer");
        return from + "->" + to + ":" + amountCents;
    }
}
