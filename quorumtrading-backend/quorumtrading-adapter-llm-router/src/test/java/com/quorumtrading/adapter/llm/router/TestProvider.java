package com.quorumtrading.adapter.llm.router;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier for test LLM providers.
 * Allows the router to discover multiple mock providers without causing ambiguity
 * for other services that need a single LLMPort instance.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
public @interface TestProvider {
    String value() default "";
}
