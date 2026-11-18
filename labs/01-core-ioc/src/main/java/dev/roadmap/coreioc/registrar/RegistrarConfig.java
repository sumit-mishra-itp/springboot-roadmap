package dev.roadmap.coreioc.registrar;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(PaymentGatewayRegistrar.class)
public class RegistrarConfig {
}
