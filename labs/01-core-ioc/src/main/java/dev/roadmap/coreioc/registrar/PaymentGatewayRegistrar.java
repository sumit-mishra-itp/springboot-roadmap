package dev.roadmap.coreioc.registrar;

import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;

/**
 * Spring Framework 7: BeanRegistrar registers beans programmatically, with logic (loops, ifs).
 * It replaces "register several beans inside one @Bean method" and most BeanDefinitionRegistryPostProcessor use.
 * Activate it with @Import(PaymentGatewayRegistrar.class) on a @Configuration class.
 */
public class PaymentGatewayRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        String provider = env.getProperty("payments.provider", "stripe");
        registry.registerBean("paymentGateway", PaymentGateway.class,
                spec -> spec.supplier(context -> new PaymentGateway(provider)));

        if (env.getProperty("features.fast-checkout", Boolean.class, false)) {
            registry.registerBean("featureFlags", FeatureFlags.class,
                    spec -> spec.supplier(context -> new FeatureFlags(true)));
        }
    }
}
