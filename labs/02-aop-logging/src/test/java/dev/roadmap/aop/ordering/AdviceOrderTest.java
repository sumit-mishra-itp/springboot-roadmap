package dev.roadmap.aop.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.roadmap.aop.CallLog;

@SpringBootTest
class AdviceOrderTest {

    @Autowired
    OrderProbe probe;

    @Autowired
    CallLog callLog;

    @BeforeEach
    void clearLog() {
        callLog.clear();
    }

    @Test
    void successPath_runsAdviceInTypeOrder_afterIsLikeFinally() {
        probe.succeed();

        assertThat(callLog.events()).containsExactly(
                "around:before",        // Around has the highest precedence: outermost
                "before",
                "target",
                "afterReturning:ok",
                "after",                // @After = "after finally", so it runs after AfterReturning
                "around:after");
    }

    @Test
    void failurePath_runsAfterThrowing_thenAfter_andAroundSeesTheException() {
        assertThatIllegalStateException().isThrownBy(probe::fail).withMessage("boom");

        assertThat(callLog.events()).containsExactly(
                "around:before",
                "before",
                "target",
                "afterThrowing:boom",   // AfterReturning is skipped
                "after",                // still runs, like finally
                "around:caught");
    }
}
