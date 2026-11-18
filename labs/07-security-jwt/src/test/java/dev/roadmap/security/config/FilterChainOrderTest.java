package dev.roadmap.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.DelegatingFilterProxy;

/**
 * Proves the architecture instead of reciting it: servlet container -> DelegatingFilterProxy ->
 * FilterChainProxy (bean "springSecurityFilterChain") -> the first matching SecurityFilterChain, and the exact
 * filter order inside a JWT chain versus a classic form-login chain.
 */
@SpringBootTest
class FilterChainOrderTest {

    private static final Logger log = LoggerFactory.getLogger(FilterChainOrderTest.class);

    /** Test-only chain, so the form-login filter order is proven too, not recalled. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FormLoginDemoChain {
        @Bean
        @Order(3)
        SecurityFilterChain formLoginDemoChain(HttpSecurity http) {
            return http.securityMatcher("/form-demo/**")
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .formLogin(withDefaults())
                    .build();
        }
    }

    @Autowired
    ApplicationContext context;

    @Test
    void servletContainer_reachesSecurityThroughDelegatingFilterProxy() {
        DelegatingFilterProxyRegistrationBean registration =
                context.getBean("securityFilterChainRegistration", DelegatingFilterProxyRegistrationBean.class);

        assertThat(registration.getFilter()).isInstanceOf(DelegatingFilterProxy.class);
        assertThat(registration.getFilterName()).isEqualTo("springSecurityFilterChain");
        assertThat(context.getBean("springSecurityFilterChain")).isInstanceOf(FilterChainProxy.class);
    }

    @Test
    void chains_areTriedInOrder_andTheCatchAllIsLast() {
        List<String> matchers = proxy().getFilterChains().stream()
                .map(chain -> chain.toString().replaceAll(", Filters=.*", ""))
                .toList();
        matchers.forEach(m -> log.info("chain: {}", m));

        assertThat(matchers).hasSize(4);
        assertThat(matchers.get(0)).contains("EndpointRequestMatcher");
        assertThat(matchers.get(1)).contains("/api/**", "/auth/**");
        assertThat(matchers.get(2)).contains("/form-demo/**");
        assertThat(matchers.get(3)).contains("any request");
    }

    @Test
    void jwtApiChain_filterOrder() {
        List<String> filters = filterNames(1);
        log.info("JWT API chain filters, in order:\n  {}", String.join("\n  ", filters));

        assertThat(filters).containsExactly(
                "DisableEncodeUrlFilter",
                "WebAsyncManagerIntegrationFilter",
                "SecurityContextHolderFilter",          // loads the SecurityContext (from the session, or nothing)
                "HeaderWriterFilter",
                "CorsFilter",                           // preflights answered BEFORE any authentication,
                "LogoutFilter",
                "OAuth2ProtectedResourceMetadataFilter", // RFC 9728 protected-resource metadata (7.x)
                "BearerTokenAuthenticationFilter",       // Authorization: Bearer -> JwtDecoder -> converter
                "RequestCacheAwareFilter",
                "SecurityContextHolderAwareRequestFilter",
                "AnonymousAuthenticationFilter",         // no token? you become "anonymousUser"
                "SessionManagementFilter",
                "ExceptionTranslationFilter",            // turns exceptions from below into 401/403
                "AuthorizationFilter");                  // authorizeHttpRequests rules, always last
        assertThat(filters).doesNotContain("CsrfFilter", "UsernamePasswordAuthenticationFilter");
    }

    @Test
    void formLoginChain_filterOrder() {
        List<String> filters = filterNames(2);
        log.info("Form-login chain filters, in order:\n  {}", String.join("\n  ", filters));

        assertThat(filters).containsExactly(
                "DisableEncodeUrlFilter",
                "WebAsyncManagerIntegrationFilter",
                "SecurityContextHolderFilter",          // loads the SecurityContext (from the session, or nothing)
                "HeaderWriterFilter",
                "CorsFilter",                           // preflights answered BEFORE any authentication,
                "CsrfFilter",                           // session cookies are ambient, so CSRF matters here
                "LogoutFilter",
                "UsernamePasswordAuthenticationFilter",  // POST /login
                "DefaultResourcesFilter",
                "DefaultLoginPageGeneratingFilter",      // GET /login page (and MFA factor pages in 7.x)
                "DefaultLogoutPageGeneratingFilter",
                "RequestCacheAwareFilter",               // replays the request saved before redirect to /login
                "SecurityContextHolderAwareRequestFilter",
                "AnonymousAuthenticationFilter",
                "ExceptionTranslationFilter",
                "AuthorizationFilter");
    }

    @Test
    void actuatorChain_filterOrder() {
        List<String> filters = filterNames(0);
        log.info("Actuator chain filters, in order:\n  {}", String.join("\n  ", filters));

        assertThat(filters).containsExactly(
                "DisableEncodeUrlFilter",
                "WebAsyncManagerIntegrationFilter",
                "SecurityContextHolderFilter",          // loads the SecurityContext (from the session, or nothing)
                "HeaderWriterFilter",
                "CorsFilter",                           // preflights answered BEFORE any authentication,
                "CsrfFilter",
                "LogoutFilter",
                "BasicAuthenticationFilter",
                "RequestCacheAwareFilter",
                "SecurityContextHolderAwareRequestFilter",
                "AnonymousAuthenticationFilter",
                "SessionManagementFilter",
                "ExceptionTranslationFilter",
                "AuthorizationFilter");
    }

    private List<String> filterNames(int chainIndex) {
        return proxy().getFilterChains().get(chainIndex).getFilters().stream()
                .map(filter -> filter.getClass().getSimpleName())
                .toList();
    }

    private FilterChainProxy proxy() {
        return context.getBean(FilterChainProxy.class);
    }
}
