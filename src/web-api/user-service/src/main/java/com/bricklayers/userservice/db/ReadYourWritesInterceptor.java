package com.bricklayers.userservice.db;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class ReadYourWritesInterceptor implements HandlerInterceptor {

    private final ReadYourWritesTracker tracker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ReadRoutingContext.setStickyPrimary(tracker.isSticky(ReadYourWritesTracker.currentSubject()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        ReadRoutingContext.clear();
    }
}
