package com.bricklayers.userservice.db;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReadFromPrimaryAspect {

    @Around("@annotation(com.bricklayers.userservice.db.ReadFromPrimary)")
    public Object routeToPrimary(ProceedingJoinPoint joinPoint) throws Throwable {
        ReadRoutingContext.enterExplicitPrimary();
        try {
            return joinPoint.proceed();
        } finally {
            ReadRoutingContext.exitExplicitPrimary();
        }
    }
}
