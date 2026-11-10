package com.bricklayers.common.instance;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceIdFilterTest {

    @Test
    void addsConfiguredInstanceIdAndIgnoresClientValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(InstanceIdFilter.HEADER, "spoofed");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InstanceIdFilter(InstanceId.of("user-1")).doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(InstanceIdFilter.HEADER)).isEqualTo("user-1");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void blankConfigurationGeneratesNodeId() {
        assertThat(InstanceId.of(" ").value()).matches("node-[0-9a-f]{8}");
    }

    @Test
    void rejectsUnsafeInstanceId() {
        assertThatThrownBy(() -> InstanceId.of("bad id\r\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
