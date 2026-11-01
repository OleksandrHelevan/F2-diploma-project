package com.bricklayers.userservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityErrorHandlersTest {

    private final ApiAccessDeniedHandler accessDeniedHandler = new ApiAccessDeniedHandler();
    private final ApiAuthenticationEntryPoint authenticationEntryPoint = new ApiAuthenticationEntryPoint();

    @Test
    void accessDenied_onApiPath_writesJsonForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("\"message\":\"Access denied\"")
                .contains("\"path\":\"/api/v1/admin/users\"");
    }

    @Test
    void accessDenied_onPagePath_sendsPlainForbiddenError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void authenticationRequired_writesJsonUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(request, response, new BadCredentialsException("bad token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"message\":\"Authentication required\"")
                .contains("\"path\":\"/api/v1/me\"");
    }

    @Test
    void authenticationRequired_escapesQuotesInPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/\"x\"");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(request, response, new BadCredentialsException("bad token"));

        assertThat(response.getContentAsString()).contains("\"path\":\"/api/v1/users/\\\"x\\\"\"");
    }
}
