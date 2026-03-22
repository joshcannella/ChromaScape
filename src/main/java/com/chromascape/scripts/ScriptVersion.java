package com.chromascape.scripts;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the semantic version of a script. Git hash is appended automatically. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScriptVersion {
  int major() default 0;

  int minor() default 1;
}
