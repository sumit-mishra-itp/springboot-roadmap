package dev.roadmap.coreioc.proxying;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ConfigurationModeTest {

    @Test
    void fullMode_interBeanCallReturnsSingleton() {
        try (var context = new AnnotationConfigApplicationContext(FullModeConfig.class)) {
            Clock bean = context.getBean("fullClock", Clock.class);
            assertThat(context.getBean("fullHolder", ClockHolder.class).clock()).isSameAs(bean);
            assertThat(context.getBean(FullModeConfig.class).getClass().getName()).contains("$$SpringCGLIB$$");
        }
    }

    @Test
    void liteMode_interBeanCallCreatesNewInstance_parameterInjectionDoesNot() {
        try (var context = new AnnotationConfigApplicationContext(LiteModeConfig.class)) {
            Clock bean = context.getBean("liteClock", Clock.class);
            assertThat(context.getBean("liteHolderByCall", ClockHolder.class).clock()).isNotSameAs(bean);
            assertThat(context.getBean("liteHolderByParam", ClockHolder.class).clock()).isSameAs(bean);
            assertThat(context.getBean(LiteModeConfig.class).getClass()).isEqualTo(LiteModeConfig.class);
        }
    }
}
