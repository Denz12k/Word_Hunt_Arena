package edu.ieu.se360.wordhunt.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runtime custom annotation for declaring metadata on PowerUp implementations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PowerUpInfo {
    String code();
    String name();
    String description();
    int multiplier() default 1;
}
