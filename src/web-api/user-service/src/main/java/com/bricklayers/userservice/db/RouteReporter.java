package com.bricklayers.userservice.db;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RouteReporter {

    public static final String HEADER = "X-DB-Route";
    private static final String ATTRIBUTE = RouteReporter.class.getName() + ".routes";

    private RouteReporter() {
    }

    public static void report(DbRoute route) {
        report(route.header());
    }

    @SuppressWarnings("unchecked")
    public static void report(String route) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return;
        }
        HttpServletRequest request = servlet.getRequest();
        HttpServletResponse response = servlet.getResponse();
        Set<String> routes = (Set<String>) request.getAttribute(ATTRIBUTE);
        if (routes == null) {
            routes = new LinkedHashSet<>();
            request.setAttribute(ATTRIBUTE, routes);
        }
        routes.add(route);
        if (response != null && !response.isCommitted()) {
            response.setHeader(HEADER, String.join(",", routes));
        }
    }
}
