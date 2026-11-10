package com.bricklayers.common.instance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LabLatencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Lab-Slow-Instance";

    private final InstanceId instanceId;
    private final long delayMillis;

    public LabLatencyFilter(InstanceId instanceId, long delayMillis) {
        this.instanceId = instanceId;
        this.delayMillis = delayMillis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("GET".equals(request.getMethod())
                && !"/health".equals(request.getRequestURI())
                && instanceId.value().equals(request.getHeader(HEADER))) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Request interrupted");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
