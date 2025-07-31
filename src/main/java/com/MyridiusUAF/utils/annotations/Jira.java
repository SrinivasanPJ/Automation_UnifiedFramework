package com.MyridiusUAF.utils.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Annotation to associate a test method with a JIRA ticket.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   @Jira("KAN-123")
 *   @Test
 *   public void myTest() { ... }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Jira {
    /**
     * JIRA ticket key (e.g., "KAN-123").
     */
    String value();
}
