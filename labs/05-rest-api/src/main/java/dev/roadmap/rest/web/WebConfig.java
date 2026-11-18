package dev.roadmap.rest.web;

import java.net.URI;
import java.time.ZonedDateTime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.StandardApiVersionDeprecationHandler;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.roadmap.rest.book.BookController;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    public static final String API_VERSION = "API-Version";

    private final CorsProperties cors;

    public WebConfig(CorsProperties cors) {
        this.cors = cors;
    }

    /**
     * Framework 7 first-class API versioning. Boot equivalent without code:
     * spring.mvc.apiversion.use.header=API-Version and spring.mvc.apiversion.default=1.0.
     * The default matters. Versioning applies to EVERY request once enabled, and a missing version is a 400 by
     * default. Without a default, unversioned clients, springdoc's /v3/api-docs and CORS preflights would all break.
     */
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        var deprecation = new StandardApiVersionDeprecationHandler();
        deprecation.configureVersion("1.0")
                .setDeprecationDate(ZonedDateTime.parse("2026-06-01T00:00:00Z"))
                .setDeprecationLink(URI.create("https://api.roadmap.dev/docs/migrate-to-v2"))
                .setSunsetDate(ZonedDateTime.parse("2027-06-01T00:00:00Z"));

        configurer.useRequestHeader(API_VERSION)
                // 2.1 has no mapping of its own: a non-breaking minor release served by the "2.0+" baseline mapping
                .addSupportedVersions("1.0", "2.0", "2.1")
                .setDefaultVersion("1.0")
                .setDeprecationHandler(deprecation);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestTraceInterceptor()).addPathPatterns("/api/**");
    }

    /** Browsers only: CORS is enforced by the browser, never by curl or server-to-server calls. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "If-Match", "If-None-Match", API_VERSION, BookController.IDEMPOTENCY_KEY)
                // Without exposedHeaders, browser JS cannot read these response headers
                .exposedHeaders("Location", "ETag", "Deprecation", "Sunset", RequestTraceFilter.REQUEST_ID,
                        BookController.IDEMPOTENT_REPLAYED)
                .maxAge(3600);
    }
}
