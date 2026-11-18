package dev.roadmap.aop.inventory;

/** An interface on purpose: it shows which proxy type the @Retryable post-processor picks on its own. */
public interface StockNotifier {

    String notifyLowStock(String sku);
}
