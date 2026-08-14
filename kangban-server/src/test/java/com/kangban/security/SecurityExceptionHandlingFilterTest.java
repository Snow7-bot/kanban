package com.kangban.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionHandlingFilterTest {

    @Test
    void convertsAuthorizationDeniedExceptionToJson403() throws Exception {
        SecurityExceptionHandlingFilter filter = new SecurityExceptionHandlingFilter(new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                (request, servletResponse) -> {
                    throw new AuthorizationDeniedException("Access Denied");
                });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":403", "权限不足");
    }

    @Test
    void doesNotWrapErrorDispatchAgain() throws Exception {
        SecurityExceptionHandlingFilter filter = new SecurityExceptionHandlingFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void doesNotWrapAsyncDispatchAgain() throws Exception {
        SecurityExceptionHandlingFilter filter = new SecurityExceptionHandlingFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }
}
