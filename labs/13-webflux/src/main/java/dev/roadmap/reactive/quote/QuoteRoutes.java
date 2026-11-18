package dev.roadmap.reactive.quote;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Functional endpoints: routing is plain code, not annotations. Boot registers every RouterFunction bean
 * with WebFlux's RouterFunctionMapping, which sits beside RequestMappingHandlerMapping in the DispatcherHandler.
 */
@Configuration(proxyBeanMethods = false)
public class QuoteRoutes {

    @Bean
    RouterFunction<ServerResponse> quoteRouter(QuoteHandler handler) {
        return RouterFunctions.route()
                .GET("/quotes/{symbol}", handler::quote)
                .GET("/downstream/quotes/{symbol}", handler::simulatedDownstream)
                .build();
    }
}
