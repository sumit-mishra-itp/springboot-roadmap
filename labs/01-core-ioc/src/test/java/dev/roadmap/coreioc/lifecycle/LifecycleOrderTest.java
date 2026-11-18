package dev.roadmap.coreioc.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Plain container (no Boot) so you can see exactly what the core IoC container does on its own. */
class LifecycleOrderTest {

    @Test
    void callbacksFireInDocumentedOrder() {
        LifecycleBean bean;
        try (var context = new AnnotationConfigApplicationContext(LifecycleConfig.class)) {
            bean = context.getBean(LifecycleBean.class);
            assertThat(bean.events()).containsExactly(
                    "constructor",
                    "BeanNameAware.setBeanName",
                    "BeanPostProcessor.before",            // our BPP runs first...
                    "@PostConstruct",                      // ...because CommonAnnotationBeanPostProcessor is "internal" and re-registered LAST
                    "InitializingBean.afterPropertiesSet",
                    "@Bean(initMethod)",
                    "BeanPostProcessor.after");            // where AOP proxies get created
        } // close() destroys singletons

        assertThat(bean.events()).endsWith(
                "@PreDestroy",
                "DisposableBean.destroy",
                "@Bean(destroyMethod)");
    }
}
