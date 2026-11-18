package dev.roadmap.aop.inventory;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Only @Retryable, no custom aspect matches it: so RetryAnnotationBeanPostProcessor (not the AspectJ auto-proxy
 * creator) builds this proxy alone. Framework 7 applies the global CGLIB default to such processors too.
 */
@Component
public class EmailStockNotifier implements StockNotifier {

    @Retryable(delay = 0)
    @Override
    public String notifyLowStock(String sku) {
        return "low-stock-mail:" + sku;
    }
}
