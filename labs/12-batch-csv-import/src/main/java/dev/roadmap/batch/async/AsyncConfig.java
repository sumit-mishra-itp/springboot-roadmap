package dev.roadmap.batch.async;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Two executors on purpose:
 * <ul>
 *   <li>plain {@code @Async} uses Boot's {@code applicationTaskExecutor} (kept alive by spring.task.execution.mode=force).
 *       It is a ThreadPoolTaskExecutor, or a virtual-thread SimpleAsyncTaskExecutor when spring.threads.virtual.enabled=true.
 *       Boot applies the {@link MdcTaskDecorator} bean to it automatically;</li>
 *   <li>{@code @Async("reportExecutor")} uses the bounded pool below, which ignores the virtual-thread flag.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    @Bean
    MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /** A bounded pool: bulkheads report I/O so it cannot starve other async work. */
    @Bean
    ThreadPoolTaskExecutor reportExecutor(MdcTaskDecorator mdcTaskDecorator) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("report-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);            // only grows once the queue is full
        executor.setQueueCapacity(50);         // the default is unbounded, which makes maxPoolSize meaningless
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // back-pressure, not errors
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    AsyncFailureHandler asyncFailureHandler() {
        return new AsyncFailureHandler();
    }

    /**
     * Only overrides the exception handler. getAsyncExecutor() stays null, and Boot wraps this configurer so that
     * the default executor is still applicationTaskExecutor.
     */
    @Bean
    AsyncConfigurer asyncConfigurer(AsyncFailureHandler asyncFailureHandler) {
        return new AsyncConfigurer() {
            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return asyncFailureHandler;
            }
        };
    }
}
