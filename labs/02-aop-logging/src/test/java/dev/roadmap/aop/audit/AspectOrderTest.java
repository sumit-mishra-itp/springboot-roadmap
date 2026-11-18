package dev.roadmap.aop.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.roadmap.aop.CallLog;
import dev.roadmap.aop.transfer.TransferService;

@SpringBootTest
class AspectOrderTest {

    @Autowired
    TransferService transferService;

    @Autowired
    CallLog callLog;

    @BeforeEach
    void clearLog() {
        callLog.clear();
    }

    @Test
    void lowerOrderValue_isOutermost_firstInLastOut() {
        String result = transferService.transfer("ACC-1", "ACC-2", 5_000);

        assertThat(result).isEqualTo("ACC-1->ACC-2:5000");
        assertThat(callLog.events()).containsExactly(
                "audit:enter:FUNDS_TRANSFER",   // AuditAspect @Order(1)
                "logging:enter:transfer",       // LoggingAspect @Order(2)
                "target:transfer",
                "logging:exit:transfer",
                "audit:exit:FUNDS_TRANSFER");
    }
}
