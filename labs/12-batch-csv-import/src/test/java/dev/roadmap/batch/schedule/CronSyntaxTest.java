package dev.roadmap.batch.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/** Spring's cron dialect, proven with the same parser @Scheduled uses. */
class CronSyntaxTest {

    private static final LocalDateTime WED_18_NOV_0300 = LocalDateTime.of(2026, 11, 18, 3, 0);

    @Test
    void sixFields_secondsFirst() {
        assertThat(CronExpression.parse("0 0 2 * * *").next(WED_18_NOV_0300))
                .isEqualTo(LocalDateTime.of(2026, 11, 19, 2, 0));
    }

    @Test
    void unixFiveFieldCron_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> CronExpression.parse("0 2 * * *"));
    }

    @Test
    void macrosAndSpecialCharacters() {
        assertThat(CronExpression.parse("@daily").next(WED_18_NOV_0300)).isEqualTo(LocalDateTime.of(2026, 11, 19, 0, 0));
        // L = last day of month, MON-FRI ranges, d#n = n-th weekday of month
        assertThat(CronExpression.parse("0 0 23 L * *").next(WED_18_NOV_0300)).isEqualTo(LocalDateTime.of(2026, 11, 30, 23, 0));
        assertThat(CronExpression.parse("0 30 9 * * MON-FRI").next(WED_18_NOV_0300)).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 30));
        assertThat(CronExpression.parse("0 0 10 ? * MON#1").next(WED_18_NOV_0300)).isEqualTo(LocalDateTime.of(2026, 12, 7, 10, 0));
    }

    @Test
    void dashDisablesAScheduledMethod() {
        // @Scheduled(cron = "${job.cron:-}") turns a trigger off per environment without code changes.
        assertThat(Scheduled.CRON_DISABLED).isEqualTo("-");
    }
}
