package dev.roadmap.coreioc.lifecycle;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Records every lifecycle callback so the test can assert the real order.
 * Registered via @Bean(initMethod/destroyMethod) in LifecycleConfig, so it is not a @Component.
 */
public class LifecycleBean implements BeanNameAware, InitializingBean, DisposableBean {

    private final List<String> events = new ArrayList<>();

    public LifecycleBean() {
        events.add("constructor");
    }

    @Override
    public void setBeanName(String name) {
        events.add("BeanNameAware.setBeanName");
    }

    @PostConstruct
    void postConstruct() {
        events.add("@PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        events.add("InitializingBean.afterPropertiesSet");
    }

    void customInit() {
        events.add("@Bean(initMethod)");
    }

    @PreDestroy
    void preDestroy() {
        events.add("@PreDestroy");
    }

    @Override
    public void destroy() {
        events.add("DisposableBean.destroy");
    }

    void customDestroy() {
        events.add("@Bean(destroyMethod)");
    }

    void record(String event) {
        events.add(event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
