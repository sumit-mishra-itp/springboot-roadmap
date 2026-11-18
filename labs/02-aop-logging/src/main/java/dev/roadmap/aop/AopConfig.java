package dev.roadmap.aop;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Boot already adds @EnableAspectJAutoProxy(proxyTargetClass = true) via AopAutoConfiguration.
 * Declaring it again only to turn on exposeProxy is safe: all such annotations are merged into ONE
 * auto-proxy creator, and the "strongest" settings win (proxyTargetClass stays true).
 *
 * Boot does not auto-configure the Framework 7 resilience annotations, so @EnableResilientMethods is
 * required, otherwise @Retryable / @ConcurrencyLimit are silently ignored.
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableResilientMethods
public class AopConfig {
}
