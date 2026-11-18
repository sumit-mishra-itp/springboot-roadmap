package dev.roadmap.aop.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.roadmap.aop.CallLog;
import dev.roadmap.aop.selfinvocation.ReportService;

@SpringBootTest
class LoggingAspectTest {

    @Autowired
    ReportService reportService;

    @Autowired
    CallLog callLog;

    @BeforeEach
    void clearLog() {
        callLog.clear(); // the context (and this singleton) is cached across test classes
    }

    @Test
    void loggableMethod_isWrappedByAroundAdvice_andResultIsUnchanged() {
        String result = reportService.render("sales");

        assertThat(result).isEqualTo("report:sales");
        assertThat(callLog.events()).containsExactly("logging:enter:render", "logging:exit:render");
    }
}
