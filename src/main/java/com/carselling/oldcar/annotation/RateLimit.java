package com.carselling.oldcar.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int capacity() default 100;
    int refill() default 10;
    int refillPeriod() default 1;
    
    /**
     * Priority of the request. 
     * Higher priority requests are favored during system load.
     * Default is NORMAL.
     */
    String priority() default "NORMAL"; 
}

