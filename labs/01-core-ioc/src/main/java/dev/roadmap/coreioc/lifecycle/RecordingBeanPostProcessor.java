package dev.roadmap.coreioc.lifecycle;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * A plain (un-ordered) BeanPostProcessor. Surprisingly, its "before" hook runs BEFORE @PostConstruct:
 * CommonAnnotationBeanPostProcessor (which calls @PostConstruct) is a MergedBeanDefinitionPostProcessor,
 * and PostProcessorRegistrationDelegate re-registers all such "internal" BPPs at the END of the chain.
 * Registered from a static @Bean method so it is created before regular beans.
 */
public class RecordingBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof LifecycleBean lifecycleBean) {
            lifecycleBean.record("BeanPostProcessor.before");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof LifecycleBean lifecycleBean) {
            lifecycleBean.record("BeanPostProcessor.after");
        }
        return bean; // AOP proxies are created here (AbstractAutoProxyCreator) by returning a wrapper instead
    }
}
