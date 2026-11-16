package edu.ieu.se360.wordhunt.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runtime custom annotation for declaring metadata and execution priority on ScoringRule implementations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScoringRuleInfo {
    String name();
    int priority() default 1;
}
