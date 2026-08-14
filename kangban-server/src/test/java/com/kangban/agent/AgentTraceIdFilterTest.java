package com.kangban.agent;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentTraceIdFilterTest {

    @Test
    void propagatesSafeIncomingTraceId() throws Exception {
        AgentTraceIdFilter filter = new AgentTraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-123");
        verify(chain).doFilter(request, response);
    }

    @Test
    void replacesUnsafeIncomingTraceId() throws Exception {
        AgentTraceIdFilter filter = new AgentTraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "bad value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id"))
                .matches("[A-Za-z0-9-]{20,64}");
        verify(chain).doFilter(request, response);
    }
}
