package com.inf.long2.util.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires every concrete class implementing the annotated interface
 * to explicitly declare an instance method that overrides the annotated
 * method. Inherited implementations do not satisfy this contract.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface HaveToImplements {
}
