package com.bricklayers.common.instance;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LabLatencyFilterTest {

    private final LabLatencyFilter filter = new LabLatencyFilter(InstanceId.of("user-1"), 200);

    @Test
    void delaysGetOnSelectedInstance() throws Exception {
        assertThat(elapsedMillis("GET", "/api/v1/me", "user-1")).isGreaterThanOrEqualTo(190);
    }

    @Test
    void doesNotDelayOtherInstancesWritesOrHealth() throws Exception {
        assertThat(elapsedMillis("GET", "/api/v1/me", "user-2")).isLessThan(150);
        assertThat(elapsedMillis("POST", "/api/v1/admin/users", "user-1")).isLessThan(150);
        assertThat(elapsedMillis("GET", "/health", "user-1")).isLessThan(150);
    }

    @Test
    void interruptedDelay_answers503AndStopsTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(LabLatencyFilter.HEADER, "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        Thread.currentThread().interrupt();
        try {
            filter.doFilter(request, response, chain);
        } finally {
            Thread.interrupted();
        }

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
    }

    private long elapsedMillis(String method, String uri, String slowInstance) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader(LabLatencyFilter.HEADER, slowInstance);
        long started = System.nanoTime();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return (System.nanoTime() - started) / 1_000_000;
    }
}
