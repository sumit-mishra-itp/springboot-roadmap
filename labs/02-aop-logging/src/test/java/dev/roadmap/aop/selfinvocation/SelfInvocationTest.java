package dev.roadmap.aop.selfinvocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.roadmap.aop.CallLog;

@SpringBootTest
class SelfInvocationTest {

    @Autowired
    ReportService reportService;

    @Autowired
    CallLog callLog;

    @BeforeEach
    void clearLog() {
        callLog.clear();
    }

    @Test
    void thisCall_bypassesTheProxy_soNoAdviceRuns() {
        List<String> reports = reportService.renderAllDirect(List.of("a", "b"));

        assertThat(reports).containsExactly("report:a", "report:b");
        assertThat(callLog.events()).isEmpty();
    }

    @Test
    void callingThroughInjectedSelf_goesThroughTheProxy() {
        reportService.renderAllViaSelf(List.of("a", "b"));

        assertThat(callLog.events()).containsExactly(
                "logging:enter:render", "logging:exit:render",
                "logging:enter:render", "logging:exit:render");
    }

    @Test
    void aopContextCurrentProxy_goesThroughTheProxy_whenExposeProxyIsOn() {
        reportService.renderAllViaCurrentProxy(List.of("a"));

        assertThat(callLog.events()).containsExactly("logging:enter:render", "logging:exit:render");
    }

    @Test
    void finalMethod_isNotAdvised_andRunsOnTheEmptyProxyInstance() {
        assertThat(AopUtils.isCglibProxy(reportService)).isTrue();

        boolean sawField = reportService.hasCallLog();

        assertThat(sawField).isFalse();          // the proxy's own field: the constructor never ran on it
        assertThat(callLog.events()).isEmpty();  // and @Loggable was ignored
    }
}
