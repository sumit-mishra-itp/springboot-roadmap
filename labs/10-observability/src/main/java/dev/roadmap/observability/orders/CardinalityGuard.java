package dev.roadmap.observability.orders;

import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every distinct tag value is a new time series. A client-controlled tag (channel) could
 * otherwise create unbounded series and exhaust Prometheus memory. Boot binds every
 * MeterFilter bean to the auto-configured registry.
 */
@Configuration(proxyBeanMethods = false)
class CardinalityGuard {

    static final int MAX_CHANNELS = 5;

    @Bean
    MeterFilter capOrderChannels() {
        return channelCap();
    }

    /** Past MAX_CHANNELS distinct values, new orders.placed series are denied (a no-op counter is returned). */
    static MeterFilter channelCap() {
        return MeterFilter.maximumAllowableTags("orders.placed", "channel", MAX_CHANNELS, MeterFilter.deny());
    }
}
