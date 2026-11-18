package dev.roadmap.security.document;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import dev.roadmap.security.config.JwtConfig;
import dev.roadmap.security.config.ProblemDetailSecurityHandler;
import dev.roadmap.security.config.SecurityConfig;

/**
 * Slice test: MVC + the REAL SecurityConfig + the REAL DocumentService. Method security needs the real proxied
 * bean, so the service is imported, not mocked. @WebMvcTest does not pick up @Configuration classes on its own,
 * so the security config is imported explicitly.
 */
@WebMvcTest(DocumentController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ProblemDetailSecurityHandler.class,
        DocumentService.class, DocumentGuard.class, DocumentRepository.class })
class DocumentControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymous_gets401ProblemDetail() throws Exception {
        mvc.perform(get("/api/documents/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    /**
     * jwt() skips the JwtDecoder AND our JwtAuthenticationConverter: it builds a JwtAuthenticationToken directly.
     * Claims you set are NOT converted to roles, so the authorities are passed explicitly.
     */
    @Test
    void jwtPostProcessor_owner_readsOwnDocument() throws Exception {
        mvc.perform(get("/api/documents/1")
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("alice"));
    }

    @Test
    @WithMockUser(username = "bob")
    void postAuthorize_deniesSomeoneElsesDocument() throws Exception {
        mvc.perform(get("/api/documents/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "root", roles = "ADMIN")
    void postAuthorize_letsAdminReadAnyDocument() throws Exception {
        mvc.perform(get("/api/documents/1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    void preAuthorize_domainRule_ownerMayRename() throws Exception {
        mvc.perform(put("/api/documents/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alice's new roadmap\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alice's new roadmap"));
    }

    @Test
    @WithMockUser(username = "alice")
    void preAuthorize_domainRule_nonOwnerMayNotRename() throws Exception {
        mvc.perform(put("/api/documents/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void urlRule_adminPath_rejectsUserRole() throws Exception {
        mvc.perform(delete("/api/admin/documents/3")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void urlRule_adminPath_allowsAdminRole() throws Exception {
        mvc.perform(delete("/api/admin/documents/3")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void corsPreflight_fromAllowedOrigin_isAnsweredWithoutAToken() throws Exception {
        mvc.perform(options("/api/documents/1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void corsPreflight_fromUnknownOrigin_isRejected() throws Exception {
        mvc.perform(options("/api/documents/1")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
