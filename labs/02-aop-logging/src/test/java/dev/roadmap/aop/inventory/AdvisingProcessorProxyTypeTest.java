package dev.roadmap.aop.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AdvisingProcessorProxyTypeTest {

    @Autowired
    StockNotifier notifier;

    @Test
    void retryOnlyBean_followsTheGlobalCglibDefault() {
        assertThat(AopUtils.isCglibProxy(notifier)).isTrue();
        assertThat(notifier).isInstanceOf(EmailStockNotifier.class);
        // a single advisor: the retry one. No AspectJ advisors, so the auto-proxy creator didn't build this proxy
        assertThat(((Advised) notifier).getAdvisors()).hasSize(1);
        assertThat(notifier.notifyLowStock("SKU-9")).isEqualTo("low-stock-mail:SKU-9");
    }
}
