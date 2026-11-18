package dev.roadmap.coreioc.registrar;

/** Registered programmatically by PaymentGatewayRegistrar, not by scanning. */
public record FeatureFlags(boolean fastCheckout) {
}
