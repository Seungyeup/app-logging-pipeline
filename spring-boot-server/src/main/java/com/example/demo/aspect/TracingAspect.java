package com.example.demo.aspect;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TracingAspect {

    private final Tracer tracer;

    @Autowired
    public TracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    // Define a pointcut for all methods in HelloController
    @Pointcut("execution(* com.example.demo.HelloController.*(..))")
    public void helloControllerMethods() {}

    // Define a pointcut for all methods in any class annotated with @RestController
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {}

    @Around("restControllerMethods()") // Apply to all methods in RestControllers
    public Object addGlobalIdToSpan(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = null;
        try {
            result = joinPoint.proceed(); // Proceed with the original method execution
        } finally {
            // Get globalId from MDC
            String globalId = MDC.get("globalId");

            // Add globalId as a span attribute
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && globalId != null && !globalId.isEmpty()) {
                currentSpan.tag("globalId", globalId);
            }
        }
        return result;
    }
}
