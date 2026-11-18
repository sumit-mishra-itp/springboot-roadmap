package dev.roadmap.coreioc.lifecycle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LifecycleConfig {

    /** static: a BPP must exist before other beans, and static avoids instantiating this config class early. */
    @Bean
    static RecordingBeanPostProcessor recordingBeanPostProcessor() {
        return new RecordingBeanPostProcessor();
    }

    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    LifecycleBean lifecycleBean() {
        return new LifecycleBean();
    }
}
